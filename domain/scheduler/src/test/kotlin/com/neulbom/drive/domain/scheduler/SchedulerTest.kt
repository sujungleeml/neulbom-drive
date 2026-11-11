package com.neulbom.drive.domain.scheduler

import com.neulbom.drive.core.model.SafetyState
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SchedulerTest {
    private val now = Instant.parse("2026-11-10T03:00:00Z")
    private val scheduler = Scheduler(SchedulerConfig())

    private fun input(
        safety: SafetyState = SafetyState.PARKED_SAFE,
        consent: Boolean = true,
        onboarded: Boolean = true,
        suspendedUntil: Instant? = null,
        valid: Int = 0,
        lastValid: Instant? = null,
        offers: Int = 0,
        lastOffer: Instant? = null,
        declined: Boolean = false,
    ) = ScheduleInput(now, safety, consent, onboarded, suspendedUntil, valid, lastValid, offers, lastOffer, declined)

    @Test fun firstOfferRightAfterOnboardingWhenParkedSafe() {
        assertEquals(OfferDecision.Offer(isFirstAssessment = true, includeCheckIn = false), scheduler.decide(input()))
        assertEquals(ScheduleState.ONBOARDING, scheduler.state(input(onboarded = false)))
        assertIs<OfferDecision.NoOffer>(scheduler.decide(input(onboarded = false)))
    }

    @Test fun neverOffersUnlessParkedSafe() {
        SafetyState.entries.filter { it != SafetyState.PARKED_SAFE }.forEach {
            assertIs<OfferDecision.NoOffer>(scheduler.decide(input(safety = it)), it.name)
        }
    }

    @Test fun noOfferWithoutConsent() {
        assertIs<OfferDecision.NoOffer>(scheduler.decide(input(consent = false)))
    }

    @Test fun declinedOfferNotRepeatedWithin24h() {
        val recent = input(valid = 1, lastValid = now.minus(Duration.ofDays(3)), offers = 1, lastOffer = now.minus(Duration.ofHours(23)), declined = true)
        val d = assertIs<OfferDecision.NoOffer>(scheduler.decide(recent))
        assertEquals(ScheduleState.DEFERRED, d.state)
        assertIs<OfferDecision.Offer>(scheduler.decide(recent.copy(lastOfferAt = now.minus(Duration.ofHours(25)))))
    }

    @Test fun atMostThreeOffersPerCycle() {
        val third = input(valid = 2, lastValid = now.minus(Duration.ofDays(5)), offers = 3, lastOffer = now.minus(Duration.ofDays(2)), declined = true)
        assertEquals("CYCLE_LIMIT", assertIs<OfferDecision.NoOffer>(scheduler.decide(third)).reason)
    }

    @Test fun baselineCollectingOffersAfterInterval() {
        val s = input(valid = 2, lastValid = now.minus(Duration.ofDays(2)), lastOffer = now.minus(Duration.ofDays(2)))
        assertEquals(ScheduleState.BASELINE_COLLECTING, scheduler.state(s))
        assertEquals(OfferDecision.Offer(isFirstAssessment = false, includeCheckIn = false), scheduler.decide(s))
    }

    @Test fun routineDueAfter28DaysOnceBaselineReady() {
        val ready = input(valid = 4, lastValid = now.minus(Duration.ofDays(10)), lastOffer = now.minus(Duration.ofDays(10)))
        assertEquals(ScheduleState.BASELINE_READY, scheduler.state(ready))
        assertIs<OfferDecision.NoOffer>(scheduler.decide(ready))
        val due = ready.copy(lastValidAssessmentAt = now.minus(Duration.ofDays(28)), lastOfferAt = now.minus(Duration.ofDays(28)))
        assertEquals(ScheduleState.ROUTINE_DUE, scheduler.state(due))
        assertEquals(OfferDecision.Offer(isFirstAssessment = false, includeCheckIn = true), scheduler.decide(due))
    }

    @Test fun checkInOnlyWithRoutine() {
        val collecting = input(valid = 3, lastValid = now.minus(Duration.ofDays(40)))
        assertEquals(false, (scheduler.decide(collecting) as OfferDecision.Offer).includeCheckIn)
    }

    @Test fun suspendedMeansNoOffer() {
        val s = input(suspendedUntil = now.plus(Duration.ofDays(7)))
        assertEquals(ScheduleState.SUSPENDED, scheduler.state(s))
        assertIs<OfferDecision.NoOffer>(scheduler.decide(s))
        assertIs<OfferDecision.Offer>(scheduler.decide(s.copy(suspendedUntil = now.minusSeconds(1))))
    }

    @Test fun inputHasNoAssessmentResultFields() {
        // 결과(반응시간·정확도 등)로 제안 빈도를 바꾸지 않는다(명세 03-4 규칙 7): 입력 타입에 결과 필드가 없다.
        val names = ScheduleInput::class.java.declaredFields.map { it.name.lowercase() }
        listOf("rt", "reaction", "accuracy", "score", "metric", "result").forEach { w -> assertEquals(emptyList(), names.filter { it.contains(w) }, w) }
    }

    @Test fun configValuesAreProvisional() {
        val c = SchedulerConfig()
        assertEquals(listOf(24L, 3L, 4L, 28L), listOf(c.minOfferIntervalHours, c.maxOffersPerCycle.toLong(), c.baselineValidAssessments.toLong(), c.routineIntervalDays))
        assertEquals(com.neulbom.drive.domain.cognitive.ProtocolStatus.PROVISIONAL, c.status)
    }
}
