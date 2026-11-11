package com.neulbom.drive.vehicle.api

import com.neulbom.drive.core.model.SafetyState
import java.time.Instant

/** 차량 움직임 상태. "속도 0"은 STATIONARY일 뿐 주차를 의미하지 않는다. */
enum class MotionState { STATIONARY, MOVING, UNKNOWN }

/**
 * 주차 상태. PARKED_VERIFIED는 플랫폼이 P단/안전 주차를 **검증**했을 때만 사용한다.
 * NOT_PARKED: 도로 위 정차(신호 대기 등)를 포함한다.
 */
enum class ParkingState { PARKED_VERIFIED, NOT_PARKED, UNKNOWN }

enum class PowerState { ON, OFF, UNKNOWN }

data class VehicleState(
    val motion: MotionState,
    val parking: ParkingState,
    val power: PowerState,
    val observedAt: Instant,
) {
    /**
     * 앱이 사용하는 안전 상태. 보수적으로 판정한다:
     * - 전원 OFF → POWERING_OFF (세션 안전 종료)
     * - 움직이는 중 또는 주차 아님(신호 대기 포함) → DRIVING
     * - 검증된 주차 + 정지 + 전원 ON → PARKED_SAFE
     * - 나머지(신호 불명, 전원 불명 등) → PARKED_UNVERIFIED
     */
    val safety: SafetyState
        get() = when {
            power == PowerState.OFF -> SafetyState.POWERING_OFF
            motion == MotionState.MOVING -> SafetyState.DRIVING
            parking == ParkingState.NOT_PARKED -> SafetyState.DRIVING
            parking == ParkingState.PARKED_VERIFIED && motion == MotionState.STATIONARY && power == PowerState.ON -> SafetyState.PARKED_SAFE
            else -> SafetyState.PARKED_UNVERIFIED
        }

    companion object {
        fun unknown(at: Instant) = VehicleState(MotionState.UNKNOWN, ParkingState.UNKNOWN, PowerState.UNKNOWN, at)
    }
}
