package com.neulbom.drive.data.sync

import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.model.SignalSample
import com.neulbom.drive.vehicle.mock.MockTripGenerator
import com.neulbom.drive.vehicle.mock.TripType
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrivingUploaderTest {
    private val trip = MockTripGenerator.generate(
        "d1", TripType.ROAD, Instant.parse("2026-11-03T01:00:00Z"), 300, ZoneId.of("Asia/Seoul"), 1,
        setOf(SignalId.VEHICLE_STATE, SignalId.SPEED, SignalId.LONG_ACCEL),
    )
    private val requests = CopyOnWriteArrayList<Pair<String?, String>>()
    private var statuses = ArrayDeque(listOf(201))
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/v1/driving-sessions") { ex ->
            requests += ex.requestHeaders.getFirst("Idempotency-Key") to ex.requestBody.readBytes().decodeToString()
            val code = statuses.removeFirstOrNull() ?: 201
            ex.sendResponseHeaders(code, -1)
            ex.close()
        }
        start()
    }
    private val base get() = "http://127.0.0.1:${server.address.port}"

    private class FakeQueue(items: List<Pair<DrivingSession, List<SignalSample>>>) : UploadQueue {
        val pendingItems = items.toMutableList()
        val uploaded = mutableListOf<String>()
        override suspend fun pending() = pendingItems.toList()
        override suspend fun markUploaded(driveId: String) {
            uploaded += driveId
            pendingItems.removeAll { it.first.driveId == driveId }
        }
    }

    @AfterTest fun stop() = server.stop(0)

    @Test fun uploadSendsIdempotencyKeyAndMarksUploaded() = runTest {
        val queue = FakeQueue(listOf(trip.session to trip.samples))
        val report = DrivingUploader(base, queue, sleeper = {}).uploadPending()
        assertEquals(UploadReport(sent = 1, failed = 0), report)
        assertEquals("d1", requests.single().first)
        assertTrue("\"samplesEncoded\"" in requests.single().second)
        assertEquals(listOf("d1"), queue.uploaded)
    }

    @Test fun serverErrorRetriesWithBackoffThenSucceeds() = runTest {
        statuses = ArrayDeque(listOf(500, 503, 201))
        val waits = mutableListOf<Long>()
        val queue = FakeQueue(listOf(trip.session to trip.samples))
        val report = DrivingUploader(base, queue, sleeper = { waits += it }).uploadPending()
        assertEquals(UploadReport(1, 0), report)
        assertEquals(3, requests.size)
        assertEquals(listOf(1000L, 2000L), waits)
    }

    @Test fun serverErrorKeepsPendingAfterThreeAttempts() = runTest {
        statuses = ArrayDeque(listOf(500, 500, 500))
        val queue = FakeQueue(listOf(trip.session to trip.samples))
        assertEquals(UploadReport(0, 1), DrivingUploader(base, queue, sleeper = {}).uploadPending())
        assertEquals(3, requests.size)
        assertEquals(1, queue.pendingItems.size)
    }

    @Test fun clientErrorIsNotRetried() = runTest {
        statuses = ArrayDeque(listOf(400))
        val queue = FakeQueue(listOf(trip.session to trip.samples))
        assertEquals(UploadReport(0, 1), DrivingUploader(base, queue, sleeper = {}).uploadPending())
        assertEquals(1, requests.size)
    }

    @Test fun okOnReuploadCountsAsSent() = runTest {
        statuses = ArrayDeque(listOf(200))
        val queue = FakeQueue(listOf(trip.session to trip.samples))
        assertEquals(UploadReport(1, 0), DrivingUploader(base, queue, sleeper = {}).uploadPending())
    }

    @Test fun noUploadWhenBaseUrlBlank() = runTest {
        val queue = FakeQueue(listOf(trip.session to trip.samples))
        assertEquals(UploadReport(0, 0), DrivingUploader("", queue, sleeper = {}).uploadPending())
        assertTrue(requests.isEmpty())
    }
}
