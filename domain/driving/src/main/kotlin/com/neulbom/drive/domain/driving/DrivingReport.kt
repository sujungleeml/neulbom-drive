package com.neulbom.drive.domain.driving

import com.neulbom.drive.core.model.DataOrigin
import java.time.YearMonth
import kotlin.math.abs

enum class ReportState { COLLECTING, DESCRIPTIVE, TREND_AVAILABLE, QUALITY_LIMITED }

/** 관측값 방향만 표현한다(좋아짐/나빠짐 판단 없음). */
enum class Direction { MORE, LESS, SIMILAR }

data class MetricLine(
    val key: String,
    val value: Double?,
    val unit: String,
    val direction: Direction?,
    val templateId: String,
    /** 보조 값(예: 주행한 날 줄의 주행 횟수). */
    val extra: Double? = null,
)

data class DrivingReport(
    val period: YearMonth,
    val state: ReportState,
    val origin: DataOrigin,
    val algorithmVersion: String,
    val lines: List<MetricLine>,
    val notices: List<String>,
)

object DrivingReportBuilder {

    fun build(current: PeriodSummary, previous: PeriodSummary?): DrivingReport {
        val state = when {
            current.sessions == 0 -> ReportState.COLLECTING
            previous == null || !enough(previous) || !enough(current) -> ReportState.DESCRIPTIVE
            previous.signalSet != current.signalSet || previous.algorithmVersion != current.algorithmVersion -> ReportState.QUALITY_LIMITED
            else -> ReportState.TREND_AVAILABLE
        }
        fun dir(cur: Double?, prev: Double?): Direction? {
            if (state != ReportState.TREND_AVAILABLE || cur == null || prev == null) return null
            if (prev == 0.0) return if (cur == 0.0) Direction.SIMILAR else Direction.MORE
            val rel = (cur - prev) / prev
            return when {
                abs(rel) < DrivingConfig.SIMILAR_RELATIVE_DIFF -> Direction.SIMILAR
                rel > 0 -> Direction.MORE
                else -> Direction.LESS
            }
        }
        fun line(key: String, value: Double?, unit: String, prev: Double?, extra: Double? = null) = MetricLine(
            key = key,
            value = value,
            unit = unit,
            direction = dir(value, prev),
            templateId = if (value == null) current.unavailable[key] ?: "COLLECTING" else DrivingCopy.METRIC_TEMPLATE.getValue(key),
            extra = extra,
        )
        val lines = if (state == ReportState.COLLECTING) {
            emptyList()
        } else {
            listOf(
                line("driving_days", current.drivingDays.toDouble(), "일", previous?.drivingDays?.toDouble(), extra = current.sessions.toDouble()),
                line("duration", current.durationS, "s", previous?.durationS),
                line("distance", current.distanceKm, "km", previous?.distanceKm),
                line("harsh_decel_per_100km", current.harshDecelPer100Km, "회/100km", previous?.harshDecelPer100Km),
                line("night_share", current.nightShare, "%", previous?.nightShare),
            )
        }
        val notices = buildList {
            when (state) {
                ReportState.COLLECTING -> add("COLLECTING")
                ReportState.DESCRIPTIVE -> add("COMPARISON_NOT_YET")
                ReportState.QUALITY_LIMITED -> add("QUALITY_LIMITED")
                ReportState.TREND_AVAILABLE -> Unit
            }
            if (current.limitedShare > 0) add("LIMITED_SESSIONS")
            if (current.harshDecelPer100Km != null) add("DECEL_IS_RECORD")
            add("EXTERNAL_CONDITIONS_NOT_ADJUSTED")
            if (current.origin == DataOrigin.MOCK) add("MOCK_DATA")
        }
        return DrivingReport(current.period, state, current.origin, current.algorithmVersion, lines, notices)
    }

    private fun enough(s: PeriodSummary) =
        s.drivingDays >= DrivingConfig.TREND_MIN_DRIVING_DAYS && (s.distanceKm ?: 0.0) >= DrivingConfig.TREND_MIN_DISTANCE_KM
}
