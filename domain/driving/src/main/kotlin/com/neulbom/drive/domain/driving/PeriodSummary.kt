package com.neulbom.drive.domain.driving

import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.core.model.SignalId
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/** 한 달 요약(명세 v0.6 08 §3). 값이 null이면 `unavailable`에 이유 템플릿 ID가 있다. */
data class PeriodSummary(
    val period: YearMonth,
    val algorithmVersion: String,
    val origin: DataOrigin,
    val drivingDays: Int,
    val sessions: Int,
    val durationS: Double,
    val distanceKm: Double?,
    val harshDecelPer100Km: Double?,
    val nightShare: Double?,
    val limitedShare: Double,
    val signalSet: Set<SignalId>,
    val unavailable: Map<String, String>,
)

object PeriodSummarizer {

    fun summarize(period: YearMonth, items: List<Pair<DrivingSession, SessionFeatures>>): PeriodSummary {
        val included = items
            .filter { (s, f) -> isIncluded(f) && YearMonth.from(s.startedAt.atZone(s.zone)) == period }
            .sortedBy { it.first.startedAt }
        val signals = included.flatMap { it.first.signals }.toSet()
        val versions = included.map { it.second.algorithmVersion }.toSet()
        val unavailable = mutableMapOf<String, String>()

        val hasSpeed = included.isEmpty() || SignalId.SPEED in signals
        val distance = if (hasSpeed) included.sumOf { it.second.distanceKm ?: 0.0 } else null
        if (!hasSpeed) unavailable["distance"] = "UNAVAILABLE_SPEED"

        val decelAvailable = included.isNotEmpty() && included.all { it.second.harshDecelCount != null }
        val decel = when {
            included.isNotEmpty() && !decelAvailable -> { unavailable["harsh_decel_per_100km"] = "UNAVAILABLE_DECEL_SIGNAL"; null }
            distance == null || distance < DrivingConfig.DECEL_MIN_PERIOD_KM -> { unavailable["harsh_decel_per_100km"] = "UNAVAILABLE_DECEL_DISTANCE"; null }
            else -> round1(included.sumOf { it.second.harshDecelCount ?: 0 } / distance * 100.0)
        }

        val totalS = included.sumOf { it.second.durationS }
        return PeriodSummary(
            period = period,
            algorithmVersion = versions.singleOrNull() ?: DrivingConfig.ALGORITHM_VERSION,
            origin = if (included.any { it.first.origin == DataOrigin.MOCK }) DataOrigin.MOCK else DataOrigin.REAL,
            drivingDays = included.map { (s, _) -> s.startedAt.atZone(s.zone).toLocalDate() }.toSet().size,
            sessions = included.size,
            durationS = totalS,
            distanceKm = distance?.let(::round1),
            harshDecelPer100Km = decel,
            nightShare = if (totalS <= 0) null else included.sumOf { it.second.nightShare * it.second.durationS } / totalS,
            limitedShare = if (included.isEmpty()) 0.0 else included.count { it.second.quality == SessionQuality.LIMITED }.toDouble() / included.size,
            signalSet = signals,
            unavailable = unavailable,
        )
    }

    private fun round1(v: Double) = (v * 10).roundToLong() / 10.0

    /** 리포트에 넣는 세션(귀속·최소 길이를 만족하고 제외 품질이 아님). 월·주 요약이 같은 규칙을 쓴다. */
    fun isIncluded(f: SessionFeatures) = f.valid && f.quality != SessionQuality.EXCLUDED
}

/** 한 주(월요일 시작)의 주행 요약. 월간 요약과 같은 포함 규칙을 쓴다. 해석·점수 없음. */
data class WeekSummary(
    val weekStart: LocalDate,
    val drivingDays: Int,
    val sessions: Int,
    val durationS: Double,
    val distanceKm: Double?,
    /** 그 주 거리가 [DrivingConfig.DECEL_MIN_PERIOD_KM] 미만이거나 감속 신호가 없으면 null(작은 분모로 흔들리지 않게). */
    val harshDecelPer100Km: Double?,
    val nightShare: Double?,
    val mock: Boolean,
    val algorithmVersions: Set<String>,
)

object WeeklySummarizer {
    fun summarize(weekStart: LocalDate, items: List<Pair<DrivingSession, SessionFeatures>>): WeekSummary {
        val end = weekStart.plusDays(7)
        val included = items.filter { (s, f) ->
            val day = s.startedAt.atZone(s.zone).toLocalDate()
            PeriodSummarizer.isIncluded(f) && !day.isBefore(weekStart) && day.isBefore(end)
        }
        val hasSpeed = included.all { SignalId.SPEED in it.first.signals }
        val distance = if (included.isNotEmpty() && hasSpeed) included.sumOf { it.second.distanceKm ?: 0.0 } else null
        val decel = if (distance != null && distance >= DrivingConfig.DECEL_MIN_PERIOD_KM && included.all { it.second.harshDecelCount != null }) {
            (included.sumOf { it.second.harshDecelCount ?: 0 } / distance * 100.0 * 10).roundToLong() / 10.0
        } else {
            null
        }
        val totalS = included.sumOf { it.second.durationS }
        return WeekSummary(
            weekStart = weekStart,
            drivingDays = included.map { (s, _) -> s.startedAt.atZone(s.zone).toLocalDate() }.toSet().size,
            sessions = included.size,
            durationS = totalS,
            distanceKm = distance?.let { (it * 10).roundToLong() / 10.0 },
            harshDecelPer100Km = decel,
            nightShare = if (totalS <= 0) null else included.sumOf { it.second.nightShare * it.second.durationS } / totalS,
            mock = included.any { it.first.origin == DataOrigin.MOCK },
            algorithmVersions = included.map { it.second.algorithmVersion }.toSet(),
        )
    }
}
