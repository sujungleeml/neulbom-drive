package com.neulbom.drive.vehicle

import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.vehicle.api.DriverIdentity
import com.neulbom.drive.vehicle.api.UnavailableNaviAdapter
import com.neulbom.drive.vehicle.api.UnavailableSpeechAdapter
import com.neulbom.drive.vehicle.mock.MockDrivingHistory
import com.neulbom.drive.vehicle.mock.MockScenario
import com.neulbom.drive.vehicle.mock.MockTripGenerator
import com.neulbom.drive.vehicle.mock.TripType
import com.neulbom.drive.vehicle.mock.MockVehicleAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** mock 플래버: SDK 없이 실행 가능한 모의 차량. */
object VehicleModule {
    fun create(clock: Clock, scope: CoroutineScope): VehicleBundle {
        val mock = MockVehicleAdapter(clock, initial = MockScenario.PARKED_VERIFIED)
        val simulator = object : VehicleSimulator {
            override val scenarios = MockScenario.entries.map { SimScenario(it.name, it.label) }
            override val current: StateFlow<String> = mock.scenario.map { it.name }.stateIn(scope, SharingStarted.Eagerly, mock.scenario.value.name)
            override val driverVerified: StateFlow<Boolean> =
                mock.driver.map { it is DriverIdentity.Verified }.stateIn(scope, SharingStarted.Eagerly, true)
            override fun select(id: String) = mock.setScenario(MockScenario.valueOf(id))
            override fun setDriverVerified(verified: Boolean) = mock.setDriverVerified(verified)
        }
        val demoTrips = object : DemoTripSource {
            override fun tripEndingNow(now: java.time.Instant, zone: java.time.ZoneId) =
                MockTripGenerator.generate(
                    driveId = "mock-${now.toEpochMilli()}", type = TripType.ROAD, start = now.minusSeconds(900), durationS = 900,
                    zone = zone, seed = now.toEpochMilli(), signals = mock.mockSignals, harshDecelCount = 1,
                ).let { it.session to it.samples }

            override fun history(today: java.time.LocalDate, weeks: Int, zone: java.time.ZoneId) =
                MockDrivingHistory.generate(today, weeks, seed = today.toEpochDay(), zone = zone, signals = mock.mockSignals)
                    .map { it.session to it.samples }
        }
        return VehicleBundle(mock, mock, UnavailableNaviAdapter, UnavailableSpeechAdapter, simulator, "모의 차량", demoTrips)
    }
}
