package com.neulbom.drive.vehicle.api

import com.neulbom.drive.core.model.SignalSupport
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration

/**
 * 차량 플랫폼 추상화(PRD 6절). 메서드 이름은 앱 내부 계약이며, 실제 Pleos SDK 메서드와의 매핑은
 * 공식 문서 확인 후 `PleosVehicleAdapter`에서 수행한다.
 */
interface VehicleAdapter {
    suspend fun getVehicleMotionState(): MotionState

    suspend fun getParkingState(): ParkingState

    /** 최신 차량 상태 스트림. 연결이 끊기거나 불확실하면 UNKNOWN 상태를 내보내야 한다. */
    fun observeState(): StateFlow<VehicleState>

    fun capabilityFlags(): Set<CapabilityFlag>

    /** 주행 신호별 지원 근거(명세 v0.6 08 §1). 근거 없는 신호는 UNVERIFIED. */
    fun signalSupport(): List<SignalSupport>

    /**
     * 신호가 이보다 오래 갱신되지 않으면 상태를 믿지 않는다(점검 시작 막음, 진행 중이면 중단).
     * 어댑터는 신호를 받을 때마다 [VehicleState.observedAt]을 갱신해야 한다.
     * null이면 나이를 보지 않는다(모의 시뮬레이터처럼 앱이 상태를 직접 정하는 경우).
     */
    val maxSignalAge: Duration? get() = null
}
