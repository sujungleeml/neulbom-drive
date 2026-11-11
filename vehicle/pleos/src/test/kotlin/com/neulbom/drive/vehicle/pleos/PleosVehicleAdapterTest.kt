package com.neulbom.drive.vehicle.pleos

import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.vehicle.api.DriverIdentity
import com.neulbom.drive.vehicle.api.ParkingState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PleosVehicleAdapterTest {
    @Test
    fun unverifiedSdkDefaultsToSafeUnknownState() = runTest {
        val adapter = PleosVehicleAdapter(FakeClock())
        assertEquals(ParkingState.UNKNOWN, adapter.getParkingState())
        assertEquals(SafetyState.PARKED_UNVERIFIED, adapter.observeState().value.safety)
        assertTrue(adapter.capabilityFlags().isEmpty())
        assertEquals(DriverIdentity.Unverified, adapter.currentDriver())
    }
}

class PleosSignalSupportTest {
    @kotlin.test.Test
    fun pleosReportsAllSignalsUnverified() {
        val support = PleosVehicleAdapter(com.neulbom.drive.core.time.FakeClock()).signalSupport()
        kotlin.test.assertEquals(com.neulbom.drive.core.model.SignalId.entries.size, support.size)
        kotlin.test.assertTrue(support.all { it.status == com.neulbom.drive.core.model.SupportStatus.UNVERIFIED })
    }
}
