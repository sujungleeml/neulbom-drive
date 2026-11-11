package com.neulbom.drive.data.sync

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal fun sampleUpload(id: String = "a1") = AssessmentUpload(
    AssessmentUploadSession(
        assessmentId = id, startedAt = Instant.parse("2026-11-02T01:00:00Z"), endedAt = Instant.parse("2026-11-02T01:03:00Z"),
        status = "COMPLETED", stopReason = null, isFirstAssessment = true,
        protocolVersions = mapOf("VISUAL_ATTENTION" to 1, "GO_NO_GO" to 1, "TASK_SWITCHING" to 1),
        appVersion = "0.3.0", deviceProfile = "emu \"x\"", vehicleSource = "MOCK",
    ),
    listOf(
        AssessmentUploadTrial("GO_NO_GO", 1, 1, 0, "MAIN", "NOGO", 1000, 1016, null, null, null, "CORRECT_REJECTION", false, 16),
        AssessmentUploadTrial("VISUAL_ATTENTION", 1, 1, 1, "PRACTICE", "ARROW_LEFT", 3000, 3016, 3500, 484, "LEFT", "CORRECT", true, 16),
    ),
)

class AssessmentUploaderTest {
    private val requests = CopyOnWriteArrayList<Pair<String?, String>>()
    private var statuses = ArrayDeque(listOf(201))
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/v1/assessments") { ex ->
            requests += ex.requestHeaders.getFirst("Idempotency-Key") to ex.requestBody.readBytes().decodeToString()
            ex.sendResponseHeaders(statuses.removeFirstOrNull() ?: 201, -1)
            ex.close()
        }
        start()
    }
    private val base get() = "http://127.0.0.1:${server.address.port}"

    private class FakeQueue(items: List<AssessmentUpload>) : AssessmentQueue {
        val items = items.toMutableList()
        val synced = mutableListOf<String>()
        override suspend fun pending() = items.toList()
        override suspend fun markSynced(assessmentId: String) {
            synced += assessmentId
            items.removeAll { it.session.assessmentId == assessmentId }
        }
    }

    @AfterTest fun stop() = server.stop(0)

    @Test fun sendsIdempotencyKeyAndMarksSyncedOnlyOn2xx() = runTest {
        val q = FakeQueue(listOf(sampleUpload("a1"), sampleUpload("a2")))
        statuses = ArrayDeque(listOf(201, 400))
        val r = AssessmentUploader(base, q, sleeper = {}).uploadPending()
        assertEquals(listOf("a1"), r.syncedIds)
        assertEquals(1, r.failed)
        assertEquals(listOf("a1", "a2"), requests.map { it.first })
        assertEquals(listOf("a1"), q.synced)
    }

    @Test fun failedUploadStaysPending() = runTest {
        val q = FakeQueue(listOf(sampleUpload()))
        statuses = ArrayDeque(listOf(500, 500, 500))
        val waits = mutableListOf<Long>()
        val r = AssessmentUploader(base, q, sleeper = { waits += it }).uploadPending()
        assertEquals(emptyList(), r.syncedIds)
        assertEquals(listOf(1_000L, 2_000L), waits)
        assertEquals(1, q.items.size)
    }

    @Test fun blankBaseUrlSendsNothing() = runTest {
        val r = AssessmentUploader("", FakeQueue(listOf(sampleUpload())), sleeper = {}).uploadPending()
        assertEquals(AssessmentUploadReport(emptyList(), 0), r)
        assertTrue(requests.isEmpty())
    }

    @Test fun payloadEscapesStringsAndKeepsNulls() {
        val json = AssessmentPayload.json(sampleUpload())
        assertTrue("\"deviceProfile\":\"emu \\\"x\\\"\"" in json)
        assertTrue("\"reactionTimeMs\":null" in json)
        assertTrue("\"stopReason\":null" in json)
        assertTrue("score" !in json)
    }
}
