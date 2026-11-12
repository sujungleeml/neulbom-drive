package com.neulbom.drive.server

import com.neulbom.drive.server.driving.*
import com.neulbom.drive.server.checks.*
import com.neulbom.drive.core.time.FakeClock
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 웹은 조회·설정 전용이다(명세 v0.6 01 W-07). 점검 실행·자극 제시·반응 측정 코드가 없어야 한다. */
class WebAssetsTest {
    private val webDir = File("src/main/resources/web")
    private val forbidden = listOf(
        "인지 저하", "치매", "진단", "위험", "정상", "인증", "점수", "등급", "면허", "반납", "급발진", "오조작",
        "부적합", "정밀검사", "스크리닝", "조기 발견", "경고", "사고",
    )

    @Test fun indexServedAndHasNoAssessmentUi() = testApplication {
        application { neulbomModule(InMemoryDrivingStore(), FakeClock()) }
        val res = client.get("/")
        assertEquals(HttpStatusCode.OK, res.status)
        val html = res.bodyAsText()
        assertTrue("나의 드라이빙" in html)
        val all = webDir.listFiles()!!.filter { it.isFile && it.extension in listOf("html", "js", "css", "json") }.joinToString("\n") { it.readText() }
        listOf("performance.now", "점검 시작", "requestAnimationFrame", "reaction", "stimulus").forEach {
            assertTrue(it !in all, "웹에 점검 실행 흔적: $it")
        }
    }

    @Test fun webCopyHasNoForbiddenWords() {
        val files = webDir.listFiles()!!.filter { it.extension in listOf("html", "js", "css") }
        assertTrue(files.size >= 3)
        files.forEach { f -> forbidden.forEach { w -> assertTrue(w !in f.readText(), "${f.name}: '$w'") } }
    }

    @Test fun allPanelsExistAndShareViewIsReadOnly() {
        val index = webDir.resolve("index.html").readText()
        listOf("driving", "checks", "journey", "settings", "share", "habits", "pattern").forEach { id ->
            assertTrue("data-tab=\"$id\"" in index && "id=\"$id\"" in index, "탭/패널 없음: $id")
        }
        val share = webDir.resolve("share.html").readText() + webDir.resolve("share.js").readText()
        listOf("PUT", "DELETE", "POST", "delete-all", "share-create", "consents").forEach { assertTrue(it !in share, "공유 화면에 조작: $it") }
        assertTrue("innerHTML" !in webDir.listFiles()!!.filter { it.extension == "js" }.joinToString { it.readText() }, "서버 문장은 textContent로만")
    }

    /** 패턴 지도(시연): 합성 데이터 표시, 분포는 분위수만, 군집·유형 이름 없음, 가족 공유 범위에 없음. */
    @Test fun patternMapIsMarkedSyntheticAndHasNoTypes() {
        val map = kotlinx.serialization.json.Json.parseToJsonElement(webDir.resolve("pattern-map.json").readText()).jsonObject
        assertEquals("true", map["synthetic"]!!.jsonPrimitive.content)
        assertTrue("합성" in map["notice"]!!.jsonPrimitive.content)
        map["metrics"]!!.jsonArray.forEach { m ->
            m.jsonObject["bands"]!!.jsonArray.forEach { b -> assertTrue(b.jsonObject.keys == setOf("band", "n", "q"), "분위수 외 값: ${b.jsonObject.keys}") }
        }
        val points = kotlinx.serialization.json.Json.parseToJsonElement(webDir.resolve("pattern-points.json").readText()).jsonObject
        assertEquals("true", points["synthetic"]!!.jsonPrimitive.content)
        assertTrue(points["drivers"]!!.jsonArray.all { "driver_id" !in it.jsonObject && "cluster" !in it.jsonObject })
        // 시연용 주행 군집(PO 결정 2026-11-12): 주행 공간에만, 가정이라는 안내와 함께, 줄 세우는 이름 없이.
        val groups = points["groups"]!!.jsonObject
        assertEquals("driving", groups["space"]!!.jsonPrimitive.content)
        val gNotice = groups["notice"]!!.jsonPrimitive.content
        assertTrue("시연" in gNotice && "가정" in gNotice && "좋고 나쁨" in gNotice)
        val items = groups["items"]!!.jsonArray
        assertTrue(items.size in 2..6)
        assertEquals(items.size, items.map { it.jsonObject["color"]!!.jsonPrimitive.content }.toSet().size, "군집 색이 겹침")
        val groupText = items.joinToString("\n") { it.jsonObject["title"]!!.jsonPrimitive.content + it.jsonObject["desc"]!!.jsonPrimitive.content }
        listOf("좋은", "나쁜", "우수", "주의", "상위", "하위", "안전한", "능력").forEach { assertTrue(it !in groupText, "군집 이름에 평가 표현: $it") }
        val ids = items.map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
        assertTrue(points["drivers"]!!.jsonArray.all { d -> d.jsonObject["group"]!!.let { it is kotlinx.serialization.json.JsonNull || it.jsonPrimitive.content in ids } })
        val ui = listOf("pattern.js", "pattern3d.js").joinToString("\n") { webDir.resolve(it).readText() } + webDir.resolve("index.html").readText()
        listOf("유형 A", "A형", "군집 1", "hyperplane").forEach { assertTrue(it !in ui, "패턴 지도에 유형·경계 표현: $it") }
        assertTrue("시연용 합성 데이터" in webDir.resolve("index.html").readText())
        assertTrue("PATTERN" !in webDir.resolve("share.js").readText())
    }

    /** W-05 건강한 운전 습관 정보: 일반 정보, 검수 전 표시, 경고·훈계·나이 낙인 없음(명세 09 §1·3). */
    @Test fun habitsContentIsGeneralDraftAndRespectful() {
        val text = webDir.resolve("habits.json").readText()
        val json = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
        assertEquals("DRAFT", json["reviewStatus"]!!.jsonPrimitive.content)
        assertTrue(json["sections"]!!.jsonArray.size >= 4)
        listOf(
            "위험", "사고", "경고", "정상", "치매", "진단", "인지 저하", "면허", "반납", "적합", "등급", "점수",
            "고령", "어르신", "노인", "나이가 들면", "하지 마세요", "해야 합니다", "금지",
        ).forEach { w -> assertTrue(w !in text, "habits.json: '$w'") }
        val index = webDir.resolve("index.html").readText()
        assertTrue("data-tab=\"habits\"" in index && "id=\"habits\"" in index)
    }
}
