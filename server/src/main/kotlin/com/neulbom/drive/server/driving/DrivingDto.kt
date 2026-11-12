package com.neulbom.drive.server.driving

import com.neulbom.drive.core.model.Attribution
import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.model.SignalSample
import com.neulbom.drive.domain.driving.SampleCodec
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/** 업로드 형식. 위치·경로·목적지 필드는 없다. 표본은 SampleCodec 문자열로 보낸다(크기 절약). */
@Serializable
data class SessionDto(
    val driveId: String,
    val startedAt: String,
    val endedAt: String,
    val zone: String,
    val origin: String,
    val signals: List<String>,
    val sampleRateHz: Map<String, Double>,
    val attribution: String,
    val terminatedNormally: Boolean,
) {
    fun toDomain() = DrivingSession(
        driveId = driveId,
        startedAt = Instant.parse(startedAt),
        endedAt = Instant.parse(endedAt),
        zone = ZoneId.of(zone),
        origin = DataOrigin.valueOf(origin),
        signals = signals.map { SignalId.valueOf(it) }.toSet(),
        sampleRateHz = sampleRateHz.mapKeys { SignalId.valueOf(it.key) },
        attribution = Attribution.valueOf(attribution),
        terminatedNormally = terminatedNormally,
    )
}

@Serializable
data class SessionUploadDto(val session: SessionDto, val samplesEncoded: String) {
    companion object {
        fun from(session: DrivingSession, samples: List<SignalSample>) = SessionUploadDto(
            SessionDto(
                driveId = session.driveId,
                startedAt = session.startedAt.toString(),
                endedAt = session.endedAt.toString(),
                zone = session.zone.id,
                origin = session.origin.name,
                signals = session.signals.sortedBy { it.ordinal }.map { it.name },
                sampleRateHz = session.sampleRateHz.mapKeys { it.key.name },
                attribution = session.attribution.name,
                terminatedNormally = session.terminatedNormally,
            ),
            SampleCodec.encodeChunks(samples, chunkSize = Int.MAX_VALUE).firstOrNull() ?: "",
        )
    }
}

@Serializable
data class UploadResultDto(val driveId: String, val quality: String, val valid: Boolean, val algorithmVersion: String)

@Serializable
data class MetricLineDto(
    val key: String,
    val value: Double?,
    val unit: String,
    val direction: String?,
    val extra: Double?,
    val text: String,
    /** 지난달 값과 변화 문장(비교할 수 있을 때만). */
    val previous: Double? = null,
    val changeText: String? = null,
)

@Serializable
data class DrivingReportDto(
    val period: String,
    val state: String,
    val origin: String,
    val algorithmVersion: String,
    val lines: List<MetricLineDto>,
    val notices: List<String>,
    /** 그 달 기록 상태(본인 화면에서만 채운다). 품질 수만 세고 점수로 바꾸지 않는다. */
    val quality: DrivingQualityDto? = null,
)

/** 한 달 주행 기록 상태: 전체 업로드 수, 리포트에 넣은 수, 그중 신호가 일부 끊긴(LIMITED) 수, 계산에서 뺀 수. */
@Serializable
data class DrivingQualityDto(val drives: Int, val included: Int, val limited: Int, val excluded: Int)

@Serializable
data class SignalSupportDto(val signal: String, val status: String, val sdkName: String?, val sampleRateHz: Double?)
