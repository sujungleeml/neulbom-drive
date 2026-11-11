package com.neulbom.drive.domain.scheduler

import com.neulbom.drive.core.model.SafetyState
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleHistoryTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val now = Instant.parse("2026-11-05T03:00:00Z") // 12:00 KST

    private fun input(completed: List<Instant>, offers: List<OfferRecord>) = ScheduleHistory.input(
        now = now, safety = SafetyState.PARKED_SAFE, consentGranted = true, onboardingDone = true,
        completedAt = completed, offers = offers, zone = seoul,
    )

    @Test
    fun offersCountOnlySinceLastValidAssessment() {
        val lastValid = Instant.parse("2026-11-02T03:00:00Z")
        val i = input(
            completed = listOf(Instant.parse("2026-11-01T03:00:00Z"), lastValid),
            offers = listOf(
                OfferRecord(Instant.parse("2026-11-01T02:00:00Z"), OfferResponse.ACCEPTED),
                OfferRecord(Instant.parse("2026-11-03T03:00:00Z"), OfferResponse.LATER),
                OfferRecord(Instant.parse("2026-11-04T03:00:00Z"), OfferResponse.LATER),
            ),
        )
        assertEquals(2, i.validAssessmentCount)
        assertEquals(lastValid, i.lastValidAssessmentAt)
        assertEquals(2, i.offersInCurrentCycle)
        assertEquals(Instant.parse("2026-11-04T03:00:00Z"), i.lastOfferAt)
        assertTrue(i.lastOfferDeclined)
        assertNull(i.suspendedUntil)
    }

    @Test
    fun notTodaySuspendsUntilNextLocalMidnight() {
        val offered = Instant.parse("2026-11-05T01:00:00Z") // 10:00 KST
        val i = input(emptyList(), listOf(OfferRecord(offered, OfferResponse.NOT_TODAY)))
        assertEquals(Instant.parse("2026-11-05T15:00:00Z"), i.suspendedUntil) // 11/6 00:00 KST
    }

    @Test
    fun acceptedOfferIsNotDeclined() {
        val i = input(emptyList(), listOf(OfferRecord(Instant.parse("2026-11-04T01:00:00Z"), OfferResponse.ACCEPTED)))
        assertFalse(i.lastOfferDeclined)
        assertEquals(1, i.offersInCurrentCycle)
    }

    @Test
    fun noHistoryMeansFirstAssessmentOffer() {
        val d = Scheduler().decide(input(emptyList(), emptyList()))
        assertEquals(OfferDecision.Offer(isFirstAssessment = true, includeCheckIn = false), d)
    }

    @Test
    fun pauseSuspendsOffersUntilItEnds() {
        val paused = Instant.parse("2026-11-12T00:00:00Z")
        val i = ScheduleHistory.input(
            now = now, safety = SafetyState.PARKED_SAFE, consentGranted = true, onboardingDone = true,
            completedAt = emptyList(), offers = emptyList(), zone = seoul, pausedUntil = paused,
        )
        assertEquals(paused, i.suspendedUntil)
        assertTrue(Scheduler().decide(i) is OfferDecision.NoOffer)
        val notTodayLater = ScheduleHistory.input(
            now = now, safety = SafetyState.PARKED_SAFE, consentGranted = true, onboardingDone = true,
            completedAt = emptyList(), offers = listOf(OfferRecord(Instant.parse("2026-11-05T01:00:00Z"), OfferResponse.NOT_TODAY)),
            zone = seoul, pausedUntil = Instant.parse("2026-11-05T05:00:00Z"),
        )
        assertEquals(Instant.parse("2026-11-05T15:00:00Z"), notTodayLater.suspendedUntil) // 더 늦은 쪽
    }

    /** R-1: 한 주기에 3번 모두 수락되지 않아도, 마지막 제안 28일 뒤에는 새 주기로 다시 제안한다. */
    @Test
    fun fullCycleRestartsAfterRoutineIntervalFromLastOffer() {
        val lastValid = Instant.parse("2026-08-01T03:00:00Z")
        val offers = listOf("2026-09-01", "2026-09-02", "2026-09-03").map { OfferRecord(Instant.parse("${it}T03:00:00Z"), OfferResponse.LATER) }
        fun at(t: String) = ScheduleHistory.input(
            now = Instant.parse(t), safety = SafetyState.PARKED_SAFE, consentGranted = true, onboardingDone = true,
            completedAt = listOf(lastValid), offers = offers, zone = seoul,
        )
        // 27일째: 아직 같은 주기 → 제한
        val before = at("2026-09-30T02:00:00Z")
        assertEquals(3, before.offersInCurrentCycle)
        assertEquals(OfferDecision.NoOffer(ScheduleState.DEFERRED, "CYCLE_LIMIT"), Scheduler().decide(before))
        // 28일 뒤: 새 주기 → 제안
        val after = at("2026-10-01T03:00:00Z")
        assertEquals(0, after.offersInCurrentCycle)
        assertTrue(Scheduler().decide(after) is OfferDecision.Offer)
    }

    @Test
    fun restartedCycleIsAlsoLimitedAndRestartsAgain() {
        val day = { d: String -> OfferRecord(Instant.parse("${d}T03:00:00Z"), OfferResponse.LATER) }
        val offers = listOf("2026-01-01", "2026-01-02", "2026-01-03", "2026-02-01", "2026-02-02", "2026-02-03").map(day)
        fun count(t: String) = ScheduleHistory.input(
            now = Instant.parse(t), safety = SafetyState.PARKED_SAFE, consentGranted = true, onboardingDone = true,
            completedAt = emptyList(), offers = offers, zone = seoul,
        ).offersInCurrentCycle
        assertEquals(3, count("2026-02-10T03:00:00Z"))  // 두 번째 주기도 3번 참
        assertEquals(0, count("2026-03-04T03:00:00Z"))  // 그 마지막 제안(2/3) 28일 뒤 → 세 번째 주기
    }

    @Test
    fun validAssessmentStillStartsNewCycleImmediately() {
        val offers = listOf("2026-11-01", "2026-11-02", "2026-11-03").map { OfferRecord(Instant.parse("${it}T03:00:00Z"), OfferResponse.LATER) }
        val i = input(listOf(Instant.parse("2026-11-04T03:00:00Z")), offers)
        assertEquals(0, i.offersInCurrentCycle)
    }
}
