package com.neulbom.drive.presentation

import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.domain.cognitive.assessment.AssessmentStatus
import com.neulbom.drive.domain.cognitive.assessment.RunnerState

/** 차량 앱 화면(명세 v0.5 CAR-01~10). */
enum class CarScreen { ONBOARDING, PROFILE, IDLE, OFFER, GUIDE, TASK, BETWEEN, SAVING, CHECK_IN, COMPLETION, INTERRUPTED, DRIVING }

/** CAR-02 운전자 확인 답. 이번 시동 동안만 기억한다. */
enum class ProfileAnswer { CONFIRMED, NOT_ME }

/**
 * 화면 흐름 상태(점검 실행 상태는 AssessmentRunner가 따로 가진다).
 * @property offerPending 주차 확인 직후 스케줄러가 제안을 허락해 제안 화면을 띄울 차례
 * @property includeCheckIn 이번 점검이 4주 정기 점검이라 끝난 뒤 짧은 문항(CAR-08)을 둔다
 */
data class CarFlowState(
    val profile: ProfileAnswer? = null,
    val offerPending: Boolean = false,
    val offerIsFirst: Boolean = false,
    val includeCheckIn: Boolean = false,
    val guideOpen: Boolean = false,
    val checkInDone: Boolean = false,
)

object CarNavigator {
    /**
     * 우선순위: 주행 중 → 온보딩 → 운전자 확인 → 점검 진행 상태 → 안내 → 제안 → 대기.
     * 주행 중이면 어떤 상태든 조작 요소 없는 주행 화면만 보인다.
     */
    fun screen(safety: SafetyState, onboardingDone: Boolean, flow: CarFlowState, runner: RunnerState): CarScreen = when {
        safety == SafetyState.DRIVING -> CarScreen.DRIVING
        !onboardingDone -> CarScreen.ONBOARDING
        flow.profile == null -> CarScreen.PROFILE
        runner is RunnerState.Running -> CarScreen.TASK
        runner is RunnerState.Between -> CarScreen.BETWEEN
        runner is RunnerState.Saving -> CarScreen.SAVING
        runner is RunnerState.Finished -> when (runner.session.status) {
            AssessmentStatus.INTERRUPTED, AssessmentStatus.CANCELLED -> CarScreen.INTERRUPTED
            else -> if (flow.includeCheckIn && !flow.checkInDone) CarScreen.CHECK_IN else CarScreen.COMPLETION
        }
        flow.guideOpen -> CarScreen.GUIDE
        flow.offerPending && safety == SafetyState.PARKED_SAFE -> CarScreen.OFFER
        else -> CarScreen.IDLE
    }
}
