package com.neulbom.drive.vehicle.mock

import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.model.SignalSupport
import com.neulbom.drive.core.model.SupportStatus
import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.vehicle.api.CapabilityFlag
import com.neulbom.drive.vehicle.api.DriverIdentity
import com.neulbom.drive.vehicle.api.DriverIdentityAdapter
import com.neulbom.drive.vehicle.api.MotionState
import com.neulbom.drive.vehicle.api.ParkingState
import com.neulbom.drive.vehicle.api.PowerState
import com.neulbom.drive.vehicle.api.VehicleAdapter
import com.neulbom.drive.vehicle.api.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 개발자 데모(S09) 주차 상태 시뮬레이터 프리셋. */
enum class MockScenario(val label: String) {
    PARKED_VERIFIED("주차 확인됨 (P단)"),
    DRIVING("주행 중"),
    STOPPED_AT_LIGHT("신호 대기 (속도 0, 주차 아님)"),
    SIGNAL_LOST("상태 불명"),
    POWERING_OFF("전원 끄는 중"),
}

/**
 * SDK 없이 실행 가능한 Mock 차량 어댑터. 실제 차량 데이터가 아니다.
 */
class MockVehicleAdapter(
    private val clock: Clock,
    initial: MockScenario = MockScenario.PARKED_VERIFIED,
    private val capabilities: Set<CapabilityFlag> = DEFAULT_CAPABILITIES,
    /** 시뮬레이션하는 주행 신호. 나머지는 UNSUPPORTED로 보여 "미지원 신호" 화면을 시연한다. */
    val mockSignals: Set<SignalId> = DEFAULT_MOCK_SIGNALS,
) : VehicleAdapter, DriverIdentityAdapter {

    private val state = MutableStateFlow(stateFor(initial))
    private val scenarioFlow = MutableStateFlow(initial)
    val scenario: StateFlow<MockScenario> = scenarioFlow.asStateFlow()

    private val driverFlow = MutableStateFlow<DriverIdentity>(DriverIdentity.Verified(DEMO_DRIVER_ID))
    val driver: StateFlow<DriverIdentity> = driverFlow.asStateFlow()

    fun setScenario(scenario: MockScenario) {
        scenarioFlow.value = scenario
        state.value = stateFor(scenario)
    }

    fun setDriverVerified(verified: Boolean) {
        driverFlow.value = if (verified) DriverIdentity.Verified(DEMO_DRIVER_ID) else DriverIdentity.Unverified
    }

    override suspend fun getVehicleMotionState(): MotionState = state.value.motion
    override suspend fun getParkingState(): ParkingState = state.value.parking
    override fun observeState(): StateFlow<VehicleState> = state.asStateFlow()
    override fun capabilityFlags(): Set<CapabilityFlag> = capabilities
    override fun signalSupport(): List<SignalSupport> = SignalId.entries.map { signal ->
        SignalSupport(
            signal = signal,
            status = if (signal in mockSignals) SupportStatus.SUPPORTED else SupportStatus.UNSUPPORTED,
            sdkName = "MOCK",
            sampleRateHz = if (signal in mockSignals && signal != SignalId.VEHICLE_STATE) MockTripGenerator.SAMPLE_HZ else null,
        )
    }
    override fun currentDriver(): DriverIdentity = driverFlow.value

    private fun stateFor(scenario: MockScenario): VehicleState {
        val now = clock.now()
        return when (scenario) {
            MockScenario.PARKED_VERIFIED -> VehicleState(MotionState.STATIONARY, ParkingState.PARKED_VERIFIED, PowerState.ON, now)
            MockScenario.DRIVING -> VehicleState(MotionState.MOVING, ParkingState.NOT_PARKED, PowerState.ON, now)
            MockScenario.STOPPED_AT_LIGHT -> VehicleState(MotionState.STATIONARY, ParkingState.NOT_PARKED, PowerState.ON, now)
            MockScenario.SIGNAL_LOST -> VehicleState(MotionState.UNKNOWN, ParkingState.UNKNOWN, PowerState.ON, now)
            MockScenario.POWERING_OFF -> VehicleState(MotionState.UNKNOWN, ParkingState.UNKNOWN, PowerState.OFF, now)
        }
    }

    companion object {
        const val DEMO_DRIVER_ID = "demo-driver-0001"

        val DEFAULT_MOCK_SIGNALS = setOf(SignalId.VEHICLE_STATE, SignalId.SPEED, SignalId.LONG_ACCEL)

        val DEFAULT_CAPABILITIES = setOf(
            CapabilityFlag.MOTION_STATE,
            CapabilityFlag.PARKING_STATE,
            CapabilityFlag.STATE_STREAM,
            CapabilityFlag.POWER_STATE,
            CapabilityFlag.DRIVER_IDENTITY,
        )
    }
}
