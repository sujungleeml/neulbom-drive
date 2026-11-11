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
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 한눈에 보기(D23): 두 레인을 같은 주 축에 따로 담고, 합친 값·연결 해석이 없다. */
class OverviewApiTest {
    private val zone = ZoneId.of("Asia/Seoul")
    // 2026-11-20(금) → 이번 주 월요일 11-16
    private val clock = FakeClock(Instant.parse("2026-11-20T03:00:00Z"))
    private val drives = InMemoryDrivingStore()
    private val checks = InMemoryCheckStore()

    private fun drive(id: String, at: String, seconds: Int = 900) {
        val t = MockTripGenerator.generate(id, TripType.ROAD, Instant.parse(at), seconds, zone, 1, setOf(SignalId.VEHICLE_STATE, SignalId.SPEED, SignalId.LONG_ACCEL), 1)
        drives.upsert(t.session, SessionFeatureCalculator.compute(t.session, t.samples))
    }

    private fun overview(weeks: Int = 4, block: (JsonObject) -> Unit) = testApplication {
        application { neulbomModule(drives, clock, checks) }
        val res = client.get("/v1/reports/me/overview?weeks=$weeks")
        assertEquals(HttpStatusCode.OK, res.status)
        block(Json.parseToJsonElement(res.bodyAsText()).jsonObject)
    }

    @Test fun weeksAreMondayAlignedAndShared() = overview(4) { o ->
        assertEquals(listOf("2026-10-26", "2026-11-02", "2026-11-09", "2026-11-16"), o["weekStarts"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(4, o["driving"]!!.jsonObject["weeks"]!!.jsonArray.size)
        assertEquals(4, o["checks"]!!.jsonObject["weeks"]!!.jsonArray.size)
        assertEquals("2026-11-22", o["to"]!!.jsonPrimitive.content)
        assertEquals("2026-11-20", o["asOf"]!!.jsonPrimitive.content)
        assertEquals("true", o["lastWeekInProgress"]!!.jsonPrimitive.content, "금요일이면 이번 주는 진행 중")
    }

    @Test fun lanesCountTheirOwnRecordsByWeek() {
        drive("d1", "2026-11-03T01:00:00Z")
        drive("d2", "2026-11-04T01:00:00Z")
        drive("d3", "2026-11-17T01:00:00Z")
        checks.putIfAbsent(demoAssessment("a1", "2026-11-05T01:00:00Z"))
        checks.putIfAbsent(demoAssessment("a2", "2026-11-18T01:00:00Z", status = "INTERRUPTED"))
        overview(4) { o ->
            val dw = o["driving"]!!.jsonObject["weeks"]!!.jsonArray.map { it.jsonObject["drivingDays"]!!.jsonPrimitive.int }
            assertEquals(listOf(0, 2, 0, 1), dw)
            val cw = o["checks"]!!.jsonObject["weeks"]!!.jsonArray.map { it.jsonObject.let { w -> w["completed"]!!.jsonPrimitive.int to w["stopped"]!!.jsonPrimitive.int } }
            assertEquals(listOf(0 to 0, 1 to 0, 0 to 0, 0 to 1), cw)
            val va = o["checks"]!!.jsonObject["tasks"]!!.jsonArray.first().jsonObject
            assertEquals(1, va["points"]!!.jsonArray.size, "마친 점검만 점으로")
            assertEquals("v1·s1·emu", va["points"]!!.jsonArray.first().jsonObject["compareKey"]!!.jsonPrimitive.content)
        }
    }

    @Test fun shortWeeksHideDecelRate() {
        drive("short", "2026-11-17T01:00:00Z", seconds = 600)
        overview(4) { o ->
            val last = o["driving"]!!.jsonObject["weeks"]!!.jsonArray.last().jsonObject
            assertEquals(1, last["drives"]!!.jsonPrimitive.int)
            assertEquals("null", last["harshDecelPer100Km"].toString(), "거리가 짧은 주는 급감속 비율을 보여 주지 않는다")
            assertEquals("NOT_ENOUGH_DISTANCE", last["harshDecelStatus"]!!.jsonPrimitive.content)
            val empty = o["driving"]!!.jsonObject["weeks"]!!.jsonArray.first().jsonObject
            assertEquals("NO_DRIVING", empty["harshDecelStatus"]!!.jsonPrimitive.content, "주행 없는 주와 거리 부족 주를 구분")
        }
    }

    @Test fun onlyAllowedWeekRanges() = testApplication {
        application { neulbomModule(drives, clock, checks) }
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/reports/me/overview?weeks=5").status)
    }

    @Test fun noCombinedOrLinkingFields() {
        drive("d1", "2026-11-03T01:00:00Z")
        checks.putIfAbsent(demoAssessment("a1", "2026-11-05T01:00:00Z"))
        overview(8) { o ->
            assertEquals(setOf("weeks", "from", "to", "weekStarts", "asOf", "lastWeekInProgress", "driving", "checks", "notices"), o.keys)
            val banned = Regex("(?i)(score|combined|correlat|together|both|risk|grade|index)")
            fun keys(e: JsonElement): List<String> = when (e) {
                is JsonObject -> e.keys.toList() + e.values.flatMap(::keys)
                is JsonArray -> e.flatMap(::keys)
                else -> emptyList()
            }
            keys(o).forEach { k -> assertTrue(banned.find(k) == null, "금지된 필드 이름: $k") }
            val texts = o.toString()
            listOf("함께 늘", "함께 줄", "같이 변", "관련이", "때문에", "위험", "정상").forEach { w -> assertTrue(w !in texts, "연결·판정 문구: $w") }
        }
    }
}
