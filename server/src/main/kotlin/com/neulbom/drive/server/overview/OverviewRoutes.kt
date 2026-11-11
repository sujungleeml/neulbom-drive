package com.neulbom.drive.server.overview

import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.server.REPORT_ZONE
import com.neulbom.drive.server.checks.CheckStore
import com.neulbom.drive.server.driving.DrivingStore
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/**
 * 한눈에 보기(D23): 주행 레인과 점검 레인을 **같은 주 단위 시간축에 따로** 담는다.
 * 두 레인을 합친 값, 함께 변했는지에 대한 설명, 위험·적격성 판정은 만들지 않는다(05 §3·§6, D15).
 */
@Serializable
data class OverviewDto(
    val weeks: Int,
    val from: String,
    val to: String,
    /** 공통 시간축: 각 주의 월요일 */
    val weekStarts: List<String>,
    /** 오늘(리포트 시간대). 마지막 주가 이 날을 포함하면 아직 진행 중인 주(부분 집계). */
    val asOf: String,
    val lastWeekInProgress: Boolean,
    val driving: DrivingLaneDto,
    val checks: ChecksLaneDto,
    val notices: List<String>,
)

object OverviewCopy {
    const val SIDE_BY_SIDE = "운전 기록과 점검 기록을 같은 기간으로 나란히 보여 드려요. 두 기록을 합치거나 서로 연결해 해석하지 않아요."
    const val NOT_JUDGEMENT = "이 화면은 건강 상태나 운전 능력을 알려 주지 않아요."
    const val MOCK = "모의 기록이 들어 있어요."
    const val PARTIAL_WEEK = "이번 주는 아직 진행 중이라 지금까지의 기록만 셌어요."
}

val OVERVIEW_WEEKS = listOf(4, 8, 12)

fun Route.overviewRoutes(driving: DrivingStore, checks: CheckStore, clock: Clock) {
    get("/v1/reports/me/overview") {
        val weeks = call.request.queryParameters["weeks"]?.toIntOrNull() ?: 8
        require(weeks in OVERVIEW_WEEKS) { "weeks must be one of $OVERVIEW_WEEKS" }
        val today = clock.now().atZone(REPORT_ZONE).toLocalDate()
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val starts = (weeks - 1 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }
        val d = drivingLane(driving, starts)
        val c = checksLane(checks, starts, REPORT_ZONE)
        call.respond(
            OverviewDto(
                weeks = weeks,
                from = starts.first().toString(),
                to = starts.last().plusDays(6).toString(),
                weekStarts = starts.map { it.toString() },
                asOf = today.toString(),
                lastWeekInProgress = today.isBefore(starts.last().plusDays(6)),
                driving = d,
                checks = c,
                notices = buildList {
                    add(OverviewCopy.SIDE_BY_SIDE)
                    if (today.isBefore(starts.last().plusDays(6))) add(OverviewCopy.PARTIAL_WEEK)
                    if (d.weeks.any { it.mock } || c.weeks.any { it.mock }) add(OverviewCopy.MOCK)
                    add(OverviewCopy.NOT_JUDGEMENT)
                },
            ),
        )
    }
}
