package com.neulbom.drive.server.overview

import com.neulbom.drive.domain.driving.DrivingConfig
import com.neulbom.drive.domain.driving.WeeklySummarizer
import com.neulbom.drive.server.driving.DrivingStore
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class DrivingWeekDto(
    val weekStart: String,
    val drivingDays: Int,
    val drives: Int,
    val durationS: Double,
    val distanceKm: Double?,
    /** 그 주 거리가 짧으면 null(작은 분모로 흔들리지 않게). */
    val harshDecelPer100Km: Double?,
    /** SHOWN(값 있음, 0 포함) / NO_DRIVING / NOT_ENOUGH_DISTANCE / NO_SIGNAL — "계산 안 함"과 "0회"를 구분한다. */
    val harshDecelStatus: String,
    val nightShare: Double?,
    val mock: Boolean,
)

/** 주행 습관 레인. 이 레인은 주행 기록만 담는다. */
@Serializable
data class DrivingLaneDto(
    val weeks: List<DrivingWeekDto>,
    val drivingDays: Int,
    val drives: Int,
    val distanceKm: Double?,
    val algorithmVersions: List<String>,
    val text: String,
    val notes: List<String>,
)

object DrivingLaneCopy {
    const val NONE = "이 기간에는 주행 기록이 없어요."
    const val VERSION_NOTE = "이 기간 안에 주행 기록 계산 방식이 바뀌어, 바뀐 앞뒤 주는 서로 비교하지 않아요."

    fun decelNote(minKm: Int) = "급감속 기록은 그 주에 ${minKm}km 넘게 주행한 주만 보여 드려요. 길·날씨·교통 상황은 반영하지 않았어요."

    fun summary(weeks: Int, days: Int) = if (days == 0) NONE else "최근 ${weeks}주 동안 ${days}일 주행했어요."
}

/** @param weekStarts 오래된 주부터, 각 주의 월요일 */
fun drivingLane(store: DrivingStore, weekStarts: List<LocalDate>): DrivingLaneDto {
    val all = store.all()
    val weeks = weekStarts.map { WeeklySummarizer.summarize(it, all) }
    val versions = weeks.flatMap { it.algorithmVersions }.toSortedSet().toList()
    val days = weeks.sumOf { it.drivingDays }
    val distances = weeks.mapNotNull { it.distanceKm }
    return DrivingLaneDto(
        weeks = weeks.map { w ->
            val status = when {
                w.harshDecelPer100Km != null -> "SHOWN"
                w.sessions == 0 -> "NO_DRIVING"
                (w.distanceKm ?: 0.0) < DrivingConfig.DECEL_MIN_PERIOD_KM -> "NOT_ENOUGH_DISTANCE"
                else -> "NO_SIGNAL"
            }
            DrivingWeekDto(w.weekStart.toString(), w.drivingDays, w.sessions, w.durationS, w.distanceKm, w.harshDecelPer100Km, status, w.nightShare, w.mock)
        },
        drivingDays = days,
        drives = weeks.sumOf { it.sessions },
        distanceKm = if (distances.isEmpty()) null else (distances.sum() * 10).toLong() / 10.0,
        algorithmVersions = versions,
        text = DrivingLaneCopy.summary(weekStarts.size, days),
        notes = buildList {
            add(DrivingLaneCopy.decelNote(DrivingConfig.DECEL_MIN_PERIOD_KM.toInt()))
            if (versions.size > 1) add(DrivingLaneCopy.VERSION_NOTE)
        },
    )
}
