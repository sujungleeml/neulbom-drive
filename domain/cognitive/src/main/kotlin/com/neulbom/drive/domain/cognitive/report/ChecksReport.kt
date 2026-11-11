package com.neulbom.drive.domain.cognitive.report

import com.neulbom.drive.domain.cognitive.ProtocolRegistry
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.assessment.AssessmentStatus
import java.time.Instant
import kotlin.math.abs

/** 리포트 분석 상태(명세 v0.6 03 §6). 정상/이상 같은 상태값은 만들지 않는다. */
enum class ChecksReportState { COLLECTING, DESCRIPTIVE, TREND_AVAILABLE, QUALITY_LIMITED }

/** 과제마다 따로 비교할지 막을지 정하는 이유. */
enum class TaskLimitReason { VERSION_CHANGED, DEVICE_CHANGED, TOO_FEW_TRIALS }

/**
 * 과제 하나의 기록. 같은 비교 키(과제 버전·기기)의 점검끼리만 비교한다.
 * @property comparableCount 최신 점검과 비교 키가 같은 유효 점검 수
 * @property excludedCount 비교 키가 달라 비교에서 뺀 유효 점검 수
 */
data class TaskTrend(
    val taskId: TaskId,
    val latest: TaskMetrics?,
    val direction: String?,
    val text: String,
    val state: ChecksReportState = ChecksReportState.COLLECTING,
    val taskVersion: Int? = null,
    val comparableCount: Int = 0,
    val excludedCount: Int = 0,
    val limitReason: TaskLimitReason? = null,
)
/**
 * 마친 점검 한 회의 과제 기록(차트의 점). 기록이 쓰인 버전의 규칙으로 계산한다.
 * 비교 키(과제 버전·자극 세트 버전·기기)가 바뀌는 곳에서 화면은 선을 끊는다.
 */
data class TaskPoint(
    val assessmentId: String,
    val startedAt: Instant,
    val taskVersion: Int?,
    val stimulusSetVersion: Int?,
    val deviceProfile: String,
    val medianRtMs: Long?,
    val accuracy: Double?,
    val hiddenReason: String?,
)

data class HistoryItem(val assessmentId: String, val startedAt: Instant, val status: AssessmentStatus)

data class ChecksReport(
    val state: ChecksReportState,
    val validCount: Int,
    val tasks: List<TaskTrend>,
    val history: List<HistoryItem>,
    val notices: List<String>,
    val mockVehicle: Boolean,
)

/** 점검 기록 문구(규칙 기반 템플릿). 관찰값 표현만 — 짧아졌어요/길어졌어요/비슷해요. */
object ChecksCopy {
    const val COLLECTING = "아직 비교할 만큼 기록이 모이지 않았어요."
    const val HIDDEN = "이번 점검은 유효한 반응이 적어 수치를 보여 드리지 않아요."
    const val DESCRIPTIVE = "최근 점검의 기록이에요. 지난 기록과 나란히 살펴보세요."
    const val QUALITY_LIMITED = "점검 버전이나 기기가 달라 이전 기록과 비교하지 않았어요."
    const val NEW_VERSION = "이 활동은 점검 방식이 바뀌어 기록을 새로 모으고 있어요. 이전 방식의 기록과는 비교하지 않아요."
    const val NEW_DEVICE = "이 활동은 점검한 기기가 바뀌어 기록을 새로 모으고 있어요. 이전 기기의 기록과는 비교하지 않아요."
    const val SHORTER = "최근 반응시간이 이전 기록들보다 짧았어요."
    const val LONGER = "최근 반응시간이 이전 기록들보다 길었어요."
    const val SIMILAR = "최근 반응시간이 이전 기록들과 비슷해요."
    const val NOTICE_CONTEXT = "피로, 점검한 시간대, 주변 상황에 따라 기록은 달라질 수 있어요."
    const val NOTICE_LEARNING = "처음 몇 번은 익숙해지면서 기록이 달라질 수 있어요."
    const val NOTICE_NOT_HEALTH = "이 기록은 건강 상태를 알려 주지 않아요. 걱정되면 전문가와 상담해 주세요."
    const val NOTICE_PER_TASK = "세 활동은 서로 다른 과제라 걸린 시간을 활동끼리 비교하지 않아요."
    const val NOTICE_SIMILAR_BAND = "'비슷해요'는 이전 기록들과의 차이가 10% 안쪽이라는 뜻의 잠정 기준이에요. 건강 상태를 나누는 기준이 아니에요."

    fun all() = listOf(COLLECTING, HIDDEN, DESCRIPTIVE, QUALITY_LIMITED, NEW_VERSION, NEW_DEVICE, SHORTER, LONGER, SIMILAR, NOTICE_CONTEXT, NOTICE_LEARNING, NOTICE_NOT_HEALTH, NOTICE_PER_TASK, NOTICE_SIMILAR_BAND)
}

object ChecksReportBuilder {
    /** 상태 기준 — 모두 PROVISIONAL. */
    const val DESCRIPTIVE_FROM = 2
    const val TREND_FROM = 4
    /** "비슷해요" 폭. 잠정 기술통계 기준이며 임상적 정상 범위가 아니다(문구 NOTICE_SIMILAR_BAND). */
    const val SIMILAR_BAND = 0.10

    /** 과제별 비교 키(과제 버전·자극 세트 버전·기기). 키가 다르면 측정 조건이 달라 직접 비교하지 않는다. */
    private data class CompareKey(val taskVersion: Int?, val stimulusSetVersion: Int?, val deviceProfile: String)

