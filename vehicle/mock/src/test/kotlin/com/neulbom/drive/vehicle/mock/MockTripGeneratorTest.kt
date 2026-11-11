package com.neulbom.drive.vehicle.mock

import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.domain.driving.SessionFeatureCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockTripGeneratorTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val signals = setOf(SignalId.VEHICLE_STATE, SignalId.SPEED, SignalId.LONG_ACCEL)
    private val start = Instant.parse("2026-11-03T01:00:00Z")

    @Test fun generatedTripMatchesRequestedHarshDecelCount() {
        listOf(TripType.CITY to 1, TripType.ROAD to 2, TripType.HIGHWAY to 3, TripType.ROAD to 0).forEachIndexed { i, (type, n) ->
            val trip = MockTripGenerator.generate("t$i", type, start, 1200, zone, seed = 7L + i, signals = signals, harshDecelCount = n)
            val f = SessionFeatureCalculator.compute(trip.session, trip.samples)
            assertEquals(n, f.harshDecelCount, "$type")
            assertTrue(f.valid)
        }
    }

    @Test fun speedDerivedCountAlsoMatchesWithoutLongAccel() {
        val sig = setOf(SignalId.VEHICLE_STATE, SignalId.SPEED)
        val trip = MockTripGenerator.generate("s", TripType.ROAD, start, 1200, zone, seed = 3, signals = sig, harshDecelCount = 2)
        assertEquals(2, SessionFeatureCalculator.compute(trip.session, trip.samples).harshDecelCount)
    }

    @Test fun meanSpeedRoughlyFollowsTripType() {
        val trip = MockTripGenerator.generate("h", TripType.HIGHWAY, start, 1800, zone, seed = 1, signals = signals)
        val km = SessionFeatureCalculator.compute(trip.session, trip.samples).distanceKm!!
        assertTrue(km / 0.5 in 70.0..110.0, "mean ${km / 0.5}")
    }

    @Test fun allMockDataIsMarkedMock() {
        val trip = MockTripGenerator.generate("m", TripType.CITY, start, 600, zone, seed = 1, signals = signals)
        assertEquals(DataOrigin.MOCK, trip.session.origin)
        assertTrue(trip.samples.all { it.signal in signals })
    }

    @Test fun sameSeedSameTrips() {
        assertEquals(
            MockTripGenerator.generate("a", TripType.ROAD, start, 600, zone, 5, signals, 1),
            MockTripGenerator.generate("a", TripType.ROAD, start, 600, zone, 5, signals, 1),
        )
        assertEquals(
            MockDrivingHistory.generate(LocalDate.of(2026, 11, 20), 4, 9, zone, signals).map { it.session },
            MockDrivingHistory.generate(LocalDate.of(2026, 11, 20), 4, 9, zone, signals).map { it.session },
        )
    }

    @Test fun historyCoversEightPlusDaysOverFourWeeks() {
        val trips = MockDrivingHistory.generate(LocalDate.of(2026, 11, 20), 4, 11, zone, signals)
        val days = trips.map { it.session.startedAt.atZone(zone).toLocalDate() }.toSet()
        assertTrue(days.size >= 8, "days=${days.size}")
        assertTrue(days.all { it in LocalDate.of(2026, 10, 24)..LocalDate.of(2026, 11, 20) })
        assertTrue(trips.any { it.session.startedAt.atZone(zone).hour >= 19 }, "야간 주행 포함")
    }

    @Test fun historyNeverFailsAcrossSeeds() {
        (1L..60L).forEach { seed -> MockDrivingHistory.generate(LocalDate.of(2026, 11, 20), 8, seed, zone, signals) }
    }

    @Test fun mockAdapterAdvertisesSimulatedSupport() {
        val support = MockVehicleAdapter(com.neulbom.drive.core.time.FakeClock()).signalSupport()
        assertEquals(SignalId.entries.toSet(), support.map { it.signal }.toSet())
        assertTrue(support.filter { it.signal in signals }.all { it.sdkName == "MOCK" })
    }
}
