package com.neulbom.drive.server

import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.domain.driving.SessionFeatureCalculator
import com.neulbom.drive.server.checks.InMemoryCheckStore
import com.neulbom.drive.server.driving.InMemoryDrivingStore
import com.neulbom.drive.vehicle.mock.MockTripGenerator
import com.neulbom.drive.vehicle.mock.TripType
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JourneyApiTest {
    private val clock = FakeClock(Instant.parse("2026-11-20T03:00:00Z"))
    private val driving = InMemoryDrivingStore()
    private val checks = InMemoryCheckStore()

    private fun drive(id: String, at: String) {
        val t = MockTripGenerator.generate(id, TripType.ROAD, Instant.parse(at), 600, ZoneId.of("Asia/Seoul"), 1,
            setOf(SignalId.VEHICLE_STATE, SignalId.SPEED, SignalId.LONG_ACCEL))
        driving.upsert(t.session, SessionFeatureCalculator.compute(t.session, t.samples))
    }

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { neulbomModule(driving, clock, checks) }
        block()
    }

    private suspend fun ApplicationTestBuilder.journey(q: String = ""): JsonObject =
        Json.parseToJsonElement(client.get("/v1/reports/me/journey$q").bodyAsText()).jsonObject

    @Test fun monthsAreNewestFirstWithBothAxesSeparate() = app {
        drive("d1", "2026-11-03T01:00:00Z"); drive("d2", "2026-11-03T05:00:00Z"); drive("d3", "2026-10-10T01:00:00Z")
        checks.putIfAbsent(demoAssessment("a1", "2026-11-04T01:00:00Z"))
        checks.putIfAbsent(demoAssessment("a2", "2026-11-05T01:00:00Z", status = "INTERRUPTED"))
        val months = journey()["months"]!!.jsonArray.map { it.jsonObject }
        assertEquals(6, months.size)
        assertEquals(listOf("2026-11", "2026-10", "2026-09"), months.take(3).map { it["period"]!!.jsonPrimitive.content })
        val nov = months.first()
        assertEquals(setOf("period", "driving", "checks", "text"), nov.keys) // 두 축은 서로 다른 객체, 합친 값 없음
        val d = nov["driving"]!!.jsonObject
        assertEquals(1, d["drivingDays"]!!.jsonPrimitive.int)
        assertEquals(2, d["drives"]!!.jsonPrimitive.int)
        assertEquals("true", d["mock"]!!.jsonPrimitive.content)
        val c = nov["checks"]!!.jsonObject
        assertEquals(1, c["completed"]!!.jsonPrimitive.int)
        assertEquals(1, c["stopped"]!!.jsonPrimitive.int)
        assertEquals("1일 운전했고, 점검을 1번 마쳤어요.", nov["text"]!!.jsonPrimitive.content)
        assertEquals("기록이 없는 달이에요.", months[2]["text"]!!.jsonPrimitive.content)
    }

    @Test fun monthCountIsLimited() = app {
        assertEquals(12, journey("?months=12")["months"]!!.jsonArray.size)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/reports/me/journey?months=40").status)
    }

    @Test fun journeyCopyIsFactualOnly() = app {
        drive("d1", "2026-11-03T01:00:00Z")
        val text = client.get("/v1/reports/me/journey").bodyAsText()
        listOf("배지", "등급", "점수", "순위", "연속", "잘하", "부족", "칭찬", "목표 달성", "score", "rank", "streak", "badge").forEach {
            assertTrue(it !in text, "'$it' in $text")
        }
    }
}
