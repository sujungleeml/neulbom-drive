package com.neulbom.drive.domain.scheduler

import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.domain.cognitive.ProtocolStatus
import java.time.Duration
import java.time.Instant

/** 점검 제안 상태(명세 v0.6 03 §4). */
enum class ScheduleState { ONBOARDING, BASELINE_COLLECTING, BASELINE_READY, ROUTINE_DUE, DEFERRED, SUSPENDED, IN_PROGRESS, INTERRUPTED }

/** 제안 간격 설정. **모두 MVP 임시값(PROVISIONAL)**이며 사용성 검증 후 확정한다. */
data class SchedulerConfig(
    val minOfferIntervalHours: Long = 24,
    val maxOffersPerCycle: Int = 3,
    val baselineValidAssessments: Int = 4,
    val routineIntervalDays: Long = 28,
    val status: ProtocolStatus = ProtocolStatus.PROVISIONAL,
)

/**
 * 스케줄러 입력. **점검 결과(반응시간·정확도 등)는 받지 않는다** — 결과에 따라 제안 빈도를 바꾸지 않기 위해서다(03 §4 규칙 7).
 * @property offersInCurrentCycle 현재 주기(마지막 유효 점검 뒤, 또는 제안이 다 찬 주기의 마지막 제안에서 정기 간격이 지난 뒤)에 제안한 횟수
 */
data class ScheduleInput(
    val now: Instant,
    val safety: SafetyState,
    val consentGranted: Boolean,
    val onboardingDone: Boolean,
    val suspendedUntil: Instant?,
    val validAssessmentCount: Int,
    val lastValidAssessmentAt: Instant?,
    val offersInCurrentCycle: Int,
    val lastOfferAt: Instant?,
    val lastOfferDeclined: Boolean,
)

sealed interface OfferDecision {
    data class Offer(val isFirstAssessment: Boolean, val includeCheckIn: Boolean) : OfferDecision
    data class NoOffer(val state: ScheduleState, val reason: String) : OfferDecision
}

class Scheduler(private val config: SchedulerConfig = SchedulerConfig()) {

    fun state(input: ScheduleInput): ScheduleState = when {
        !input.onboardingDone -> ScheduleState.ONBOARDING
        input.suspendedUntil?.isAfter(input.now) == true -> ScheduleState.SUSPENDED
        input.validAssessmentCount < config.baselineValidAssessments -> ScheduleState.BASELINE_COLLECTING
        input.lastValidAssessmentAt == null ||
            !input.now.isBefore(input.lastValidAssessmentAt.plus(Duration.ofDays(config.routineIntervalDays))) -> ScheduleState.ROUTINE_DUE
        else -> ScheduleState.BASELINE_READY
    }

    fun decide(input: ScheduleInput): OfferDecision {
        val state = state(input)
        if (input.safety != SafetyState.PARKED_SAFE) return OfferDecision.NoOffer(state, "NOT_PARKED_SAFE")
        if (!input.consentGranted) return OfferDecision.NoOffer(state, "NO_CONSENT")
        if (state != ScheduleState.BASELINE_COLLECTING && state != ScheduleState.ROUTINE_DUE) return OfferDecision.NoOffer(state, "NOT_DUE")
        if (input.offersInCurrentCycle >= config.maxOffersPerCycle) return OfferDecision.NoOffer(ScheduleState.DEFERRED, "CYCLE_LIMIT")
        val last = input.lastOfferAt
        if (last != null && input.now.isBefore(last.plus(Duration.ofHours(config.minOfferIntervalHours)))) {
            return OfferDecision.NoOffer(ScheduleState.DEFERRED, "COOLDOWN")
        }
        return OfferDecision.Offer(
            isFirstAssessment = input.validAssessmentCount == 0,
            includeCheckIn = state == ScheduleState.ROUTINE_DUE,
        )
    }
}
