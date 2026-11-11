package com.neulbom.drive.domain.cognitive.report

import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.TaskProtocol
import com.neulbom.drive.domain.cognitive.TrialOutcome
import com.neulbom.drive.domain.cognitive.assessment.AssessmentStatus
import java.time.Instant

/** 저장·전송된 시행 한 건(차량 Room ↔ 서버 공통 모양). */
data class TrialRecord(
    val taskId: TaskId,
    val block: String,
    /** 예: ARROW_LEFT, GO, NOGO, DIGIT_7_PARITY_SWITCH */
    val trialType: String,
    val rtMs: Long?,
    val outcome: TrialOutcome,
    val isPractice: Boolean,
)

data class AssessmentRecord(
    val assessmentId: String,
    val startedAt: Instant,
    val status: AssessmentStatus,
    val protocolVersions: Map<TaskId, Int>,
    val deviceProfile: String,
    /** MOCK(모의 차량) 또는 차량 SDK 이름 */
    val vehicleSource: String,
    val trials: List<TrialRecord>,
    /** 과제별 자극 세트 버전. 같은 과제 버전이라도 자극 세트가 다르면 비교하지 않는다. */
    val stimulusSetVersions: Map<TaskId, Int> = emptyMap(),
)

/**
 * 과제 하나의 기술통계(명세 v0.6 03 §7). 해석·판정 없이 값만.
 * @property hiddenReason 값이 안정적이지 않아 보여 주지 않는 이유(예: TOO_FEW_TRIALS). 이때 수치는 모두 null.
 */
data class TaskMetrics(
    val taskId: TaskId,
    val mainTrials: Int,
    val medianRtMs: Long?,
    val accuracy: Double?,
    val secondary: Map<String, Double?>,
    val hiddenReason: String?,
)

object CheckMetrics {
    /** Go/No-Go 오반응률을 보여 줄 최소 No-Go 시행 수 — PROVISIONAL. */
    const val MIN_NOGO_TRIALS = 8

    /** 전환 비용을 보여 줄 최소 전환·반복 정답 수(각각) — PROVISIONAL. 혼합 블록 16회 중 전환은 약 7회. */
    const val MIN_SWITCH_TRIALS = 6

    fun compute(taskId: TaskId, trials: List<TrialRecord>, protocol: TaskProtocol): TaskMetrics {
        val main = trials.filter { it.taskId == taskId && !it.isPractice && it.outcome != TrialOutcome.ANTICIPATION }
        if (main.size < protocol.minimumValidTrials) {
            return TaskMetrics(taskId, main.size, null, null, emptyMap(), "TOO_FEW_TRIALS")
        }
        val correct = main.filter { it.outcome == TrialOutcome.CORRECT }
        val medianRt = median(correct.mapNotNull { it.rtMs })
        val n = main.size.toDouble()
        return when (taskId) {
            TaskId.VISUAL_ATTENTION -> TaskMetrics(
                taskId, main.size, medianRt, correct.size / n,
                mapOf("omission_rate" to main.count { it.outcome == TrialOutcome.OMISSION } / n), null,
            )
            TaskId.GO_NO_GO -> {
                val go = main.filter { it.trialType == "GO" }
                val noGo = main.filter { it.trialType == "NOGO" }
                val accurate = main.count { it.outcome == TrialOutcome.CORRECT || it.outcome == TrialOutcome.CORRECT_REJECTION }
                TaskMetrics(
                    taskId, main.size, median(go.filter { it.outcome == TrialOutcome.CORRECT }.mapNotNull { it.rtMs }), accurate / n,
                    mapOf(
                        "commission_rate" to noGo.takeIf { it.size >= MIN_NOGO_TRIALS }?.let { l -> l.count { it.outcome == TrialOutcome.COMMISSION } / l.size.toDouble() },
                        "omission_rate" to go.takeIf { it.isNotEmpty() }?.let { l -> l.count { it.outcome == TrialOutcome.OMISSION } / l.size.toDouble() },
                    ),
                    null,
                )
            }
            TaskId.TASK_SWITCHING -> {
                val sw = correct.filter { it.trialType.endsWith("_SWITCH") }.mapNotNull { it.rtMs }
                val rep = correct.filter { it.trialType.endsWith("_REPEAT") }.mapNotNull { it.rtMs }
                val cost = if (sw.size >= MIN_SWITCH_TRIALS && rep.size >= MIN_SWITCH_TRIALS) (median(sw)!! - median(rep)!!).toDouble() else null
                TaskMetrics(taskId, main.size, medianRt, correct.size / n, mapOf("switch_cost_ms" to cost), null)
            }
        }
    }

    internal fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }
}
