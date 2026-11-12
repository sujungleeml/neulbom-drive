package com.neulbom.drive.vehicle.pleos

import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.model.SignalSupport
import com.neulbom.drive.core.model.SupportStatus
import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.vehicle.api.CapabilityFlag
import com.neulbom.drive.vehicle.api.DriverIdentity
import com.neulbom.drive.vehicle.api.DriverIdentityAdapter
import com.neulbom.drive.vehicle.api.MotionState
import com.neulbom.drive.vehicle.api.ParkingState
import com.neulbom.drive.vehicle.api.VehicleAdapter
import com.neulbom.drive.vehicle.api.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration

/**
 * Pleos 차량 플랫폼 어댑터 — **공식 SDK 문서 검증 전 자리표시자**.
 *
 * 확인되지 않은 Pleos API는 임의로 호출하지 않는다(PRD 6절, 마스터 지시문 2).
 * 그때까지 이 어댑터는 안전 기본값을 반환한다:
 *  - 모든 상태 UNKNOWN → ParkingSafetyGate가 인지 과제 진입을 항상 거부
 *  - capabilityFlags() 비어 있음 → 앱이 차량 연동 기능을 "사용 불가"로 표시
 *  - 운전자 미식별 → 개인 기준선 업데이트 없음
 *
 * TODO(PLEOS-1): 차량 속도/기어(P단)/주차 브레이크 신호를 제공하는 공식 API와 필요한 권한을 확인하고
 *                getVehicleMotionState()/getParkingState()에 매핑. "속도 0"만으로 PARKED_VERIFIED를 만들지 말 것.
 * TODO(PLEOS-2): 상태 변경 콜백(구독) API를 확인해 observeState()에 연결. 연결 끊김·오류 시 UNKNOWN 방출.
 * TODO(PLEOS-3): 차량 전원(IGN/ACC) 상태 신호 확인 → PowerState 매핑, 전원 OFF 시 세션 안전 종료.
 * TODO(PLEOS-4): 운전자 프로필 식별 API 존재 여부 확인 → DriverIdentityAdapter. 실명/계정 ID 대신 기기 내 가명 ID로 변환.
 * TODO(PLEOS-5): 플랫폼 UI·운전자 주의 분산 규정(주행 중 화면 제한)과 앱 심사 정책 확인.
 * TODO(PLEOS-6): Navi/Location/STT/TTS 접근 가능 여부·권한 확인 후 optional 어댑터 구현.
 */
class PleosVehicleAdapter(clock: Clock) : VehicleAdapter, DriverIdentityAdapter {

    private val state = MutableStateFlow(VehicleState.unknown(clock.now()))

    override suspend fun getVehicleMotionState(): MotionState = MotionState.UNKNOWN // TODO(PLEOS-1)

    override suspend fun getParkingState(): ParkingState = ParkingState.UNKNOWN // TODO(PLEOS-1)

    override fun observeState(): StateFlow<VehicleState> = state.asStateFlow() // TODO(PLEOS-2)

    override fun capabilityFlags(): Set<CapabilityFlag> = emptySet()

    /**
     * 실차 신호는 1.5초 넘게 갱신되지 않으면 믿지 않는다(Fail-closed).
     * TODO(PLEOS-2): 실제 구현은 SDK 콜백을 받을 때마다 observedAt을 지금 시각으로 갱신해야 한다.
     */
    override val maxSignalAge: Duration = Duration.ofMillis(1500)

    // TODO(PLEOS-7): 신호별 SDK 이름·문서·권한·샘플링을 확인하면 docs/neulbom-drive/v0.6/signal-support.md와 함께 갱신.
    override fun signalSupport(): List<SignalSupport> = SignalId.entries.map { SignalSupport(it, SupportStatus.UNVERIFIED) }

    override fun currentDriver(): DriverIdentity = DriverIdentity.Unverified // TODO(PLEOS-4)
}
