package com.neulbom.drive.server.checks

import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.TrialOutcome
import com.neulbom.drive.domain.cognitive.assessment.AssessmentStatus
import com.neulbom.drive.domain.cognitive.report.AssessmentRecord
import com.neulbom.drive.domain.cognitive.report.ChecksReport
import com.neulbom.drive.domain.cognitive.report.TrialRecord
import com.neulbom.drive.server.overview.TaskPointDto
import kotlinx.serialization.Serializable
import java.time.Instant

/** 점검 세션(명세 v0.6 04 assessment_sessions). 결과 점수 필드는 없다 — 원자료만 받는다. */
@Serializable
data class AssessmentSessionDto(
    val assessmentId: String,
    val startedAt: String,
    val endedAt: String?,
    val status: String,
    val stopReason: String?,
    val isFirstAssessment: Boolean,
    val protocolVersions: Map<String, Int>,
    val appVersion: String,
    val deviceProfile: String,
    /** MOCK(모의 차량) 또는 차량 SDK 이름 */
    val vehicleSource: String,
)

/** 시행 원자료(assessment_trials). 단조 시각(ms). */
@Serializable
data class TrialDto(
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

@Serializable
data class AssessmentUploadDto(val session: AssessmentSessionDto, val trials: List<TrialDto>) {
    fun toRecord() = AssessmentRecord(
        assessmentId = session.assessmentId,
        startedAt = Instant.parse(session.startedAt),
        status = AssessmentStatus.valueOf(session.status),
        protocolVersions = session.protocolVersions.mapKeys { TaskId.valueOf(it.key) },
        deviceProfile = session.deviceProfile,
        vehicleSource = session.vehicleSource,
        trials = trials.map { TrialRecord(TaskId.valueOf(it.taskId), it.block, it.trialType, it.reactionTimeMs, TrialOutcome.valueOf(it.outcome), it.isPractice) },
        stimulusSetVersions = trials.groupBy { TaskId.valueOf(it.taskId) }.mapValues { (_, l) -> l.first().stimulusSetVersion },
    )
}

@Serializable
data class AssessmentUploadResultDto(val assessmentId: String, val trialsStored: Int)

@Serializable
data class TaskTrendDto(
    val taskId: String,
    val mainTrials: Int?,
    val medianRtMs: Long?,
    val accuracy: Double?,
    val secondary: Map<String, Double?>,
    val hiddenReason: String?,
    val direction: String?,
    val text: String,
    /** 과제별 상태(COLLECTING/DESCRIPTIVE/TREND_AVAILABLE). 같은 과제 버전·기기의 점검끼리만 비교한다. */
    val state: String = "COLLECTING",
    val taskVersion: Int? = null,
    val comparableCount: Int = 0,
    val excludedCount: Int = 0,
    val limitReason: String? = null,
    /** 점검별 기록(본인 화면에서만 채운다, 공유 화면은 비워 둔다). 비교 키가 다른 점끼리는 잇지 않는다. */
    val points: List<TaskPointDto> = emptyList(),
)

@Serializable
data class CheckHistoryDto(val assessmentId: String, val startedAt: String, val status: String)

@Serializable
data class ChecksReportDto(
    val state: String,
    val validCount: Int,
    val mockVehicle: Boolean,
    val tasks: List<TaskTrendDto>,
    val history: List<CheckHistoryDto>,
    val notices: List<String>,
) {
    companion object {
        fun from(r: ChecksReport) = ChecksReportDto(
            state = r.state.name,
            validCount = r.validCount,
            mockVehicle = r.mockVehicle,
            tasks = r.tasks.map { t ->
                TaskTrendDto(
                    t.taskId.name, t.latest?.mainTrials, t.latest?.medianRtMs, t.latest?.accuracy,
                    t.latest?.secondary ?: emptyMap(), t.latest?.hiddenReason, t.direction, t.text,
                    t.state.name, t.taskVersion, t.comparableCount, t.excludedCount, t.limitReason?.name,
                )
            },
            history = r.history.map { CheckHistoryDto(it.assessmentId, it.startedAt.toString(), it.status.name) },
            notices = r.notices,
        )
    }
}
