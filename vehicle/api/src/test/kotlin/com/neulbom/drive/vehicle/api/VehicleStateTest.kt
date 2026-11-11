package com.neulbom.drive.vehicle.api

import com.neulbom.drive.core.model.SafetyState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class VehicleStateTest {
    private fun state(m: MotionState, p: ParkingState, pw: PowerState) = VehicleState(m, p, pw, Instant.EPOCH)

    @Test
    fun mapsToFourSafetyStates() {
        assertEquals(SafetyState.PARKED_SAFE, state(MotionState.STATIONARY, ParkingState.PARKED_VERIFIED, PowerState.ON).safety)
        assertEquals(SafetyState.DRIVING, state(MotionState.MOVING, ParkingState.PARKED_VERIFIED, PowerState.ON).safety)
        assertEquals(SafetyState.DRIVING, state(MotionState.STATIONARY, ParkingState.NOT_PARKED, PowerState.ON).safety) // 신호 대기
        assertEquals(SafetyState.PARKED_UNVERIFIED, state(MotionState.STATIONARY, ParkingState.UNKNOWN, PowerState.ON).safety)
        assertEquals(SafetyState.PARKED_UNVERIFIED, state(MotionState.UNKNOWN, ParkingState.PARKED_VERIFIED, PowerState.UNKNOWN).safety)
        assertEquals(SafetyState.POWERING_OFF, state(MotionState.STATIONARY, ParkingState.PARKED_VERIFIED, PowerState.OFF).safety)
    }

    @Test
    fun speedZeroAloneIsNeverParkedSafe() {
        assertEquals(SafetyState.PARKED_UNVERIFIED, state(MotionState.STATIONARY, ParkingState.UNKNOWN, PowerState.ON).safety)
        assertEquals(SafetyState.PARKED_UNVERIFIED, state(MotionState.UNKNOWN, ParkingState.UNKNOWN, PowerState.ON).safety)
    }
}
