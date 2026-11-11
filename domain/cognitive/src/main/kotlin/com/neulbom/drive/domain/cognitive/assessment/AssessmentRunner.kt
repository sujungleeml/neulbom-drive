package com.neulbom.drive.domain.cognitive.assessment

import com.neulbom.drive.core.id.IdGenerator
import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.domain.cognitive.DenialReason
import com.neulbom.drive.domain.cognitive.GateDecision
import com.neulbom.drive.domain.cognitive.ParkingSafetyGate
import com.neulbom.drive.domain.cognitive.StartResult
import com.neulbom.drive.domain.cognitive.TaskEngine
import com.neulbom.drive.domain.cognitive.TaskPlans
import com.neulbom.drive.domain.cognitive.TaskProtocol
import com.neulbom.drive.domain.cognitive.TrialResult
import com.neulbom.drive.domain.consent.ConsentRepository
import com.neulbom.drive.domain.consent.ConsentType
import com.neulbom.drive.vehicle.api.VehicleAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 세 과제를 이어서 시행하는 점검 실행기(명세 v0.6 03, D11, V-06, V-09).
 *  - 시작: 주차 안전 게이트가 허용할 때만. 첫 점검만 연습 시행 포함.
 *  - 진행 중·과제 사이: 차량 상태·앱 전경을 계속 감시하고, 조건이 깨지면 즉시 엔진을 멈추고(입력 차단) INTERRUPTED로 끝낸다.
 *  - 시행 결과는 기록되는 즉시 [TrialLogSink]에 쓴다. 이미 쓴 시행은 지우지 않고 세션 상태로 무효를 표시한다.
 *  - 완료 화면에는 저장 상태만 넘긴다(점수 없음, D12).
 */
class AssessmentRunner(
    private val gate: ParkingSafetyGate,
    private val vehicle: VehicleAdapter,
    private val consents: ConsentRepository,
    private val sink: TrialLogSink,
    private val protocols: List<TaskProtocol>,
    private val appForeground: StateFlow<Boolean>,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val scope: CoroutineScope,
    private val appVersion: String,
    private val deviceProfile: String,
    private val randomSeed: () -> Long = { clock.now().toEpochMilli() },
) {
    private val stateFlow = MutableStateFlow<RunnerState>(RunnerState.Idle)
    val state: StateFlow<RunnerState> = stateFlow.asStateFlow()

    private var session: AssessmentSession? = null
    private var engine: TaskEngine? = null
    private var random = Random(0)
    private var watchJob: Job? = null

    /** sink 쓰기를 한 줄로 세워 open → append… → close 순서를 보장한다. */
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch { for (w in writes) w() }
    }

    fun currentDecision(userInitiated: Boolean = true): GateDecision = gate.evaluateStart(
        state = vehicle.observeState().value,
        capabilities = vehicle.capabilityFlags(),
        userInitiated = userInitiated,
        appInForeground = appForeground.value,
        cognitiveConsent = consents.state.value.isGranted(ConsentType.COGNITIVE_CHECK),
    )

    fun start(userInitiated: Boolean, isFirstAssessment: Boolean): StartResult {
        if (stateFlow.value !is RunnerState.Idle && stateFlow.value !is RunnerState.Finished) {
            return StartResult.Denied(DenialReason.NOT_USER_INITIATED)
        }
        val decision = currentDecision(userInitiated)
        if (decision is GateDecision.Denied) return StartResult.Denied(decision.reason)

        random = Random(randomSeed())
        val s = AssessmentSession(
            assessmentId = ids.newId(),
            startedAt = clock.now(),
            endedAt = null,
            status = AssessmentStatus.IN_PROGRESS,
            stopReason = null,
            isFirstAssessment = isFirstAssessment,
            protocolVersions = protocols.associate { it.taskId to it.taskVersion },
            appVersion = appVersion,
            deviceProfile = deviceProfile,
        )
        session = s
        writes.trySend { sink.open(s) }
        startTask(0)
        watch()
        return StartResult.Started(s.assessmentId)
    }

    private fun startTask(index: Int) {
        val s = session ?: return
        val p = protocols[index]
        val e = TaskEngine(TaskPlans.forProtocol(p, s.isFirstAssessment, random), p.responseWindowMs, p.anticipationMs)
        engine = e
        stateFlow.value = RunnerState.Running(s.assessmentId, index, p.taskId, p, e)
    }

    /** UI가 시행 하나를 기록할 때마다 호출. */
    fun onTrialRecorded(result: TrialResult) {
        val running = stateFlow.value as? RunnerState.Running ?: return
        val id = running.assessmentId
        val task = running.taskId
        writes.trySend { sink.append(id, task, result) }
        if (!running.engine.isComplete) return
        val next = running.taskIndex + 1
        if (next < protocols.size) {
            stateFlow.value = RunnerState.Between(id, next, protocols[next].taskId)
        } else {
            finish(AssessmentStatus.COMPLETED, null)
        }
    }

    /** 과제 사이 안내 화면에서 "다음"을 눌렀을 때. 다시 안전 조건을 확인한다. */
    fun beginNextTask() {
        val between = stateFlow.value as? RunnerState.Between ?: return
        val stop = gate.stopReason(vehicle.observeState().value, vehicle.capabilityFlags(), appForeground.value)
        if (stop != null) {
            finish(AssessmentStatus.INTERRUPTED, stop)
            return
        }
        startTask(between.nextTaskIndex)
    }

    fun cancelByUser() {
        if (isActive()) finish(AssessmentStatus.CANCELLED, null)
    }

    fun reset() {
        if (stateFlow.value is RunnerState.Finished) stateFlow.value = RunnerState.Idle
    }

    private fun isActive() = stateFlow.value is RunnerState.Running || stateFlow.value is RunnerState.Between

    private fun watch() {
        watchJob?.cancel()
        watchJob = scope.launch {
            combine(vehicle.observeState(), appForeground) { v, fg -> gate.stopReason(v, vehicle.capabilityFlags(), fg) }
                .collect { reason -> if (reason != null && isActive()) finish(AssessmentStatus.INTERRUPTED, reason) }
        }
    }

    private fun finish(status: AssessmentStatus, reason: DenialReason?) {
        val s = session ?: return
        engine?.abort()
        watchJob?.cancel()
        watchJob = null
        val done = s.copy(endedAt = clock.now(), status = status, stopReason = reason)
        session = null
        engine = null
        stateFlow.value = RunnerState.Saving(done.assessmentId)
        writes.trySend {
            val saved = runCatching { sink.close(done) }.getOrDefault(SaveState.SAVE_FAILED)
            stateFlow.value = RunnerState.Finished(done, saved)
        }
    }
}
