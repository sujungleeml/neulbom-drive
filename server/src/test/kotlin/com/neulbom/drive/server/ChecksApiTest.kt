package com.neulbom.drive.server

import com.neulbom.drive.server.checks.AssessmentSessionDto
import com.neulbom.drive.server.checks.AssessmentUploadDto
import com.neulbom.drive.server.checks.InMemoryCheckStore
import com.neulbom.drive.server.checks.TrialDto
import com.neulbom.drive.server.driving.InMemoryDrivingStore
import com.neulbom.drive.core.time.FakeClock
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 시연·테스트용 점검 한 회(세 과제, 시드 없는 고정 값). */
internal fun demoAssessment(
    id: String,
    startedAt: String,
    rt: Long = 520,
    status: String = "COMPLETED",
    source: String = "MOCK",
    versions: Map<String, Int> = mapOf("VISUAL_ATTENTION" to 1, "GO_NO_GO" to 1, "TASK_SWITCHING" to 1),
): AssessmentUploadDto {
    fun t(task: String, i: Int, type: String, rt: Long?, outcome: String) =
        TrialDto(task, versions.getValue(task), 1, i, "MAIN", type, i * 2000L, i * 2000L + 16, rt?.let { i * 2000L + 16 + it }, rt, rt?.let { "TAP" }, outcome, false, 16)
    val trials = List(24) { t("VISUAL_ATTENTION", it, "ARROW_LEFT", rt, "CORRECT") } +
        List(30) { t("GO_NO_GO", it, "GO", rt - 120, "CORRECT") } + List(10) { t("GO_NO_GO", 30 + it, "NOGO", null, "CORRECT_REJECTION") } +
        List(32) { t("TASK_SWITCHING", it, if (it % 2 == 0) "DIGIT_3_PARITY_SWITCH" else "DIGIT_8_MAGNITUDE_REPEAT", rt + 200 + (it % 2) * 0, "CORRECT") }
    return AssessmentUploadDto(
        AssessmentSessionDto(id, startedAt, startedAt, status, null, false, versions, "test", "emu", source),
        trials,
    )
}

