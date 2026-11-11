package com.neulbom.drive.presentation

import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.domain.cognitive.assessment.AssessmentSession
import com.neulbom.drive.domain.cognitive.assessment.AssessmentStatus
import com.neulbom.drive.domain.cognitive.assessment.RunnerState
import com.neulbom.drive.domain.cognitive.assessment.SaveState
import com.neulbom.drive.domain.cognitive.TaskId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CarNavigatorTest {
    private val ready = CarFlowState(profile = ProfileAnswer.CONFIRMED)
    private fun screen(
        safety: SafetyState = SafetyState.PARKED_SAFE,
        onboarded: Boolean = true,
        flow: CarFlowState = ready,
        runner: RunnerState = RunnerState.Idle,
    ) = CarNavigator.screen(safety, onboarded, flow, runner)

    private fun finished(status: AssessmentStatus) = RunnerState.Finished(
        AssessmentSession("a", Instant.EPOCH, Instant.EPOCH, status, null, false, emptyMap(), "t", "t"),
        SaveState.LOCAL_PENDING_SYNC,
    )

    @Test
    fun drivingAlwaysWins() {
        listOf(RunnerState.Idle, RunnerState.Saving("a"), finished(AssessmentStatus.COMPLETED)).forEach {
            assertEquals(CarScreen.DRIVING, screen(safety = SafetyState.DRIVING, runner = it))
        }
        assertEquals(CarScreen.DRIVING, screen(safety = SafetyState.DRIVING, onboarded = false))
        assertEquals(CarScreen.DRIVING, screen(safety = SafetyState.DRIVING, flow = ready.copy(offerPending = true, guideOpen = true)))
    }

    @Test
    fun onboardingThenProfileThenIdle() {
        assertEquals(CarScreen.ONBOARDING, screen(onboarded = false))
        assertEquals(CarScreen.PROFILE, screen(flow = CarFlowState()))
        assertEquals(CarScreen.IDLE, screen())
    }

    @Test
    fun pendingOfferShowsOfferAndGuideTakesPrecedence() {
        assertEquals(CarScreen.OFFER, screen(flow = ready.copy(offerPending = true)))
        assertEquals(CarScreen.GUIDE, screen(flow = ready.copy(offerPending = true, guideOpen = true)))
    }

    @Test
    fun runnerStatesMapToScreens() {
        assertEquals(CarScreen.BETWEEN, screen(runner = RunnerState.Between("a", 1, TaskId.GO_NO_GO)))
        assertEquals(CarScreen.SAVING, screen(runner = RunnerState.Saving("a")))
        assertEquals(CarScreen.COMPLETION, screen(runner = finished(AssessmentStatus.COMPLETED)))
        assertEquals(CarScreen.INTERRUPTED, screen(runner = finished(AssessmentStatus.INTERRUPTED)))
        assertEquals(CarScreen.INTERRUPTED, screen(runner = finished(AssessmentStatus.CANCELLED)))
    }

    @Test
    fun routineCheckInComesBeforeCompletion() {
        val routine = ready.copy(includeCheckIn = true)
        assertEquals(CarScreen.CHECK_IN, screen(flow = routine, runner = finished(AssessmentStatus.COMPLETED)))
        assertEquals(CarScreen.COMPLETION, screen(flow = routine.copy(checkInDone = true), runner = finished(AssessmentStatus.COMPLETED)))
    }

    @Test
    fun unverifiedParkingDoesNotHideTheScreenButOffersNothing() {
        assertEquals(CarScreen.IDLE, screen(safety = SafetyState.PARKED_UNVERIFIED, flow = ready.copy(offerPending = true)))
    }
}
