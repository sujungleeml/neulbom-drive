package com.neulbom.drive.server

import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.server.checks.CheckStore
import com.neulbom.drive.server.checks.InMemoryCheckStore
import com.neulbom.drive.server.driving.InMemoryDrivingStore
import com.neulbom.drive.server.settings.ConsentEventDto
import com.neulbom.drive.server.settings.InMemorySettingsStore
import com.neulbom.drive.server.suggest.DemoSuggestionFixtures
import com.neulbom.drive.server.suggest.SuggestionCopy
import com.neulbom.drive.server.suggest.SuggestionEngine
import com.neulbom.drive.server.suggest.SuggestionEngine.DrivePeriod
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 「이번 달의 기록과 작은 제안」(시연 규칙 R01~R10). */
class SuggestionTest {
    private fun run(s: DemoSuggestionFixtures.Scenario) =
        SuggestionEngine.evaluate(SuggestionEngine.activityChange(s.sessions), SuggestionEngine.drivingChange(s.previous, s.current))

    @Test fun everyScenarioSelectsItsExpectedRules() {
        assertEquals(10, DemoSuggestionFixtures.ALL.size)
        assertEquals((1..10).map { "R%02d".format(it) }, DemoSuggestionFixtures.ALL.map { it.id })
        DemoSuggestionFixtures.ALL.forEach { s ->
            val r = run(s)
            assertEquals(s.expected, r.matched, "${s.id}: ${r.activity} / ${r.driving}")
            assertEquals(s.id, r.primary)
        }
    }

    @Test fun fixturesAreDeterministic() {
        DemoSuggestionFixtures.ALL.forEach { s -> assertEquals(run(s), run(s)) }
    }

    @Test fun noSuggestionWithoutBaseline() {
        val s = DemoSuggestionFixtures.get("R01")!!
        val few = s.sessions.take(3)
        val r = SuggestionEngine.evaluate(SuggestionEngine.activityChange(few), SuggestionEngine.drivingChange(s.previous, s.current))
        assertTrue(r.matched.isEmpty())
        // 기준선 4번만 있고 최근 점검이 없어도 만들지 않는다
        val baseOnly = SuggestionEngine.activityChange(s.sessions.take(4))
        assertTrue(baseOnly.baselineReady && baseOnly.rtPct == null)
        assertTrue(SuggestionEngine.evaluate(baseOnly, SuggestionEngine.drivingChange(s.previous, s.current)).matched.isEmpty())
    }

    @Test fun noSuggestionWhenDrivingNotComparable() {
        val s = DemoSuggestionFixtures.get("R01")!!
        val a = SuggestionEngine.activityChange(s.sessions)
        assertTrue(SuggestionEngine.evaluate(a, SuggestionEngine.drivingChange(null, s.current)).matched.isEmpty())
        val short = s.current.copy(drivingDays = 3)
        assertTrue(SuggestionEngine.evaluate(a, SuggestionEngine.drivingChange(s.previous, short)).matched.isEmpty())
    }

    @Test fun zeroKmAndZeroDurationAreHandled() {
        val s = DemoSuggestionFixtures.get("R01")!!
        val zero = s.current.copy(km = 0.0, harshDecel = 3, weeklyKm = listOf(0.0, 0.0, 0.0, 0.0))
        val d = SuggestionEngine.drivingChange(s.previous, zero)
        assertFalse(d.comparable)
        val noTime = s.current.copy(durationS = 0.0, nightS = 0.0)
        assertFalse(SuggestionEngine.drivingChange(s.previous, noTime).comparable)
        // 거리는 충분하지만 급감속 계산 거리(50 km) 미만은 없으므로, 분모는 늘 0보다 크다
        val ok = SuggestionEngine.drivingChange(s.previous, s.current)
        assertTrue(ok.decelPer100!!.toList().all { it.isFinite() })
    }

    @Test fun differentPeriodLengthsUsePerDayAverages() {
        // 이번 기간이 12일뿐이어도 하루 평균 주행 시간이 같으면 '주행 시간 증가'로 보지 않는다
        val prev = DrivePeriod("10월", 31, 14, 1000.0, 50.0 * 60 * 31, 15, 0.15 * 50 * 60 * 31, listOf(250.0, 250.0, 250.0, 250.0))
        val cur = DrivePeriod("11월 1~12일", 12, 8, 400.0, 50.0 * 60 * 12, 6, 0.15 * 50 * 60 * 12, listOf(200.0, 200.0))
        val d = SuggestionEngine.drivingChange(prev, cur)
        assertTrue(d.comparable)
        assertEquals(0.0, d.durationPct!!, 1e-9)
        assertEquals(0.0, d.decelDelta!!, 1e-9)
    }

    @Test fun priorityAndConflicts() {
        assertEquals("R04", SuggestionEngine.PRIORITY.first())
        // R01 시나리오는 R02도 맞지만 R01이 대표이고 R02는 펼쳐 보기로 간다
        assertEquals(listOf("R01", "R02"), run(DemoSuggestionFixtures.get("R01")!!).matched)
        // '좋아졌어요'와 '평소와 비슷해요'는 다른 제안과 섞이지 않는다
        DemoSuggestionFixtures.ALL.map { run(it).matched }.forEach { m ->
            if ("R08" in m) assertEquals(listOf("R08"), m)
            if (m.any { it in setOf("R06", "R09") }) assertTrue(m.none { it in setOf("R01", "R02", "R03", "R04", "R05", "R07", "R10") })
        }
    }

