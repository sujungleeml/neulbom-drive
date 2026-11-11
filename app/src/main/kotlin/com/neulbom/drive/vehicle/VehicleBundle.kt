package com.neulbom.drive.vehicle

import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.core.model.SignalSample
import com.neulbom.drive.vehicle.api.DriverIdentityAdapter
import com.neulbom.drive.vehicle.api.NaviAdapter
import com.neulbom.drive.vehicle.api.SpeechAdapter
import com.neulbom.drive.vehicle.api.VehicleAdapter
import kotlinx.coroutines.flow.StateFlow

/** 플래버(mock/pleos)마다 다른 차량 연동 구성. */
class VehicleBundle(
    val adapter: VehicleAdapter,
    val driverIdentity: DriverIdentityAdapter,
    val navi: NaviAdapter,
    val speech: SpeechAdapter,
    /** mock 플래버에서만 존재하는 주차 상태 시뮬레이터(S09). */
    val simulator: VehicleSimulator?,
    val sourceLabel: String,
    /** mock 플래버에서만: 시연용 모의 주행 기록 생성. */
    val demoTrips: DemoTripSource? = null,
)

/** 시연용 모의 주행(모두 DataOrigin.MOCK). */
interface DemoTripSource {
    /** 방금 끝난 주행 한 건(약 15분). */
    fun tripEndingNow(now: java.time.Instant, zone: java.time.ZoneId): Pair<DrivingSession, List<SignalSample>>

    /** 오늘까지 weeks주 동안의 주행 기록. */
    fun history(today: java.time.LocalDate, weeks: Int, zone: java.time.ZoneId): List<Pair<DrivingSession, List<SignalSample>>>
}

data class SimScenario(val id: String, val label: String)

interface VehicleSimulator {
    val scenarios: List<SimScenario>
    val current: StateFlow<String>
    val driverVerified: StateFlow<Boolean>
    fun select(id: String)
    fun setDriverVerified(verified: Boolean)
}
