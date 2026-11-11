package com.neulbom.drive.ui.layout

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Visual Layout Studio 배치 파일 ↔ 앱 IdleLayout 매핑. */
@RunWith(RobolectricTestRunner::class)
class IdleLayoutTest {
    private val repo = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    /** 편집기의 "기본값 복원" 파일은 코드 기본값(편집 전 화면)과 같다. 앱 배치 파일은 PO가 편집하는 값이라 읽히기만 하면 된다. */
    @Test fun studioDefaultsMatchCodeAndBundledFilesParse() {
        val d = ScreenLayouts.parseIdle(File(repo, "tools/layout-studio/defaults/idle.json").readText())
        assertEquals(IdleLayout(), d.idleDay)
        assertEquals(IdleLayout(), d.idleNight)
        ScreenLayouts.parseIdle(File(repo, "app/src/main/assets/layout/idle.json").readText())
    }

    @Test fun missingKeysUseDefaultsAndOutOfRangeIsClamped() {
        val l = IdleLayout.parse(JSONObject("""{"brand":{"widthFraction":0.9,"missionFontSize":10,"imageFit":"weird"},"main":{"ctaHeight":20,"actionsPlacement":"afterContent","actionsBottomOffset":900}}"""))
        assertEquals(400f, l.main.actionsBottomOffset)
        assertEquals(0.6f, l.brand.widthFraction)
        assertEquals(IdleLayout.MIN_MISSION, l.brand.missionFontSize)
        assertEquals("crop", l.brand.imageFit)
        assertEquals(IdleLayout.MIN_TOUCH, l.main.ctaHeight)
        assertEquals("afterContent", l.main.actionsPlacement)
        assertEquals(IdleLayout().main.titleFontSize, l.main.titleFontSize)
    }

    @Test fun nightFallsBackToDayAndRevisionIsRead() {
        val s = ScreenLayouts.parseIdle("""{"schemaVersion":1,"revision":7,"day":{"main":{"gap":30}}}""")
        assertEquals(7L, s.revision)
        assertEquals(30f, s.idle(night = true).main.gap)
    }

    @Test fun releaseBuildHasNoStudioUrl() {
        val gradle = File(repo, "app/build.gradle.kts").readText()
        val release = gradle.substringAfter("release {").substringBefore("}")
        assertTrue(release.contains("LAYOUT_STUDIO_URL\", \"\\\"\\\"\""))
    }

    /** 나머지 화면도 편집기 기본값 = 코드 기본값, 앱 파일은 모두 읽힌다. */
    @Test fun pageDefaultsMatchCodeDefaults() {
        PageLayout.DEFAULTS.forEach { (screen, def) ->
            val (day, night) = ScreenLayouts.parsePage(screen, File(repo, "tools/layout-studio/defaults/$screen.json").readText())
            assertEquals(screen, def, day)
            assertEquals(screen, def, night)
            ScreenLayouts.parsePage(screen, File(repo, "app/src/main/assets/layout/$screen.json").readText())
        }
    }

    @Test fun parseAllCombinesScreensAndSumsRevisions() {
        val s = ScreenLayouts.parseAll(mapOf(
            "idle" to """{"schemaVersion":1,"revision":2,"day":{}}""",
            "offer" to """{"schemaVersion":1,"revision":3,"day":{"ctaHeight":10,"verticalBias":-0.5},"night":{"ctaHeight":150}}""",
        ))
        assertEquals(5L, s.revision)
        assertEquals(IdleLayout.MIN_TOUCH, s.page("offer", night = false).ctaHeight)
        assertEquals(-0.5f, s.page("offer", night = false).verticalBias)
        assertEquals(150f, s.page("offer", night = true).ctaHeight)
        assertEquals(PageLayout.default("guide"), s.page("guide", night = false))
    }
}