    @Test fun demoRulesetIsMarkedUnvalidated() {
        assertFalse(SuggestionEngine.DEMO_RULESET.validated)
        assertTrue(SuggestionEngine.DEMO_RULESET.id.startsWith("demo-"))
    }

    @Test fun copyHasNoJudgementWords() {
        val forbidden = listOf(
            "사고", "위험", "저하", "부족", "중단", "못합니다", "못해요", "진단", "치매", "정상", "점수", "등급", "면허", "반납", "경고",
            "운전 능력", "인지기능", "그만",
        )
        val text = SuggestionCopy.MESSAGES.values.joinToString("\n") { it.title + it.together + it.action } +
            SuggestionCopy.ACTIVITY_BASIS + SuggestionCopy.NOT_ENOUGH + SuggestionCopy.NO_MATCH
        forbidden.forEach { w -> assertTrue(w !in text, "제안 문구에 '$w'") }
        assertEquals(
            "최근에는 주의와 반응 활동에서 응답하는 데 시간이 조금 더 걸렸고, 급감속 기록도 늘었어요.",
            SuggestionCopy.MESSAGES.getValue("R01").together,
        )
    }

    // ---- API: 동의, 시연과 실제 기록의 분리 ----

    private fun consent(s: InMemorySettingsStore, type: String, granted: Boolean, at: String) = s.addConsentEvent(ConsentEventDto(type, granted, at))

    @Test fun realPathNeedsBothConsentsAndNeverProducesSuggestions() = testApplication {
        val settings = InMemorySettingsStore()
        application { neulbomModule(InMemoryDrivingStore(), FakeClock(), settings = settings, suggestionsDemo = true) }
        fun state(body: String) = Json.parseToJsonElement(body).jsonObject["state"]!!.jsonPrimitive.content
        assertEquals("CONSENT_REQUIRED", state(client.get("/v1/reports/me/suggestions").bodyAsText()))
        consent(settings, "DRIVING_RECORD", true, "2026-11-01T00:00:00Z")
        consent(settings, "COGNITIVE_CHECK", true, "2026-11-01T00:00:00Z")
        val ok = Json.parseToJsonElement(client.get("/v1/reports/me/suggestions").bodyAsText()).jsonObject
        assertEquals("NOT_VALIDATED", ok["state"]!!.jsonPrimitive.content)
        assertTrue(ok["primary"] == null || ok["primary"].toString() == "null")
        // 동의를 거두면 다시 막힌다
        consent(settings, "DRIVING_RECORD", false, "2026-11-02T00:00:00Z")
        assertEquals("CONSENT_REQUIRED", state(client.get("/v1/reports/me/suggestions").bodyAsText()))
    }

    /** 시연 경로는 저장소를 읽지 않는다: 읽으면 터지는 저장소를 넣어도 동작해야 한다. */
    @Test fun demoPathUsesOnlySyntheticRecords() = testApplication {
        val exploding = object : CheckStore by InMemoryCheckStore() {
            override fun all() = error("시연 경로가 실제 점검 기록을 읽음")
        }
        val driving = object : com.neulbom.drive.server.driving.DrivingStore by InMemoryDrivingStore() {
            override fun all() = error("시연 경로가 실제 주행 기록을 읽음")
        }
        application { neulbomModule(driving, FakeClock(), checks = exploding, suggestionsDemo = true) }
        DemoSuggestionFixtures.ALL.forEach { s ->
            val res = client.get("/v1/demo/suggestions?scenario=${s.id}")
            assertEquals(HttpStatusCode.OK, res.status)
            val o = Json.parseToJsonElement(res.bodyAsText()).jsonObject
            assertEquals("true", o["synthetic"]!!.jsonPrimitive.content)
            assertEquals("false", o["rulesValidated"]!!.jsonPrimitive.content)
            assertEquals(s.id, o["primary"]!!.jsonObject["id"]!!.jsonPrimitive.content)
            assertTrue(o["activityEvidence"]!!.jsonArray.isNotEmpty() && o["drivingEvidence"]!!.jsonArray.isNotEmpty())
        }
    }

    @Test fun demoPathIsClosedOnRealServers() = testApplication {
        application { neulbomModule(InMemoryDrivingStore(), FakeClock()) }
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/demo/suggestions?scenario=R01").status)
    }

    /** 시스템 다크 모드에서 브라우저가 화면 색을 강제로 뒤집지 않도록 색 구성을 직접 밝힌다. */
    @Test fun pagesDeclareColorSchemeToAvoidForcedInversion() {
        val web = File("src/main/resources/web")
        listOf("index.html", "share.html").forEach { f ->
            assertTrue("name=\"color-scheme\" content=\"light dark\"" in web.resolve(f).readText(), "$f: color-scheme meta 없음")
        }
        assertTrue("color-scheme: light dark" in web.resolve("app.css").readText())
    }

    @Test fun unknownScenarioIsRejected() = testApplication {
        application { neulbomModule(InMemoryDrivingStore(), FakeClock(), suggestionsDemo = true) }
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/demo/suggestions?scenario=R99").status)
        assertNull(DemoSuggestionFixtures.get("R99"))
    }
}
