package com.neulbom.drive.domain.driving

import com.neulbom.drive.core.id.SeededUuidGenerator
import com.neulbom.drive.core.model.Attribution
import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.model.SignalSample
import com.neulbom.drive.core.model.SignalSupport
import com.neulbom.drive.core.model.SupportStatus
import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.domain.consent.ConsentType
import com.neulbom.drive.domain.consent.InMemoryConsentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrivingRecorderTest {
    private val clock = FakeClock()
    private val consents = InMemoryConsentRepository(clock)
    private val saved = mutableListOf<Pair<DrivingSession, List<SignalSample>>>()
    private val sink = object : DrivingSink {
        override suspend fun saveSession(session: DrivingSession, samples: List<SignalSample>) { saved += session to samples }
    }
    private val support = listOf(
        SignalSupport(SignalId.VEHICLE_STATE, SupportStatus.SUPPORTED),
        SignalSupport(SignalId.SPEED, SupportStatus.SUPPORTED, sampleRateHz = 10.0),
        SignalSupport(SignalId.LONG_ACCEL, SupportStatus.UNVERIFIED),
    )
    private val recorder = DrivingRecorder(consents, { support }, sink, clock, SeededUuidGenerator(1), SEOUL, { Attribution.VERIFIED })

    private suspend fun drive(seconds: Int) {
        recorder.onSafetyChanged(SafetyState.DRIVING, DataOrigin.MOCK)
        repeat(seconds * 10) {
            clock.advanceMs(100)
            recorder.onSample(SignalId.SPEED, 40.0)
            recorder.onSample(SignalId.LONG_ACCEL, 0.0)
        }
    }

    @Test fun noConsentNoSession() = runTest {
        drive(120)
        recorder.onSafetyChanged(SafetyState.PARKED_SAFE, DataOrigin.MOCK)
        assertTrue(saved.isEmpty())
    }

    @Test fun parkedSafeEndsAndSavesSession() = runTest {
        consents.update(ConsentType.DRIVING_RECORD, true)
        drive(120)
        recorder.onSafetyChanged(SafetyState.PARKED_SAFE, DataOrigin.MOCK)
        val (session, samples) = saved.single()
        assertEquals(setOf(SignalId.VEHICLE_STATE, SignalId.SPEED), session.signals)
        assertEquals(120.0, java.time.Duration.between(session.startedAt, session.endedAt).seconds.toDouble(), 0.5)
        assertTrue(session.terminatedNormally)
        assertEquals(DataOrigin.MOCK, session.origin)
        assertEquals(1200, samples.size)
        assertTrue(samples.first().tMonoMs in 0..200)
    }

    @Test fun unsupportedSignalSamplesDropped() = runTest {
        consents.update(ConsentType.DRIVING_RECORD, true)
        drive(70)
        recorder.onSafetyChanged(SafetyState.POWERING_OFF, DataOrigin.MOCK)
        assertTrue(saved.single().second.none { it.signal == SignalId.LONG_ACCEL })
    }

    @Test fun consentRevokedMidDriveStopsSamplesAndDiscardsSession() = runTest {
        consents.update(ConsentType.DRIVING_RECORD, true)
        drive(60)
        consents.update(ConsentType.DRIVING_RECORD, false)
        repeat(100) { clock.advanceMs(100); recorder.onSample(SignalId.SPEED, 40.0) }
        recorder.onSafetyChanged(SafetyState.PARKED_SAFE, DataOrigin.MOCK)
        assertTrue(saved.isEmpty(), "철회 후 저장 0건")
    }

    @Test fun unterminatedSessionClosedAtLastSampleAsLimited() = runTest {
        consents.update(ConsentType.DRIVING_RECORD, true)
        drive(90)
        clock.advanceMs(600_000) // 앱이 꺼졌다 다시 켜짐: 종료 이벤트 없이 다음 주행 시작
        recorder.onSafetyChanged(SafetyState.DRIVING, DataOrigin.MOCK)
        val (first, _) = saved.single()
        assertFalse(first.terminatedNormally)
        assertEquals(90.0, java.time.Duration.between(first.startedAt, first.endedAt).seconds.toDouble(), 0.5)
        val f = SessionFeatureCalculator.compute(saved.single().first, saved.single().second)
        assertEquals(SessionQuality.LIMITED, f.quality)
    }

    @Test fun noSessionWhenVehicleStateUnverified() = runTest {
        consents.update(ConsentType.DRIVING_RECORD, true)
        val unverified = DrivingRecorder(consents, { SignalId.entries.map { SignalSupport(it, SupportStatus.UNVERIFIED) } }, sink, clock, SeededUuidGenerator(2), SEOUL, { Attribution.VERIFIED })
        unverified.onSafetyChanged(SafetyState.DRIVING, DataOrigin.REAL)
        clock.advanceMs(120_000)
        unverified.onSafetyChanged(SafetyState.PARKED_SAFE, DataOrigin.REAL)
        assertTrue(saved.isEmpty())
    }

    @Test fun importedMockTripPassesSameGates() = runTest {
        val trip = com.neulbom.drive.vehicle.mock.MockTripGenerator.generate(
            "m1", com.neulbom.drive.vehicle.mock.TripType.ROAD, clock.now(), 600, SEOUL, 1,
            setOf(SignalId.VEHICLE_STATE, SignalId.SPEED, SignalId.LONG_ACCEL),
        )
        recorder.importMockTrip(trip.session, trip.samples)
        assertTrue(saved.isEmpty())
        consents.update(ConsentType.DRIVING_RECORD, true)
        recorder.importMockTrip(trip.session, trip.samples)
        val (s, samples) = saved.single()
        assertEquals(setOf(SignalId.VEHICLE_STATE, SignalId.SPEED), s.signals)
        assertTrue(samples.none { it.signal == SignalId.LONG_ACCEL })
    }
}
