package com.neulbom.drive.data.sync

import java.time.Instant

/** 서버 `AssessmentSessionDto`와 같은 필드. 결과 점수 필드는 없다(원자료만). */
data class AssessmentUploadSession(
    val assessmentId: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val status: String,
    val stopReason: String?,
    val isFirstAssessment: Boolean,
    val protocolVersions: Map<String, Int>,
    val appVersion: String,
    val deviceProfile: String,
    val vehicleSource: String,
)

/** 서버 `TrialDto`와 같은 필드. */
data class AssessmentUploadTrial(
    val taskId: String,
    val taskVersion: Int,
    val stimulusSetVersion: Int,
    val trialIndex: Int,
    val block: String,
    val trialType: String,
    val scheduledOnsetMs: Long,
    val actualOnsetMs: Long?,
    val responseMs: Long?,
    val reactionTimeMs: Long?,
    val responseCode: String?,
    val outcome: String,
    val isPractice: Boolean,
    val displayLatencyMs: Long,
)

data class AssessmentUpload(val session: AssessmentUploadSession, val trials: List<AssessmentUploadTrial>)

/** 업로드 대기열(차량 앱: RoomAssessmentQueue). 끝난 점검(완료·중단·취소)만 들어 있다. */
interface AssessmentQueue {
    suspend fun pending(): List<AssessmentUpload>
    suspend fun markSynced(assessmentId: String)
}

data class AssessmentUploadReport(val syncedIds: List<String>, val failed: Int)

object AssessmentPayload {
    fun json(u: AssessmentUpload): String = buildString {
        val s = u.session
        append("{\"session\":{")
        append("\"assessmentId\":").append(Json.q(s.assessmentId)).append(',')
        append("\"startedAt\":").append(Json.q(s.startedAt.toString())).append(',')
        append("\"endedAt\":").append(Json.q(s.endedAt?.toString())).append(',')
        append("\"status\":").append(Json.q(s.status)).append(',')
        append("\"stopReason\":").append(Json.q(s.stopReason)).append(',')
        append("\"isFirstAssessment\":").append(s.isFirstAssessment).append(',')
        append("\"protocolVersions\":{").append(s.protocolVersions.entries.sortedBy { it.key }.joinToString(",") { "${Json.q(it.key)}:${it.value}" }).append("},")
        append("\"appVersion\":").append(Json.q(s.appVersion)).append(',')
        append("\"deviceProfile\":").append(Json.q(s.deviceProfile)).append(',')
        append("\"vehicleSource\":").append(Json.q(s.vehicleSource))
        append("},\"trials\":[")
        u.trials.forEachIndexed { i, t ->
            if (i > 0) append(',')
            append("{\"taskId\":").append(Json.q(t.taskId))
            append(",\"taskVersion\":").append(t.taskVersion)
            append(",\"stimulusSetVersion\":").append(t.stimulusSetVersion)
            append(",\"trialIndex\":").append(t.trialIndex)
            append(",\"block\":").append(Json.q(t.block))
            append(",\"trialType\":").append(Json.q(t.trialType))
            append(",\"scheduledOnsetMs\":").append(t.scheduledOnsetMs)
            append(",\"actualOnsetMs\":").append(Json.n(t.actualOnsetMs))
            append(",\"responseMs\":").append(Json.n(t.responseMs))
            append(",\"reactionTimeMs\":").append(Json.n(t.reactionTimeMs))
            append(",\"responseCode\":").append(Json.q(t.responseCode))
            append(",\"outcome\":").append(Json.q(t.outcome))
            append(",\"isPractice\":").append(t.isPractice)
            append(",\"displayLatencyMs\":").append(t.displayLatencyMs)
            append('}')
        }
        append("]}")
    }
}

/**
 * 끝난 점검의 원자료를 멱등 업로드한다. `Idempotency-Key` = assessmentId.
 * 서버가 2xx로 답한 뒤에만 `markSynced`(→ SERVER_SAVED). baseUrl이 비어 있으면 아무것도 보내지 않는다.
 */
class AssessmentUploader(
    private val baseUrl: String,
    private val queue: AssessmentQueue,
    transport: HttpTransport = UrlConnectionTransport,
    sleeper: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    private val poster = RetryingPoster(transport, sleeper)

    suspend fun uploadPending(): AssessmentUploadReport {
        if (baseUrl.isBlank()) return AssessmentUploadReport(emptyList(), 0)
        val synced = mutableListOf<String>()
        var failed = 0
        for (u in queue.pending()) {
            val id = u.session.assessmentId
            if (poster.send("$baseUrl/v1/assessments", AssessmentPayload.json(u), mapOf("Idempotency-Key" to id))) {
                queue.markSynced(id)
                synced += id
            } else {
                failed++
            }
        }
        return AssessmentUploadReport(synced, failed)
    }
}
