package com.neulbom.drive.server.journey

import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.domain.driving.PeriodSummarizer
import com.neulbom.drive.server.REPORT_ZONE
import com.neulbom.drive.server.checks.CheckStore
import com.neulbom.drive.server.driving.DrivingStore
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.YearMonth

@Serializable
data class JourneyDrivingDto(val drivingDays: Int, val drives: Int, val durationS: Double, val distanceKm: Double?, val mock: Boolean)

@Serializable
data class JourneyChecksDto(val completed: Int, val stopped: Int, val mock: Boolean)

/** 한 달. 두 축은 서로 다른 객체로만 담는다(합친 값·점수 없음). */
@Serializable
data class JourneyMonthDto(val period: String, val driving: JourneyDrivingDto, val checks: JourneyChecksDto, val text: String)

@Serializable
data class JourneyDto(val months: List<JourneyMonthDto>, val notices: List<String>)

/** 나의 여정 문구: 사실만, 많고 적음을 평가하지 않는다(배지·순위·연속 기록 없음). */
object JourneyCopy {
    const val EMPTY = "기록이 없는 달이에요."
    const val NOTICE_NO_JUDGE = "달마다 운전하고 점검한 기록을 모아 보여 드릴 뿐, 많고 적음을 평가하지 않아요."
    const val NOTICE_MOCK = "모의 기록이 들어 있는 달은 따로 표시해요."

    fun month(drivingDays: Int, completedChecks: Int): String = when {
        drivingDays == 0 && completedChecks == 0 -> EMPTY
        completedChecks == 0 -> "${drivingDays}일 운전했어요."
        drivingDays == 0 -> "점검을 ${completedChecks}번 마쳤어요."
        else -> "${drivingDays}일 운전했고, 점검을 ${completedChecks}번 마쳤어요."
    }
}

const val MAX_JOURNEY_MONTHS = 12

/** 나의 여정(W-03): 최근 몇 달의 운전·점검 참여 기록. */
fun Route.journeyRoutes(driving: DrivingStore, checks: CheckStore, clock: Clock) {
    get("/v1/reports/me/journey") {
        val count = call.request.queryParameters["months"]?.toIntOrNull() ?: 6
        require(count in 1..MAX_JOURNEY_MONTHS) { "months must be 1..$MAX_JOURNEY_MONTHS" }
        val now = YearMonth.from(clock.now().atZone(REPORT_ZONE))
        val drives = driving.all()
        val uploads = checks.all()
        val months = (0 until count).map { back ->
            val period = now.minusMonths(back.toLong())
            val s = PeriodSummarizer.summarize(period, drives)
            val inMonth = uploads.filter { YearMonth.from(Instant.parse(it.session.startedAt).atZone(REPORT_ZONE)) == period }
            val completed = inMonth.count { it.session.status == "COMPLETED" }
            val stopped = inMonth.count { it.session.status == "INTERRUPTED" || it.session.status == "CANCELLED" }
            JourneyMonthDto(
                period = period.toString(),
                driving = JourneyDrivingDto(s.drivingDays, s.sessions, s.durationS, s.distanceKm, s.sessions > 0 && s.origin == DataOrigin.MOCK),
                checks = JourneyChecksDto(completed, stopped, inMonth.any { it.session.vehicleSource == "MOCK" }),
                text = JourneyCopy.month(s.drivingDays, completed),
            )
        }
        call.respond(JourneyDto(months, listOf(JourneyCopy.NOTICE_NO_JUDGE, JourneyCopy.NOTICE_MOCK)))
    }
}