    fun build(records: List<AssessmentRecord>): ChecksReport {
        val sorted = records.sortedBy { it.startedAt }
        val valid = sorted.filter { it.status == AssessmentStatus.COMPLETED }
        val tasks = TaskId.entries.map { id -> taskTrend(id, valid) }
        // 전체 상태는 안내 문구용. 과제마다 비교 범위가 다를 수 있어 실제 판단은 과제별 상태로 한다.
        val state = when {
            valid.size < DESCRIPTIVE_FROM -> ChecksReportState.COLLECTING
            // 방식·기기가 바뀌어 아직 새로 모으는 과제가 있을 때만.
            tasks.any { it.excludedCount > 0 && it.state == ChecksReportState.COLLECTING } -> ChecksReportState.QUALITY_LIMITED
            else -> tasks.maxOf { it.state }
        }
        val notices = buildList {
            add(ChecksCopy.NOTICE_CONTEXT)
            if (valid.size < TREND_FROM) add(ChecksCopy.NOTICE_LEARNING)
            add(ChecksCopy.NOTICE_NOT_HEALTH)
            add(ChecksCopy.NOTICE_PER_TASK)
            if (tasks.any { it.state == ChecksReportState.TREND_AVAILABLE }) add(ChecksCopy.NOTICE_SIMILAR_BAND)
        }
        return ChecksReport(
            state = state,
            validCount = valid.size,
            tasks = tasks,
            history = sorted.map { HistoryItem(it.assessmentId, it.startedAt, it.status) },
            notices = notices,
            mockVehicle = sorted.any { it.vehicleSource == "MOCK" },
        )
    }

    /** 과제 하나의 점검별 기록(시간 순, 마친 점검만). */
    fun series(id: TaskId, records: List<AssessmentRecord>): List<TaskPoint> =
        records.filter { it.status == AssessmentStatus.COMPLETED }.sortedBy { it.startedAt }.map { r ->
            val version = r.protocolVersions[id]
            val m = version?.let { ProtocolRegistry.find(id, it) }?.let { CheckMetrics.compute(id, r.trials, it) }
            TaskPoint(
                assessmentId = r.assessmentId,
                startedAt = r.startedAt,
                taskVersion = version,
                stimulusSetVersion = r.stimulusSetVersions[id],
                deviceProfile = r.deviceProfile,
                medianRtMs = m?.medianRtMs,
                accuracy = m?.accuracy,
                hiddenReason = if (m == null) "UNKNOWN_VERSION" else m.hiddenReason,
            )
        }

    private fun taskTrend(id: TaskId, valid: List<AssessmentRecord>): TaskTrend {
        if (valid.isEmpty()) return TaskTrend(id, null, null, ChecksCopy.COLLECTING)
        fun keyOf(r: AssessmentRecord) = CompareKey(r.protocolVersions[id], r.stimulusSetVersions[id], r.deviceProfile)
        val latestKey = keyOf(valid.last())
        val (same, other) = valid.partition { keyOf(it) == latestKey }
        // 기록이 쓰인 버전의 규칙(최소 시행 수 등)으로 계산한다.
        val protocol = latestKey.taskVersion?.let { ProtocolRegistry.find(id, it) }
        val metrics = same.map { r ->
            protocol?.let { CheckMetrics.compute(id, r.trials, it) } ?: TaskMetrics(id, 0, null, null, emptyMap(), "UNKNOWN_VERSION")
        }
        val latest = metrics.last()
        val state = when {
            same.size < DESCRIPTIVE_FROM -> ChecksReportState.COLLECTING
            same.size < TREND_FROM -> ChecksReportState.DESCRIPTIVE
            else -> ChecksReportState.TREND_AVAILABLE
        }
        val changed = when {
            other.isEmpty() -> null
            // 바로 앞의 다른 키 기록과 무엇이 달라졌는지로 사유를 정한다(가장 최근 변화).
            keyOf(other.last()).let { k -> k.taskVersion != latestKey.taskVersion || k.stimulusSetVersion != latestKey.stimulusSetVersion } ->
                TaskLimitReason.VERSION_CHANGED
            else -> TaskLimitReason.DEVICE_CHANGED
        }
        val direction = if (state == ChecksReportState.TREND_AVAILABLE) direction(latest, metrics.dropLast(1)) else null
        val text = when {
            latest.hiddenReason != null -> ChecksCopy.HIDDEN
            state == ChecksReportState.COLLECTING && changed == TaskLimitReason.VERSION_CHANGED -> ChecksCopy.NEW_VERSION
            state == ChecksReportState.COLLECTING && changed == TaskLimitReason.DEVICE_CHANGED -> ChecksCopy.NEW_DEVICE
            state == ChecksReportState.COLLECTING -> ChecksCopy.COLLECTING
            direction == "SHORTER" -> ChecksCopy.SHORTER
            direction == "LONGER" -> ChecksCopy.LONGER
            direction == "SIMILAR" -> ChecksCopy.SIMILAR
            else -> ChecksCopy.DESCRIPTIVE
        }
        return TaskTrend(
            taskId = id,
            latest = latest,
            direction = direction,
            text = text,
            state = state,
            taskVersion = latestKey.taskVersion,
            comparableCount = same.size,
            excludedCount = other.size,
            limitReason = if (latest.hiddenReason == "TOO_FEW_TRIALS") TaskLimitReason.TOO_FEW_TRIALS else changed,
        )
    }

    private fun direction(latest: TaskMetrics?, previous: List<TaskMetrics>): String? {
        val now = latest?.medianRtMs ?: return null
        val before = CheckMetrics.median(previous.mapNotNull { it.medianRtMs }) ?: return null
        val ratio = (now - before).toDouble() / before
        return when {
            abs(ratio) <= SIMILAR_BAND -> "SIMILAR"
            ratio < 0 -> "SHORTER"
            else -> "LONGER"
        }
    }
}
