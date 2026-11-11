package com.neulbom.drive.domain.cognitive

import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.vehicle.api.CapabilityFlag
import com.neulbom.drive.vehicle.mock.MockScenario
import com.neulbom.drive.vehicle.mock.MockVehicleAdapter
import com.neulbom.drive.vehicle.pleos.PleosVehicleAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParkingSafetyGateTest {
    private val gate = ParkingSafetyGate()
    private val adapter = MockVehicleAdapter(FakeClock())

    private fun start(
        scenario: MockScenario,
        userInitiated: Boolean = true,
        foreground: Boolean = true,
        consent: Boolean = true,
        caps: Set<CapabilityFlag> = adapter.capabilityFlags(),
    ): GateDecision {
        adapter.setScenario(scenario)
        return gate.evaluateStart(adapter.observeState().value, caps, userInitiated, foreground, consent)
    }

    @Test
    fun T01_onlyParkedSafeAllowsStart() {
        assertEquals(GateDecision.Allowed, start(MockScenario.PARKED_VERIFIED))
        assertEquals(GateDecision.Denied(DenialReason.DRIVING), start(MockScenario.DRIVING))
        assertEquals(GateDecision.Denied(DenialReason.STATE_UNKNOWN), start(MockScenario.SIGNAL_LOST))
        assertEquals(GateDecision.Denied(DenialReason.POWERING_OFF), start(MockScenario.POWERING_OFF))
    }

    @Test
    fun T01_speedZeroWithoutVerifiedParkingIsDenied() {
        assertFalse(start(MockScenario.STOPPED_AT_LIGHT).isAllowed)
    }

    @Test
    fun requiresExplicitUserStartForegroundAndConsent() {
        assertEquals(GateDecision.Denied(DenialReason.NOT_USER_INITIATED), start(MockScenario.PARKED_VERIFIED, userInitiated = false))
        assertEquals(GateDecision.Denied(DenialReason.APP_IN_BACKGROUND), start(MockScenario.PARKED_VERIFIED, foreground = false))
        assertEquals(GateDecision.Denied(DenialReason.CONSENT_MISSING), start(MockScenario.PARKED_VERIFIED, consent = false))
    }

    @Test
    fun missingParkingCapabilityAlwaysDenies() {
        assertEquals(
            GateDecision.Denied(DenialReason.CAPABILITY_MISSING),
            start(MockScenario.PARKED_VERIFIED, caps = setOf(CapabilityFlag.MOTION_STATE)),
        )
    }

    @Test
    fun unverifiedPleosAdapterNeverAllowsStart() {
        val pleos = PleosVehicleAdapter(FakeClock())
        val decision = gate.evaluateStart(pleos.observeState().value, pleos.capabilityFlags(), true, true, true)
        assertFalse(decision.isAllowed)
    }

    @Test
    fun stopReasonTracksContinuingConditions() {
        adapter.setScenario(MockScenario.PARKED_VERIFIED)
        assertNull(gate.stopReason(adapter.observeState().value, adapter.capabilityFlags(), true))
        adapter.setScenario(MockScenario.DRIVING)
        assertEquals(DenialReason.DRIVING, gate.stopReason(adapter.observeState().value, adapter.capabilityFlags(), true))
        adapter.setScenario(MockScenario.PARKED_VERIFIED)
        assertEquals(DenialReason.APP_IN_BACKGROUND, gate.stopReason(adapter.observeState().value, adapter.capabilityFlags(), false))
    }

    @Test
    fun parkedPromptOnlyWhenParked() {
        adapter.setScenario(MockScenario.PARKED_VERIFIED)
        assertTrue(gate.canShowParkedPrompt(adapter.observeState().value, adapter.capabilityFlags(), true))
        adapter.setScenario(MockScenario.STOPPED_AT_LIGHT)
        assertFalse(gate.canShowParkedPrompt(adapter.observeState().value, adapter.capabilityFlags(), true))
    }
}
