package com.neulbom.drive.server

import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.data.sync.AssessmentPayload
import com.neulbom.drive.data.sync.AssessmentUpload
import com.neulbom.drive.data.sync.AssessmentUploadSession
import com.neulbom.drive.data.sync.AssessmentUploadTrial
import com.neulbom.drive.server.checks.AssessmentUploadDto
import com.neulbom.drive.server.checks.InMemoryCheckStore
import com.neulbom.drive.server.driving.InMemoryDrivingStore
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** 앱(data:sync)이 만드는 본문을 서버가 그대로 받는다. */
class AssessmentWireTest {
    private val upload = AssessmentUpload(
        AssessmentUploadSession("w1", Instant.parse("2026-11-02T01:00:00Z"), null, "INTERRUPTED", "DRIVING", false,
            mapOf("VISUAL_ATTENTION" to 1), "0.3.0", "emu", "MOCK"),
        listOf(AssessmentUploadTrial("VISUAL_ATTENTION", 1, 1, 0, "MAIN", "ARROW_RIGHT", 10, null, null, null, null, "OMISSION", false, 0)),
    )

    @Test fun appPayloadIsAcceptedByServer() = testApplication {
        val store = InMemoryCheckStore()
        application { neulbomModule(InMemoryDrivingStore(), FakeClock(), store) }
        val res = client.post("/v1/assessments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "w1")
            setBody(AssessmentPayload.json(upload))
        }
        assertEquals(HttpStatusCode.Created, res.status)
        val stored = store.get("w1")!!
        assertEquals(Json.decodeFromString(AssessmentUploadDto.serializer(), AssessmentPayload.json(upload)), stored)
        assertEquals("DRIVING", stored.session.stopReason)
        assertEquals(null, stored.trials.single().actualOnsetMs)
    }
}
