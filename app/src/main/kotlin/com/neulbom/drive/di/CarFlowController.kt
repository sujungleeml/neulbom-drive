package com.neulbom.drive.di

import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.data.local.SchedulingEventEntity
import com.neulbom.drive.domain.cognitive.DenialReason
import com.neulbom.drive.domain.cognitive.StartResult
import com.neulbom.drive.domain.cognitive.assessment.RunnerState
import com.neulbom.drive.domain.consent.ConsentType
import com.neulbom.drive.domain.scheduler.OfferDecision
import com.neulbom.drive.domain.scheduler.OfferRecord
import com.neulbom.drive.domain.scheduler.OfferResponse
import com.neulbom.drive.domain.scheduler.ScheduleHistory
import com.neulbom.drive.domain.scheduler.ScheduleInput
import com.neulbom.drive.domain.scheduler.ScheduleState
import com.neulbom.drive.domain.scheduler.Scheduler
import com.neulbom.drive.presentation.CarFlowState
import com.neulbom.drive.presentation.ProfileAnswer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * 차량 앱 흐름(CAR-02~10)의 상태. 화면 선택은 [com.neulbom.drive.presentation.CarNavigator]가 한다.
 * 점검 제안은 **주차가 확인된 순간에만** 스케줄러에 묻고, 결과가 아니라 시각·응답 기록만 쓴다.
 */
class CarFlowController(private val c: AppContainer) {
    private val scheduler = Scheduler()
    private val flow = MutableStateFlow(CarFlowState())
    val state: StateFlow<CarFlowState> = flow.asStateFlow()

    private val denialFlow = MutableStateFlow<DenialReason?>(null)
    /** 직접 시작을 눌렀지만 게이트가 막은 이유(대기 화면에 표시). */
    val lastDenial: StateFlow<DenialReason?> = denialFlow.asStateFlow()

    private val scheduleFlow = MutableStateFlow(ScheduleState.ONBOARDING)
    val scheduleState: StateFlow<ScheduleState> = scheduleFlow.asStateFlow()

    private val savedFlow = MutableStateFlow<Set<String>>(emptySet())
    /** 서버가 받았다고 답한 점검 id(완료 화면이 SERVER_SAVED 문구로 바꾼다). */
    val serverSaved: StateFlow<Set<String>> = savedFlow.asStateFlow()

    fun markServerSaved(ids: Collection<String>) {
        if (ids.isNotEmpty()) savedFlow.update { it + ids }
    }

    private var currentOfferId: String? = null

    fun start() {
        c.appScope.launch {
            c.vehicle.adapter.observeState().map { it.safety }.distinctUntilChanged().collect { safety ->
                when (safety) {
                    SafetyState.PARKED_SAFE -> evaluateOffer()
                    SafetyState.POWERING_OFF -> {
                        // 시동을 끄면 운전자 확인과 남은 제안을 지운다(다음 시동에서 다시 묻는다).
                        currentOfferId = null
                        flow.update { CarFlowState() }
                    }
                    else -> {
                        // 주차가 풀리면 띄워 둔 제안은 응답 없이 내린다.
                        currentOfferId = null
                        flow.update { it.copy(offerPending = false, guideOpen = false) }
                    }
                }
            }
        }
        c.appScope.launch { refreshSchedule() }
    }

    fun answerProfile(answer: ProfileAnswer) {
        flow.update { it.copy(profile = answer) }
        c.appScope.launch { evaluateOffer() }
    }

    fun respondToOffer(response: OfferResponse) {
        val id = currentOfferId
        currentOfferId = null
        flow.update {
            if (response == OfferResponse.ACCEPTED) it.copy(offerPending = false, guideOpen = true)
            else it.copy(offerPending = false, includeCheckIn = false)
        }
        if (id != null) c.appScope.launch { c.db.schedulingDao().respond(id, response.name, c.clock.now().toEpochMilli()) }
    }

    /** 대기 화면의 "지금 점검하기"(사용자가 직접 시작). */
    fun openGuide() {
        denialFlow.value = null
        flow.update { it.copy(guideOpen = true, includeCheckIn = it.includeCheckIn || scheduleFlow.value == ScheduleState.ROUTINE_DUE) }
    }

    fun closeGuide() = flow.update { it.copy(guideOpen = false, includeCheckIn = false) }

    fun startCheck() {
        c.appScope.launch {
            val isFirst = c.db.assessmentDao().countCompleted() == 0
            c.taskRunner.reset()
            when (val r = c.taskRunner.start(userInitiated = true, isFirstAssessment = isFirst)) {
                is StartResult.Denied -> {
                    denialFlow.value = r.reason
                    flow.update { it.copy(guideOpen = false) }
                }
                is StartResult.Started -> {
                    denialFlow.value = null
                    flow.update { it.copy(guideOpen = false, checkInDone = false) }
                }
            }
        }
    }

    fun finishCheckIn() = flow.update { it.copy(checkInDone = true) }

    /** 완료·중단 화면의 "닫기". */
    fun closeResult() {
        c.taskRunner.reset()
        flow.update { it.copy(includeCheckIn = false, checkInDone = false, guideOpen = false) }
        c.appScope.launch { refreshSchedule() }
    }

    /** 시연 도구 "시연 처음부터": 흐름 상태를 처음으로. */
    fun resetForDemo() {
        currentOfferId = null
        denialFlow.value = null
        savedFlow.value = emptySet()
        flow.value = CarFlowState()
    }

    /** 시연 도구: 제안 기록을 지우고 지금 다시 판단한다(mock 전용 버튼). */
    fun resetOffersForDemo() {
        c.appScope.launch {
            c.db.schedulingDao().deleteAll()
            evaluateOffer()
        }
    }

    private suspend fun input(): ScheduleInput {
        val now = c.clock.now()
        return ScheduleHistory.input(
            now = now,
            safety = c.vehicle.adapter.observeState().value.safety,
            consentGranted = c.consents.state.value.isGranted(ConsentType.COGNITIVE_CHECK),
            onboardingDone = c.settings.settings.value.onboardingDone,
            completedAt = c.db.assessmentDao().completedStartTimes().map(Instant::ofEpochMilli),
            offers = c.db.schedulingDao().all().map { e ->
                OfferRecord(Instant.ofEpochMilli(e.offeredAt), e.response?.let { runCatching { OfferResponse.valueOf(it) }.getOrNull() })
            },
            zone = c.zone,
            pausedUntil = c.settings.settings.value.offersPausedUntilMs?.let(Instant::ofEpochMilli),
        )
    }

    private suspend fun refreshSchedule() {
        scheduleFlow.value = scheduler.state(input())
    }

    private suspend fun evaluateOffer() {
        val f = flow.value
        refreshSchedule()
        if (f.profile != ProfileAnswer.CONFIRMED || f.offerPending || f.guideOpen) return
        if (c.taskRunner.state.value != RunnerState.Idle) return
        val decision = scheduler.decide(input())
        if (decision !is OfferDecision.Offer) return
        val id = c.ids.newId()
        c.db.schedulingDao().insert(
            SchedulingEventEntity(id, c.clock.now().toEpochMilli(), null, null, simulated = c.vehicle.simulator != null),
        )
        currentOfferId = id
        flow.update { it.copy(offerPending = true, offerIsFirst = decision.isFirstAssessment, includeCheckIn = decision.includeCheckIn) }
    }
}
