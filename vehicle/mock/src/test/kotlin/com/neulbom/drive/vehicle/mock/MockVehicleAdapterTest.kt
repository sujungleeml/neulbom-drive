package com.neulbom.drive.vehicle.mock

import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.vehicle.api.DriverIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MockVehicleAdapterTest {
    @Test
    fun scenariosMapToExpectedContexts() {
        val adapter = MockVehicleAdapter(FakeClock())
        val expected = mapOf(
            MockScenario.PARKED_VERIFIED to SafetyState.PARKED_SAFE,
            MockScenario.DRIVING to SafetyState.DRIVING,
            MockScenario.STOPPED_AT_LIGHT to SafetyState.DRIVING,
            MockScenario.SIGNAL_LOST to SafetyState.PARKED_UNVERIFIED,
            MockScenario.POWERING_OFF to SafetyState.POWERING_OFF,
        )
        expected.forEach { (scenario, context) ->
            adapter.setScenario(scenario)
            assertEquals(context, adapter.observeState().value.safety, scenario.name)
            assertEquals(scenario, adapter.scenario.value)
        }
    }

    @Test
    fun driverVerificationCanBeSimulated() {
        val adapter = MockVehicleAdapter(FakeClock())
        assertIs<DriverIdentity.Verified>(adapter.currentDriver())
        adapter.setDriverVerified(false)
        assertEquals(DriverIdentity.Unverified, adapter.currentDriver())
    }
}
