package com.neulbom.drive.domain.cognitive.assessment

import com.neulbom.drive.core.id.SeededUuidGenerator
import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.domain.cognitive.Block
import com.neulbom.drive.domain.cognitive.DenialReason
import com.neulbom.drive.domain.cognitive.ParkingSafetyGate
import com.neulbom.drive.domain.cognitive.ProvisionalProtocols
import com.neulbom.drive.domain.cognitive.ResponseKey
import com.neulbom.drive.domain.cognitive.StartResult
import com.neulbom.drive.domain.cognitive.TaskEngine
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.TrialResult
import com.neulbom.drive.domain.consent.ConsentType
import com.neulbom.drive.domain.consent.InMemoryConsentRepository
import com.neulbom.drive.vehicle.mock.MockScenario
import com.neulbom.drive.vehicle.mock.MockVehicleAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryTrialLogSink : TrialLogSink {
    val opened = mutableListOf<AssessmentSession>()
    val trials = mutableListOf<Triple<String, TaskId, TrialResult>>()
    var closed: AssessmentSession? = null
    var closeResult = SaveState.LOCAL_PENDING_SYNC
    override suspend fun open(session: AssessmentSession) { opened += session }
    override suspend fun append(assessmentId: String, taskId: TaskId, result: TrialResult) { trials += Triple(assessmentId, taskId, result) }
    override suspend fun close(session: AssessmentSession): SaveState { closed = session; return closeResult }
}

class AssessmentRunnerTest {
    private val clock = FakeClock()
    private val vehicle = MockVehicleAdapter(clock)
    private val consents = InMemoryConsentRepository(clock)
    private val sink = InMemoryTrialLogSink()
    private val foreground = MutableStateFlow(true)

    private fun TestScope.runner() = AssessmentRunner(
        gate = ParkingSafetyGate(), vehicle = vehicle, consents = consents, sink = sink,
        protocols = ProvisionalProtocols.ALL, appForeground = foreground, clock = clock, ids = SeededUuidGenerator(3),
        scope = backgroundScope, appVersion = "0.2.0", deviceProfile = "test", randomSeed = { 42L },
    )

    private fun TestScope.completeCurrentTask(runner: AssessmentRunner) {
        val running = runner.state.value as RunnerState.Running
        val engine: TaskEngine = running.engine
        while (true) {
            val spec = engine.current ?: break
            clock.advanceMs(spec.isiMs)
            engine.stimulusShown(clock.monotonicMs())
            val result = if (spec.expected == null) {
                clock.advanceMs(running.protocol.responseWindowMs); engine.windowElapsed(clock.monotonicMs())
            } else {
                clock.advanceMs(450); engine.respond(spec.expected!!, clock.monotonicMs())
            }
            runner.onTrialRecorded(result!!)
            runCurrent()
        }
    }

    @Test fun T01_cannotStartUnlessParkedSafe() = runTest {
        consents.update(ConsentType.COGNITIVE_CHECK, true)
        val runner = runner()
        listOf(MockScenario.DRIVING, MockScenario.SIGNAL_LOST, MockScenario.POWERING_OFF, MockScenario.STOPPED_AT_LIGHT).forEach {
            vehicle.setScenario(it)
            assertIs<StartResult.Denied>(runner.start(userInitiated = true, isFirstAssessment = true), it.name)
        }
        assertTrue(sink.opened.isEmpty())
    }

    @Test fun runsAllThreeTasksInOrder() = runTest {
        consents.update(ConsentType.COGNITIVE_CHECK, true)
        val runner = runner()
        assertIs<StartResult.Started>(runner.start(userInitiated = true, isFirstAssessment = false))
        runCurrent()
        repeat(3) { i ->
            assertEquals(TaskId.entries[i], (runner.state.value as RunnerState.Running).taskId)
            completeCurrentTask(runner)
            if (i < 2) {
                assertIs<RunnerState.Between>(runner.state.value)
                runner.beginNextTask()
            }
        }
        runCurrent()
        val finished = assertIs<RunnerState.Finished>(runner.state.value)
        assertEquals(AssessmentStatus.COMPLETED, finished.session.status)
        assertEquals(SaveState.LOCAL_PENDING_SYNC, finished.saveState)
        assertEquals(listOf(TaskId.VISUAL_ATTENTION, TaskId.GO_NO_GO, TaskId.TASK_SWITCHING), sink.trials.map { it.second }.distinct())
        assertEquals(24 + 40 + 32, sink.trials.size)
        assertEquals(mapOf(TaskId.VISUAL_ATTENTION to 3, TaskId.GO_NO_GO to 4, TaskId.TASK_SWITCHING to 2), finished.session.protocolVersions)
    }

