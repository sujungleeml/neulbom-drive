package com.neulbom.drive.data.sync

import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.core.model.SignalSample
import com.neulbom.drive.core.model.SignalSupport
import com.neulbom.drive.domain.driving.SampleCodec

/** 업로드 대기열(차량 앱: RoomDrivingQueue). */
interface UploadQueue {
    suspend fun pending(): List<Pair<DrivingSession, List<SignalSample>>>
    suspend fun markUploaded(driveId: String)
}

data class UploadReport(val sent: Int, val failed: Int)

/** 서버 `SessionUploadDto`와 같은 JSON. 위치·경로 필드는 없다. */
object UploadPayload {
    fun json(session: DrivingSession, samples: List<SignalSample>): String = buildString {
        append("{\"session\":{")
        append("\"driveId\":").append(q(session.driveId)).append(',')
        append("\"startedAt\":").append(q(session.startedAt.toString())).append(',')
        append("\"endedAt\":").append(q(session.endedAt.toString())).append(',')
        append("\"zone\":").append(q(session.zone.id)).append(',')
        append("\"origin\":").append(q(session.origin.name)).append(',')
        append("\"signals\":[").append(session.signals.sortedBy { it.ordinal }.joinToString(",") { q(it.name) }).append("],")
        append("\"sampleRateHz\":{").append(session.sampleRateHz.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${q(it.key.name)}:${it.value}" }).append("},")
        append("\"attribution\":").append(q(session.attribution.name)).append(',')
        append("\"terminatedNormally\":").append(session.terminatedNormally)
        append("},\"samplesEncoded\":").append(q(SampleCodec.encodeChunks(samples, Int.MAX_VALUE).firstOrNull() ?: ""))
        append('}')
    }

    fun signalSupportJson(list: List<SignalSupport>): String = list.joinToString(",", "[", "]") {
        "{\"signal\":${q(it.signal.name)},\"status\":${q(it.status.name)},\"sdkName\":${it.sdkName?.let(::q) ?: "null"},\"sampleRateHz\":${it.sampleRateHz ?: "null"}}"
    }

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

/**
 * 대기열의 주행 세션을 멱등 업로드한다(명세 v0.6 04 §5).
 *  - `Idempotency-Key` = driveId, 2xx면 업로드 완료 표시(서버 ACK 이후에만).
 *  - 5xx·네트워크 오류는 1초→2초 간격으로 최대 3회 시도, 4xx는 재시도하지 않는다. 실패한 세션은 대기열에 남는다.
 *  - baseUrl이 비어 있으면(예: pleos 플래버) 아무것도 보내지 않는다.
 */
class DrivingUploader(
    private val baseUrl: String,
    private val queue: UploadQueue,
    private val transport: HttpTransport = UrlConnectionTransport,
    private val sleeper: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    suspend fun uploadPending(): UploadReport {
        if (baseUrl.isBlank()) return UploadReport(0, 0)
        var sent = 0
        var failed = 0
        for ((session, samples) in queue.pending()) {
            val body = UploadPayload.json(session, samples)
            if (send("$baseUrl/v1/driving-sessions", body, mapOf("Idempotency-Key" to session.driveId))) {
                queue.markUploaded(session.driveId)
                sent++
            } else {
                failed++
            }
        }
        return UploadReport(sent, failed)
    }

    suspend fun uploadSignalSupport(list: List<SignalSupport>): Boolean =
        baseUrl.isNotBlank() && send("$baseUrl/v1/signal-support", UploadPayload.signalSupportJson(list), emptyMap())

    private val poster = RetryingPoster(transport, sleeper)

    private suspend fun send(url: String, body: String, headers: Map<String, String>) = poster.send(url, body, headers)
}
