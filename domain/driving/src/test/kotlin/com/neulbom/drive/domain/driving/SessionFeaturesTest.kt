package com.neulbom.drive.domain.driving

import com.neulbom.drive.core.model.Attribution
import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.model.SignalSample
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 08 문서 정의 검증. 표본은 10 Hz(100 ms)로 만든다. */
class SessionFeaturesTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val noon = Instant.parse("2026-11-03T03:00:00Z") // 12:00 KST

    private fun session(
        durationS: Long,
        start: Instant = noon,
        signals: Set<SignalId> = setOf(SignalId.VEHICLE_STATE, SignalId.SPEED, SignalId.LONG_ACCEL),
        speedHz: Double = 10.0,
        attribution: Attribution = Attribution.VERIFIED,
    ) = DrivingSession(
        driveId = "d1", startedAt = start, endedAt = start.plusSeconds(durationS), zone = zone,
        origin = DataOrigin.MOCK, signals = signals,
        sampleRateHz = signals.associateWith { if (it == SignalId.SPEED) speedHz else 10.0 },
        attribution = attribution, terminatedNormally = true,
    )

    /** 일정 속도 표본. stepMs 간격, skip 구간(초)은 비워 둔다. */
    private fun speed(kmh: Double, durationS: Long, stepMs: Long = 100, skip: LongRange? = null) =
        (0..durationS * 1000 step stepMs).filter { t -> skip == null || (t / 1000) !in skip }
            .map { SignalSample(SignalId.SPEED, it, kmh) }

    private fun accel(durationS: Long, events: List<Pair<Long, Long>> = emptyList(), value: Double = -4.0) =
        (0..durationS * 1000 step 100).map { t ->
            val inEvent = events.any { (from, len) -> t in from until from + len }
            SignalSample(SignalId.LONG_ACCEL, t, if (inEvent) value else 0.0)
        }

    @Test fun distanceIsTrapezoidIntegralOfSpeed() {
        val f = SessionFeatureCalculator.compute(session(600), speed(60.0, 600) + accel(600))
        assertEquals(10.0, f.distanceKm!!, 0.01)
        assertTrue(f.valid)
    }

    @Test fun gapsOverFiveSecondsExcludedFromDistance() {
        val f = SessionFeatureCalculator.compute(session(600), speed(60.0, 600, skip = 100L..129L) + accel(600))
        assertEquals(10.0 - 0.5, f.distanceKm!!, 0.02)
        assertEquals(30.1, f.maxGapS, 0.05)
        assertTrue(f.missingRate > 0.0)
    }

    @Test fun harshDecelNeedsHalfSecondBelowThreshold() {
        val short = SessionFeatureCalculator.compute(session(120), speed(50.0, 120) + accel(120, listOf(10_000L to 400L)))
        val long = SessionFeatureCalculator.compute(session(120), speed(50.0, 120) + accel(120, listOf(10_000L to 600L)))
        assertEquals(0, short.harshDecelCount)
        assertEquals(1, long.harshDecelCount)
        assertEquals(DecelSource.LONG_ACCEL, long.decelSource)
    }

    @Test fun harshDecelEventsSeparatedByThreeSeconds() {
        val close = SessionFeatureCalculator.compute(session(120), speed(50.0, 120) + accel(120, listOf(10_000L to 600L, 12_000L to 600L)))
        val apart = SessionFeatureCalculator.compute(session(120), speed(50.0, 120) + accel(120, listOf(10_000L to 600L, 14_000L to 600L)))
        assertEquals(1, close.harshDecelCount)
        assertEquals(2, apart.harshDecelCount)
    }

    @Test fun speedDerivedDecelOnlyAtFiveHz() {
        val sig = setOf(SignalId.VEHICLE_STATE, SignalId.SPEED)
        // 60 km/h → 20 km/h 를 1초에 (≈ -11.1 m/s²)
        val braking = (0..120_000L step 100).map { t ->
            val v = when { t < 10_000 -> 60.0; t < 11_000 -> 60.0 - (t - 10_000) * 0.04; else -> 20.0 }
            SignalSample(SignalId.SPEED, t, v)
        }
        val fast = SessionFeatureCalculator.compute(session(120, signals = sig, speedHz = 10.0), braking)
        assertEquals(DecelSource.SPEED_DERIVED, fast.decelSource)
        assertEquals(1, fast.harshDecelCount)
        val slow = SessionFeatureCalculator.compute(session(120, signals = sig, speedHz = 1.0), braking.filter { it.tMonoMs % 1000 == 0L })
        assertNull(slow.harshDecelCount)
        assertNull(slow.decelSource)
    }

    @Test fun nightShareUsesLocalClock() {
        val start = Instant.parse("2026-11-03T09:30:00Z") // 18:30 KST
        val f = SessionFeatureCalculator.compute(session(3600, start = start), speed(40.0, 3600) + accel(3600))
        assertEquals(0.5, f.nightShare, 0.01)
    }

    @Test fun noSpeedSignalMeansNoDistanceOrBands() {
        val f = SessionFeatureCalculator.compute(session(600, signals = setOf(SignalId.VEHICLE_STATE)), emptyList())
        assertNull(f.distanceKm)
        assertNull(f.speedBandSeconds)
        assertNull(f.harshDecelCount)
    }

    @Test fun speedBandsAccumulateSeconds() {
        val f = SessionFeatureCalculator.compute(session(600), speed(75.0, 600) + accel(600))
        assertEquals(600.0, f.speedBandSeconds!![SpeedBand.B60_90]!!, 1.0)
    }

    @Test fun shortSessionIsInvalid() {
        assertFalse(SessionFeatureCalculator.compute(session(50), speed(60.0, 50) + accel(50)).valid)
        assertFalse(SessionFeatureCalculator.compute(session(120), speed(3.0, 120) + accel(120)).valid) // 0.1 km
    }

    @Test fun unattributedSessionIsInvalid() {
        val f = SessionFeatureCalculator.compute(session(600, attribution = Attribution.UNVERIFIED), speed(60.0, 600) + accel(600))
        assertFalse(f.valid)
    }

    @Test fun outOfOrderSamplesDroppedAndCounted() {
        val samples = speed(60.0, 600).toMutableList()
        listOf(1000, 2000, 3000).forEach { i -> samples.add(i, SignalSample(SignalId.SPEED, samples[i].tMonoMs - 50_000, 60.0)) }
        val f = SessionFeatureCalculator.compute(session(600), samples + accel(600))
        assertEquals(3, f.droppedSamples)
        assertTrue(f.distanceKm!! >= 0.0)
        assertEquals(SessionQuality.EXCLUDED, f.quality)
    }

    @Test fun unterminatedSessionIsLimited() {
        val f = SessionFeatureCalculator.compute(session(600).copy(terminatedNormally = false), speed(60.0, 600) + accel(600))
        assertEquals(SessionQuality.LIMITED, f.quality)
    }

    @Test fun deterministicForSameInput() {
        val s = speed(60.0, 600) + accel(600, listOf(10_000L to 600L))
        assertEquals(SessionFeatureCalculator.compute(session(600), s), SessionFeatureCalculator.compute(session(600), s))
        assertEquals("drv-0.1.0", SessionFeatureCalculator.compute(session(600), s).algorithmVersion)
    }
}