    @Test fun T02_drivingMidTaskInterruptsAndBlocksInput() = runTest {
        consents.update(ConsentType.COGNITIVE_CHECK, true)
        val runner = runner()
        runner.start(userInitiated = true, isFirstAssessment = false)
        runCurrent()
        completeCurrentTask(runner)
        runner.beginNextTask()
        val engine = (runner.state.value as RunnerState.Running).engine
        engine.stimulusShown(clock.monotonicMs())
        vehicle.setScenario(MockScenario.DRIVING)
        runCurrent()
        assertNull(engine.respond(ResponseKey.TAP, clock.monotonicMs() + 300))
        val finished = assertIs<RunnerState.Finished>(runner.state.value)
        assertEquals(AssessmentStatus.INTERRUPTED, finished.session.status)
        assertEquals(DenialReason.DRIVING, finished.session.stopReason)
        assertEquals(AssessmentStatus.INTERRUPTED, sink.closed!!.status)
    }

    @Test fun drivingBetweenTasksInvalidatesWholeAssessment() = runTest {
        consents.update(ConsentType.COGNITIVE_CHECK, true)
        val runner = runner()
        runner.start(userInitiated = true, isFirstAssessment = false)
        runCurrent()
        completeCurrentTask(runner)
        assertIs<RunnerState.Between>(runner.state.value)
        val firstTaskTrials = sink.trials.size
        vehicle.setScenario(MockScenario.DRIVING)
        runCurrent()
        assertEquals(AssessmentStatus.INTERRUPTED, assertIs<RunnerState.Finished>(runner.state.value).session.status)
        assertEquals(firstTaskTrials, sink.trials.size, "이미 기록한 시행은 지우지 않는다")
    }

    @Test fun poweringOffInterruptsAndKeepsTrialLog() = runTest {
        consents.update(ConsentType.COGNITIVE_CHECK, true)
        val runner = runner()
        runner.start(userInitiated = true, isFirstAssessment = false)
        runCurrent()
        val running = runner.state.value as RunnerState.Running
        val spec = running.engine.current!!
        running.engine.stimulusShown(clock.monotonicMs())
        runner.onTrialRecorded(running.engine.respond(spec.expected!!, clock.monotonicMs() + 400)!!)
        runCurrent()
        vehicle.setScenario(MockScenario.POWERING_OFF)
        runCurrent()
        val finished = assertIs<RunnerState.Finished>(runner.state.value)
        assertEquals(DenialReason.POWERING_OFF, finished.session.stopReason)
        assertEquals(1, sink.trials.size)
    }

    @Test fun backgroundInterrupts() = runTest {
        consents.update(ConsentType.COGNITIVE_CHECK, true)
        val runner = runner()
        runner.start(userInitiated = true, isFirstAssessment = false)
        runCurrent()
        foreground.value = false
        runCurrent()
        assertEquals(DenialReason.APP_IN_BACKGROUND, assertIs<RunnerState.Finished>(runner.state.value).session.stopReason)
    }

    @Test fun practiceOnlyOnFirstAssessment() = runTest {
        consents.update(ConsentType.COGNITIVE_CHECK, true)
        val runner = runner()
        runner.start(userInitiated = true, isFirstAssessment = false)
        runCurrent()
        assertTrue((runner.state.value as RunnerState.Running).engine.plan.none { it.block == Block.PRACTICE })
        runner.cancelByUser()
        runCurrent()
        runner.reset()
        runner.start(userInitiated = true, isFirstAssessment = true)
        runCurrent()
        assertTrue((runner.state.value as RunnerState.Running).engine.plan.any { it.block == Block.PRACTICE })
    }

    @Test fun userCancelIsRecordedAsCancelled() = runTest {
        consents.update(ConsentType.COGNITIVE_CHECK, true)
        val runner = runner()
        runner.start(userInitiated = true, isFirstAssessment = false)
        runCurrent()
        runner.cancelByUser()
        runCurrent()
        assertEquals(AssessmentStatus.CANCELLED, assertIs<RunnerState.Finished>(runner.state.value).session.status)
    }

    @Test fun consentRevokedMeansNothingWritten() = runTest {
        val runner = runner()
        vehicle.setScenario(MockScenario.PARKED_VERIFIED)
        assertEquals(StartResult.Denied(DenialReason.CONSENT_MISSING), runner.start(userInitiated = true, isFirstAssessment = true))
        runCurrent()
        assertTrue(sink.opened.isEmpty() && sink.trials.isEmpty())
    }

    @Test fun sinkFailureYieldsSaveFailed() = runTest {
        consents.update(ConsentType.COGNITIVE_CHECK, true)
        sink.closeResult = SaveState.SAVE_FAILED
        val runner = runner()
        runner.start(userInitiated = true, isFirstAssessment = false)
        runCurrent()
        runner.cancelByUser()
        runCurrent()
        assertEquals(SaveState.SAVE_FAILED, assertIs<RunnerState.Finished>(runner.state.value).saveState)
    }
}
