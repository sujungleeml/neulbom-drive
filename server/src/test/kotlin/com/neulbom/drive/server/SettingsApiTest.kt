package com.neulbom.drive.server

import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.server.checks.InMemoryCheckStore
import com.neulbom.drive.server.settings.InMemorySettingsStore
import com.neulbom.drive.server.driving.InMemoryDrivingStore
import com.neulbom.drive.domain.driving.SessionFeatureCalculator
import com.neulbom.drive.vehicle.mock.MockTripGenerator
import com.neulbom.drive.vehicle.mock.TripType
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsApiTest {
    private val clock = FakeClock(Instant.parse("2026-11-05T03:00:00Z"))
    private val driving = InMemoryDrivingStore()
    private val checks = InMemoryCheckStore()
    private val settings = InMemorySettingsStore()

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { neulbomModule(driving, clock, checks, settings) }
        block()
    }

    private suspend fun ApplicationTestBuilder.settingsJson(): JsonObject = Json.parseToJsonElement(client.get("/v1/settings/me").bodyAsText()).jsonObject
    private fun JsonObject.granted(type: String) = this["consents"]!!.jsonArray.map { it.jsonObject }.firstOrNull { it["type"]!!.jsonPrimitive.content == type }?.get("granted")?.jsonPrimitive?.content

    @Test fun defaultsAllOff() = app {
        val s = settingsJson()
        assertEquals(0, s["consents"]!!.jsonArray.size)
        assertEquals("null", s["offersPausedUntil"].toString())
    }

    @Test fun webToggleRecordsEventWithServerTime() = app {
        val res = client.put("/v1/settings/me/consents/COGNITIVE_CHECK") { contentType(ContentType.Application.Json); setBody("""{"granted":false}""") }
        assertEquals(HttpStatusCode.OK, res.status)
        val c = settingsJson()["consents"]!!.jsonArray.single().jsonObject
        assertEquals("false", c["granted"]!!.jsonPrimitive.content)
        assertEquals("2026-11-05T03:00:00Z", c["changedAt"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.BadRequest, client.put("/v1/settings/me/consents/USAGE_ANALYTICS") { contentType(ContentType.Application.Json); setBody("""{"granted":true}""") }.status)
    }

    @Test fun carEventsMergeLatestWins() = app {
        client.put("/v1/settings/me/consents/DRIVING_RECORD") { contentType(ContentType.Application.Json); setBody("""{"granted":false}""") } // 03:00
        client.post("/v1/consent-events") {
            contentType(ContentType.Application.Json)
            setBody("""[{"type":"DRIVING_RECORD","granted":true,"changedAt":"2026-11-05T02:00:00Z"},{"type":"COGNITIVE_CHECK","granted":true,"changedAt":"2026-11-05T04:00:00Z"}]""")
        }
        val s = settingsJson()
        assertEquals("false", s.granted("DRIVING_RECORD")) // 웹 03:00이 차량 02:00보다 늦음
        assertEquals("true", s.granted("COGNITIVE_CHECK"))
    }

    @Test fun pauseSetsUntilAndZeroClears() = app {
        client.put("/v1/settings/me/offers") { contentType(ContentType.Application.Json); setBody("""{"pauseDays":7}""") }
        assertEquals("2026-11-12T03:00:00Z", settingsJson()["offersPausedUntil"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.BadRequest, client.put("/v1/settings/me/offers") { contentType(ContentType.Application.Json); setBody("""{"pauseDays":3}""") }.status)
        client.put("/v1/settings/me/offers") { contentType(ContentType.Application.Json); setBody("""{"pauseDays":0}""") }
        assertEquals("null", settingsJson()["offersPausedUntil"].toString())
    }

    @Test fun exportKeepsAxesSeparateAndDeleteKeepsConsentAudit() = app {
        val trip = MockTripGenerator.generate("d1", TripType.ROAD, Instant.parse("2026-11-03T01:00:00Z"), 300, ZoneId.of("Asia/Seoul"), 1,
            setOf(SignalId.VEHICLE_STATE, SignalId.SPEED, SignalId.LONG_ACCEL))
        driving.upsert(trip.session, SessionFeatureCalculator.compute(trip.session, trip.samples))
        checks.putIfAbsent(demoAssessment("a1", "2026-11-02T01:00:00Z"))
        client.put("/v1/settings/me/consents/COGNITIVE_CHECK") { contentType(ContentType.Application.Json); setBody("""{"granted":true}""") }
        val export = Json.parseToJsonElement(client.get("/v1/me/export").bodyAsText()).jsonObject
        assertEquals(setOf("drivingSessions", "assessments", "consentEvents", "exportedAt"), export.keys)
        assertEquals(1, export["drivingSessions"]!!.jsonArray.size)
        assertEquals(1, export["assessments"]!!.jsonArray.size)
        assertEquals(HttpStatusCode.NoContent, client.delete("/v1/me/data").status)
        assertTrue(driving.all().isEmpty())
        assertTrue(checks.all().isEmpty())
        assertEquals("true", settingsJson().granted("COGNITIVE_CHECK")) // 동의 감사 이력은 남긴다
    }
}
