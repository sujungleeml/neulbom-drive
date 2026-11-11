package com.neulbom.drive.server

import com.neulbom.drive.server.driving.*
import com.neulbom.drive.server.checks.*
import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.domain.driving.DrivingCopy
import com.neulbom.drive.vehicle.mock.MockDrivingHistory
import com.neulbom.drive.vehicle.mock.MockTripGenerator
import com.neulbom.drive.vehicle.mock.TripType
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrivingApiTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val signals = setOf(SignalId.VEHICLE_STATE, SignalId.SPEED, SignalId.LONG_ACCEL)
    private val clock = FakeClock(Instant.parse("2026-11-20T03:00:00Z"))

    private fun trip(id: String = "d1") = MockTripGenerator.generate(id, TripType.ROAD, Instant.parse("2026-11-03T01:00:00Z"), 900, zone, 1, signals, 1)

    private suspend fun ApplicationTestBuilder.upload(dto: SessionUploadDto, key: String = dto.session.driveId) =
        client.post("/v1/driving-sessions") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", key)
            setBody(Json.encodeToString(dto))
        }

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { neulbomModule(InMemoryDrivingStore(), clock) }
        block()
    }

    @Test fun uploadComputesFeaturesServerSide() = app {
        val t = trip()
        val res = upload(SessionUploadDto.from(t.session, t.samples))
        assertEquals(HttpStatusCode.Created, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("d1", body["driveId"]!!.jsonPrimitive.content)
        assertEquals("true", body["valid"]!!.jsonPrimitive.content)
    }

    @Test fun reuploadIsIdempotent() = app {
        val t = trip()
        val first = upload(SessionUploadDto.from(t.session, t.samples)).bodyAsText()
        val second = upload(SessionUploadDto.from(t.session, t.samples))
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(first, second.bodyAsText())
        val report = Json.parseToJsonElement(client.get("/v1/reports/me/driving?period=2026-11").bodyAsText()).jsonObject
        val days = report["lines"]!!.jsonArray.first().jsonObject
        assertEquals(1.0, days["extra"]!!.jsonPrimitive.content.toDouble())
    }

    @Test fun conflictingReuploadIsRejected() = app {
        val t = trip()
        upload(SessionUploadDto.from(t.session, t.samples))
        val changed = t.session.copy(endedAt = t.session.endedAt.plusSeconds(60))
        assertEquals(HttpStatusCode.Conflict, upload(SessionUploadDto.from(changed, t.samples)).status)
    }

    @Test fun recordsSurviveServerRestartWithSqlite() {
        val file = java.io.File.createTempFile("neulbom-api", ".db").also { it.delete() }
        val t = trip()
        com.neulbom.drive.server.db.SqliteDb(file.path).use { db ->
            testApplication {
                application { neulbomModule(com.neulbom.drive.server.db.SqliteDrivingStore(db), clock) }
                assertEquals(HttpStatusCode.Created, upload(SessionUploadDto.from(t.session, t.samples)).status)
            }
        }
        com.neulbom.drive.server.db.SqliteDb(file.path).use { db ->
            testApplication {
                application { neulbomModule(com.neulbom.drive.server.db.SqliteDrivingStore(db), clock) }
                assertEquals(HttpStatusCode.OK, upload(SessionUploadDto.from(t.session, t.samples)).status, "다시 켠 뒤에도 같은 기록이 있다")
                val report = Json.parseToJsonElement(client.get("/v1/reports/me/driving?period=2026-11").bodyAsText()).jsonObject
                assertEquals(1.0, report["lines"]!!.jsonArray.first().jsonObject["extra"]!!.jsonPrimitive.content.toDouble())
            }
        }
        listOf("", "-wal", "-shm").forEach { java.io.File(file.path + it).delete() }
    }

    @Test fun mismatchedIdempotencyKeyIsRejected() = app {
        val t = trip()
        assertEquals(HttpStatusCode.BadRequest, upload(SessionUploadDto.from(t.session, t.samples), key = "other").status)
    }

    @Test fun reportReturnsFiveMetricKeysInOrder() = app {
        MockDrivingHistory.generate(LocalDate.of(2026, 11, 20), 3, 5, zone, signals).forEach { upload(SessionUploadDto.from(it.session, it.samples)) }
        val report = Json.parseToJsonElement(client.get("/v1/reports/me/driving?period=2026-11").bodyAsText()).jsonObject
        assertEquals(
            listOf("driving_days", "duration", "distance", "harsh_decel_per_100km", "night_share"),
            report["lines"]!!.jsonArray.map { it.jsonObject["key"]!!.jsonPrimitive.content },
        )
        assertEquals("MOCK", report["origin"]!!.jsonPrimitive.content)
        assertEquals("drv-0.1.0", report["algorithmVersion"]!!.jsonPrimitive.content)
    }

    @Test fun reportTextsComeFromTemplates() = app {
        val t = trip()
        upload(SessionUploadDto.from(t.session, t.samples))
        val report = Json.parseToJsonElement(client.get("/v1/reports/me/driving?period=2026-11").bodyAsText()).jsonObject
        val notices = report["notices"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(DrivingCopy.render("MOCK_DATA") in notices)
        assertTrue(DrivingCopy.render("EXTERNAL_CONDITIONS_NOT_ADJUSTED") in notices)
        report["lines"]!!.jsonArray.forEach { assertTrue(it.jsonObject["text"]!!.jsonPrimitive.content.isNotBlank()) }
    }

    /** 기록 상태: 받은 수·넣은 수·신호 일부 끊김·뺀 수만 센다(점수 없음). */
    @Test fun ownReportCountsRecordQuality() = app {
        val t = trip()
        upload(SessionUploadDto.from(t.session, t.samples))
        val cut = trip("d2").let { it.copy(session = it.session.copy(terminatedNormally = false)) }
        upload(SessionUploadDto.from(cut.session, cut.samples))
        val q = Json.parseToJsonElement(client.get("/v1/reports/me/driving?period=2026-11").bodyAsText()).jsonObject["quality"]!!.jsonObject
        assertEquals(2, q["drives"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, q["included"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, q["limited"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, q["excluded"]!!.jsonPrimitive.content.toInt())
        val empty = Json.parseToJsonElement(client.get("/v1/reports/me/driving?period=2026-10").bodyAsText()).jsonObject["quality"]!!.jsonObject
        assertEquals(0, empty["drives"]!!.jsonPrimitive.content.toInt())
    }

    @Test fun emptyMonthIsCollecting() = app {
        val report = Json.parseToJsonElement(client.get("/v1/reports/me/driving?period=2026-11").bodyAsText()).jsonObject
        assertEquals("COLLECTING", report["state"]!!.jsonPrimitive.content)
        assertTrue(report["lines"]!!.jsonArray.isEmpty())
    }

    @Test fun defaultPeriodIsCurrentMonthInSeoul() = app {
        val report = Json.parseToJsonElement(client.get("/v1/reports/me/driving").bodyAsText()).jsonObject
        assertEquals("2026-11", report["period"]!!.jsonPrimitive.content)
    }

    @Test fun signalSupportRoundTrip() = app {
        val body = Json.encodeToString(listOf(SignalSupportDto("SPEED", "SUPPORTED", "MOCK", 10.0), SignalSupportDto("BRAKE", "UNSUPPORTED", "MOCK", null)))
        client.post("/v1/signal-support") { contentType(ContentType.Application.Json); setBody(body) }
        val got = Json.parseToJsonElement(client.get("/v1/signal-support").bodyAsText()).jsonArray
        assertEquals(2, got.size)
    }

    @Test fun responsesHaveNoLocationFields() = app {
        MockDrivingHistory.generate(LocalDate.of(2026, 11, 20), 2, 5, zone, signals).forEach { upload(SessionUploadDto.from(it.session, it.samples)) }
        val texts = listOf("/v1/reports/me/driving?period=2026-11", "/v1/signal-support").map { client.get(it).bodyAsText() }
        val forbiddenKeys = listOf("lat", "lng", "lon", "location", "route", "destination", "address", "gps")
        texts.forEach { text -> keys(Json.parseToJsonElement(text)).forEach { k -> assertTrue(forbiddenKeys.none { k.lowercase().contains(it) }, k) } }
    }

    private fun keys(e: JsonElement): List<String> = when (e) {
        is JsonObject -> e.keys.toList() + e.values.flatMap(::keys)
        is JsonArray -> e.flatMap(::keys)
        else -> emptyList()
    }
}

class DemoSeedTest {
    @Test fun demoSeedFillsTwoMonthsOfMockDataComparably() {
        val store = InMemoryDrivingStore()
        seedDemo(store, java.time.LocalDate.of(2026, 11, 28))
        assertTrue(store.all().isNotEmpty())
        assertTrue(store.all().all { it.first.origin == com.neulbom.drive.core.model.DataOrigin.MOCK })
        assertEquals(7, store.signalSupport().size)
    }
}

/** 앱 업로더가 만든 본문을 서버가 그대로 받아들이는지(와이어 호환). */
class UploaderCompatibilityTest {
    @Test fun uploaderPayloadIsAcceptedByServer() = testApplication {
        application { neulbomModule(InMemoryDrivingStore(), FakeClock(Instant.parse("2026-11-20T03:00:00Z"))) }
        val trip = MockTripGenerator.generate("w1", TripType.CITY, Instant.parse("2026-11-03T01:00:00Z"), 600, ZoneId.of("Asia/Seoul"), 2,
            setOf(SignalId.VEHICLE_STATE, SignalId.SPEED, SignalId.LONG_ACCEL), 1)
        val res = client.post("/v1/driving-sessions") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "w1")
            setBody(com.neulbom.drive.data.sync.UploadPayload.json(trip.session, trip.samples))
        }
        assertEquals(HttpStatusCode.Created, res.status)
    }
}
