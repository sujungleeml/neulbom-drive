package com.neulbom.drive.domain.cognitive

import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.vehicle.api.CapabilityFlag
import com.neulbom.drive.vehicle.api.VehicleState
import java.time.Duration
import java.time.Instant

enum class DenialReason(val message: String) {
    DRIVING("운전 중에는 점검을 진행할 수 없어요."),
    POWERING_OFF("차량 전원이 꺼지고 있어 점검을 멈췄습니다."),
    STATE_UNKNOWN("차량이 안전하게 주차되었는지 확인할 수 없습니다."),
    CAPABILITY_MISSING("이 차량에서는 주차 상태를 확인할 수 없어 점검을 제공하지 않습니다."),
    NOT_USER_INITIATED("점검은 직접 시작하실 때만 진행됩니다."),
    APP_IN_BACKGROUND("앱 화면이 보이지 않아 점검을 멈췄습니다."),
    CONSENT_MISSING("주의·반응 점검에 동의하지 않으셨습니다. 늘봄 웹의 설정에서 바꿀 수 있습니다."),
}

sealed interface GateDecision {
    data object Allowed : GateDecision
    data class Denied(val reason: DenialReason) : GateDecision

    val isAllowed: Boolean get() = this is Allowed
}

/**
 * 주차 안전 게이트(PRD 6절).
 *  - 진입 조건: PARKED_SAFE + 사용자 명시적 시작 + 앱 전경 + 인지 점검 동의 + 주차 확인 capability.
 *  - 속도 0만으로는 절대 허용하지 않는다(VehicleState.safety가 검증된 주차만 PARKED_SAFE로 판정).
 *  - 지속 조건: 위 조건 중 하나라도 깨지면 즉시 중지 사유를 반환.
 *  - 신호 나이: [now]와 [maxSignalAge]가 주어지면, 상태가 그보다 오래 갱신되지 않았을 때 상태 불명으로 본다(Fail-closed).
 */
class ParkingSafetyGate {

    fun evaluateStart(
        state: VehicleState,
        capabilities: Set<CapabilityFlag>,
        userInitiated: Boolean,
        appInForeground: Boolean,
        cognitiveConsent: Boolean,
        now: Instant? = null,
        maxSignalAge: Duration? = null,
    ): GateDecision {
        environmentDenial(state, capabilities, appInForeground, now, maxSignalAge)?.let { return GateDecision.Denied(it) }
        if (!cognitiveConsent) return GateDecision.Denied(DenialReason.CONSENT_MISSING)
        if (!userInitiated) return GateDecision.Denied(DenialReason.NOT_USER_INITIATED)
        return GateDecision.Allowed
    }

    /** 과제 진행 중 매 상태 변화마다 호출. null이면 계속 진행 가능. */
    fun stopReason(
        state: VehicleState,
        capabilities: Set<CapabilityFlag>,
        appInForeground: Boolean,
        now: Instant? = null,
        maxSignalAge: Duration? = null,
    ): DenialReason? = environmentDenial(state, capabilities, appInForeground, now, maxSignalAge)

    /** 주차 후 권유 카드(S04)를 보여도 되는 환경인지. 사용자 시작 여부와 무관. */
    fun canShowParkedPrompt(
        state: VehicleState,
        capabilities: Set<CapabilityFlag>,
        appInForeground: Boolean,
        now: Instant? = null,
        maxSignalAge: Duration? = null,
    ): Boolean = environmentDenial(state, capabilities, appInForeground, now, maxSignalAge) == null

    /** 신호가 허용 나이보다 오래 갱신되지 않았는지. */
    fun isStale(state: VehicleState, now: Instant?, maxSignalAge: Duration?): Boolean =
        now != null && maxSignalAge != null && Duration.between(state.observedAt, now) > maxSignalAge

    private fun environmentDenial(
        state: VehicleState,
        capabilities: Set<CapabilityFlag>,
        appInForeground: Boolean,
        now: Instant?,
        maxSignalAge: Duration?,
    ): DenialReason? = when {
        CapabilityFlag.PARKING_STATE !in capabilities -> DenialReason.CAPABILITY_MISSING
        isStale(state, now, maxSignalAge) -> DenialReason.STATE_UNKNOWN
        state.safety == SafetyState.DRIVING -> DenialReason.DRIVING
        state.safety == SafetyState.POWERING_OFF -> DenialReason.POWERING_OFF
        state.safety != SafetyState.PARKED_SAFE -> DenialReason.STATE_UNKNOWN
        !appInForeground -> DenialReason.APP_IN_BACKGROUND
        else -> null
    }
}
