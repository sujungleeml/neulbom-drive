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

    /** 신호가 허용 나이보다 오래 갱신되지 않으면 주차 확인 상태라도 상태 불명으로 막는다(Fail-closed). */
    @Test
    fun staleSignalIsTreatedAsUnknown() {
        adapter.setScenario(MockScenario.PARKED_VERIFIED)
        val state = adapter.observeState().value
        val caps = adapter.capabilityFlags()
        val maxAge = java.time.Duration.ofMillis(1500)
        val fresh = state.observedAt.plusMillis(1000)
        val stale = state.observedAt.plusMillis(2000)
        assertEquals(GateDecision.Allowed, gate.evaluateStart(state, caps, true, true, true, fresh, maxAge))
        assertEquals(GateDecision.Denied(DenialReason.STATE_UNKNOWN), gate.evaluateStart(state, caps, true, true, true, stale, maxAge))
        assertEquals(DenialReason.STATE_UNKNOWN, gate.stopReason(state, caps, true, stale, maxAge))
        assertFalse(gate.canShowParkedPrompt(state, caps, true, stale, maxAge))
        // 나이 기준이 없으면(모의 시뮬레이터) 보지 않는다
        assertEquals(GateDecision.Allowed, gate.evaluateStart(state, caps, true, true, true, stale, null))
        assertEquals(GateDecision.Allowed, gate.evaluateStart(state, caps, true, true, true, null, maxAge))
    }

    @Test
    fun pleosAdapterDeclaresSignalAge() {
        assertEquals(java.time.Duration.ofMillis(1500), PleosVehicleAdapter(FakeClock()).maxSignalAge)
        assertNull(adapter.maxSignalAge)
    }
}