class ChecksApiTest {
    private val clock = FakeClock(Instant.parse("2026-11-20T03:00:00Z"))

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { neulbomModule(InMemoryDrivingStore(), clock, InMemoryCheckStore()) }
        block()
    }

    private suspend fun ApplicationTestBuilder.upload(dto: AssessmentUploadDto, key: String = dto.session.assessmentId) =
        client.post("/v1/assessments") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", key)
            setBody(Json.encodeToString(dto))
        }

    @Test fun uploadIsIdempotent() = app {
        val a = demoAssessment("a1", "2026-11-02T01:00:00Z")
        assertEquals(HttpStatusCode.Created, upload(a).status)
        val again = upload(a)
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals(96, Json.parseToJsonElement(again.bodyAsText()).jsonObject["trialsStored"]!!.jsonPrimitive.int)
        // 같은 ID로 내용이 다르면(시행 일부만 등) 덮어쓰지 않고 충돌. 차는 실패로 보고 다음에 다시 보낸다.
        assertEquals(HttpStatusCode.Conflict, upload(a.copy(trials = a.trials.take(3))).status)
        val report = Json.parseToJsonElement(client.get("/v1/reports/me/checks").bodyAsText()).jsonObject
        assertEquals(1, report["history"]!!.jsonArray.size)
    }

    /** 점검별 기록(points)은 본인 리포트에만 있고, 공유 화면이 쓰는 리포트에는 없다. */
    @Test fun ownReportHasPerCheckPointsWithCompareKeyButShareReportDoesNot() {
        val store = InMemoryCheckStore()
        listOf("2026-11-02T01:00:00Z", "2026-11-05T01:00:00Z").forEachIndexed { i, at -> store.putIfAbsent(demoAssessment("p$i", at, rt = 500L + 10 * i)) }
        val own = com.neulbom.drive.server.checks.checksReport(store, withPoints = true)
        own.tasks.forEach { t ->
            assertEquals(2, t.points.size, t.taskId)
            assertTrue(t.points.all { it.compareKey.startsWith("v1·s1·") }, t.taskId)
        }
        assertEquals(listOf(500L, 510L), own.tasks.first { it.taskId == "VISUAL_ATTENTION" }.points.map { it.medianRtMs })
        assertTrue(com.neulbom.drive.server.checks.checksReport(store).tasks.all { it.points.isEmpty() })
    }

    @Test fun keyMustMatchAssessmentId() = app {
        assertEquals(HttpStatusCode.BadRequest, upload(demoAssessment("a1", "2026-11-02T01:00:00Z"), key = "other").status)
    }

    @Test fun unknownOutcomeIsRejected() = app {
        val a = demoAssessment("a1", "2026-11-02T01:00:00Z")
        assertEquals(HttpStatusCode.BadRequest, upload(a.copy(trials = a.trials.map { it.copy(outcome = "NORMAL") })).status)
    }

    @Test fun reportReflectsUploadedCompletedChecks() = app {
        (1..4).forEach { upload(demoAssessment("a$it", "2026-11-0${it}T01:00:00Z", rt = 600)) }
        upload(demoAssessment("a5", "2026-11-05T01:00:00Z", rt = 500))
        upload(demoAssessment("a6", "2026-11-06T01:00:00Z", status = "INTERRUPTED"))
        val r = Json.parseToJsonElement(client.get("/v1/reports/me/checks").bodyAsText()).jsonObject
        assertEquals("TREND_AVAILABLE", r["state"]!!.jsonPrimitive.content)
        assertEquals(5, r["validCount"]!!.jsonPrimitive.int)
        assertEquals("true", r["mockVehicle"]!!.jsonPrimitive.content)
        val va = r["tasks"]!!.jsonArray.first().jsonObject
        assertEquals("VISUAL_ATTENTION", va["taskId"]!!.jsonPrimitive.content)
        assertEquals("500", va["medianRtMs"]!!.jsonPrimitive.content)
        assertEquals("SHORTER", va["direction"]!!.jsonPrimitive.content)
        assertEquals(6, r["history"]!!.jsonArray.size)
    }

    @Test fun conflictingReuploadIsRejected() = app {
        upload(demoAssessment("a1", "2026-11-01T01:00:00Z", rt = 600))
        val changed = upload(demoAssessment("a1", "2026-11-01T01:00:00Z", rt = 650))
        assertEquals(HttpStatusCode.Conflict, changed.status)
        val r = Json.parseToJsonElement(client.get("/v1/reports/me/checks").bodyAsText()).jsonObject
        assertEquals(1, r["validCount"]!!.jsonPrimitive.int, "기존 기록을 덮어쓰지 않는다")
    }

    @Test fun inconsistentUploadsAreRejected() = app {
        val unknown = demoAssessment("u1", "2026-11-01T01:00:00Z", versions = mapOf("VISUAL_ATTENTION" to 9, "GO_NO_GO" to 1, "TASK_SWITCHING" to 1))
        assertEquals(HttpStatusCode.BadRequest, upload(unknown).status)
        val base = demoAssessment("u2", "2026-11-01T01:00:00Z")
        val mismatch = base.copy(session = base.session.copy(protocolVersions = base.session.protocolVersions + ("GO_NO_GO" to 2)))
        assertEquals(HttpStatusCode.BadRequest, upload(mismatch).status, "시행 버전과 세션 버전이 다르면 받지 않는다")
        val backwards = base.copy(session = base.session.copy(assessmentId = "u3", endedAt = "2026-10-31T01:00:00Z"))
        assertEquals(HttpStatusCode.BadRequest, upload(backwards).status)
    }

    @Test fun versionChangeLimitsOnlyThatTask() = app {
        val v2 = mapOf("VISUAL_ATTENTION" to 2, "GO_NO_GO" to 2, "TASK_SWITCHING" to 2)
        (1..4).forEach { upload(demoAssessment("a$it", "2026-11-0${it}T01:00:00Z", rt = 600, versions = v2)) }
        upload(demoAssessment("a5", "2026-11-05T01:00:00Z", rt = 500, versions = v2 + ("VISUAL_ATTENTION" to 3)))
        val r = Json.parseToJsonElement(client.get("/v1/reports/me/checks").bodyAsText()).jsonObject
        val tasks = r["tasks"]!!.jsonArray.associateBy { it.jsonObject["taskId"]!!.jsonPrimitive.content }.mapValues { it.value.jsonObject }
        val va = tasks.getValue("VISUAL_ATTENTION")
        assertEquals("COLLECTING", va["state"]!!.jsonPrimitive.content)
        assertEquals("VERSION_CHANGED", va["limitReason"]!!.jsonPrimitive.content)
        assertEquals(4, va["excludedCount"]!!.jsonPrimitive.int)
        val gng = tasks.getValue("GO_NO_GO")
        assertEquals("TREND_AVAILABLE", gng["state"]!!.jsonPrimitive.content)
        assertEquals("SHORTER", gng["direction"]!!.jsonPrimitive.content)
        assertEquals(5, gng["comparableCount"]!!.jsonPrimitive.int)
    }

    @Test fun reportHasNoScoreOrDrivingFields() = app {
        upload(demoAssessment("a1", "2026-11-02T01:00:00Z"))
        val text = client.get("/v1/reports/me/checks").bodyAsText()
        listOf("score", "grade", "risk", "driving", "distance", "NORMAL").forEach { w -> assertTrue(w !in text, "'$w' in $text") }
    }

    @Test fun malformedPeriodIsBadRequest() = app {
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/reports/me/driving?period=nope").status)
    }
}

class DemoChecksTest {
    @Test fun demoSeedIsMockReproducibleAndReachesTrend() {
        val today = java.time.LocalDate.of(2026, 11, 10)
        val a = com.neulbom.drive.server.checks.DemoChecks.generate(today, REPORT_ZONE)
        assertEquals(a, com.neulbom.drive.server.checks.DemoChecks.generate(today, REPORT_ZONE))
        assertTrue(a.all { it.session.vehicleSource == "MOCK" })
        val r = com.neulbom.drive.domain.cognitive.report.ChecksReportBuilder.build(a.map { it.toRecord() })
        assertEquals(com.neulbom.drive.domain.cognitive.report.ChecksReportState.TREND_AVAILABLE, r.state)
        assertEquals(5, r.validCount)
        assertTrue(r.tasks.all { it.latest?.hiddenReason == null }, r.tasks.toString())
    }
}
