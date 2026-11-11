package com.neulbom.drive.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.vehicle.mock.MockTripGenerator
import com.neulbom.drive.vehicle.mock.TripType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DrivingQueueTest {
    private lateinit var db: NeulbomDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NeulbomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun savedSessionIsPendingWithAllSamplesUntilUploaded() = runTest {
        val queue = RoomDrivingQueue(db)
        val trip = MockTripGenerator.generate(
            "d1", TripType.ROAD, Instant.parse("2026-11-03T01:00:00Z"), 900, ZoneId.of("Asia/Seoul"), 1,
            setOf(SignalId.VEHICLE_STATE, SignalId.SPEED, SignalId.LONG_ACCEL), 1,
        )
        queue.saveSession(trip.session, trip.samples)
        val (session, samples) = queue.pending().single()
        assertEquals(trip.session, session)
        assertEquals(trip.samples, samples)

        queue.markUploaded("d1")
        assertTrue(queue.pending().isEmpty())
        assertTrue(db.drivingDao().chunks("d1").isEmpty())
        assertEquals(1, db.drivingDao().count())
    }
}
