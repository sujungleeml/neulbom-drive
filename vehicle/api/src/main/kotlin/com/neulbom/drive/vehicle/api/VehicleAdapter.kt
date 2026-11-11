package com.neulbom.drive.vehicle.api

import com.neulbom.drive.core.model.SignalSupport
import kotlinx.coroutines.flow.StateFlow

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
}
