package com.neulbom.drive.server

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 명세 v0.6 테스트 22·23 / D17: 주행 습관 축과 주의·반응 점검 축을 하나의 값으로 합치지 않고, 위치·경로 필드가 없다.
 *  - 주행 축 소스는 점검 축 식별자를 쓰지 않고, 점검 축 소스는 주행 축 식별자를 쓰지 않는다.
 *  - 어디에도 위험·안전 점수, 통합 점수, 등급 식별자가 없다.
 */
class NoCombinedScoreTest {
    private val repo = File(System.getProperty("user.dir")).parentFile
    private fun kt(vararg roots: String) = roots.flatMap { repo.resolve(it).walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }
    private fun code(f: File) = f.readLines().map { it.trimStart() }
        .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("package ") || it.startsWith("import ") }
        .joinToString("\n") { it.replace(literal, "\"\"") } // 식별자만 본다(문자열 값 예: 중단 사유 "DRIVING"은 제외)
    private val literal = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")

    private val overview = "server/src/main/kotlin/com/neulbom/drive/server/overview"
    private val drivingAxis = kt("server/src/main/kotlin/com/neulbom/drive/server/driving", "domain/driving/src/main") +
        repo.resolve("data/sync/src/main/kotlin/com/neulbom/drive/data/sync/DrivingUploader.kt") +
        repo.resolve("$overview/DrivingLane.kt")
    private val checkAxis = kt("server/src/main/kotlin/com/neulbom/drive/server/checks", "domain/cognitive/src/main/kotlin/com/neulbom/drive/domain/cognitive/report") +
        repo.resolve("$overview/ChecksLane.kt")

    @Test fun drivingAxisNeverReferencesCheckAxis() {
        assertTrue(drivingAxis.size >= 8)
        val banned = Regex("(?i)\\b\\w*(cognitive|assessment|reactiontime|checks?report|trial)\\w*\\b")
        drivingAxis.forEach { f -> assertTrue(banned.find(code(f)) == null, "${f.name}: ${banned.find(code(f))?.value}") }
    }

    @Test fun checkAxisNeverReferencesDrivingAxis() {
        assertTrue(checkAxis.size >= 4)
        val banned = Regex("(?i)\\b\\w*(driving|drive|trip|harsh|decel|speed)\\w*\\b")
        checkAxis.forEach { f -> assertTrue(banned.find(code(f)) == null, "${f.name}: ${banned.find(code(f))?.value}") }
    }

    @Test fun noScoreOrGradeIdentifiersAnywhere() {
        val all = kt("server/src/main", "domain", "data/sync/src/main", "app/src/main", "presentation/src/main")
        val banned = Regex("(?i)\\b\\w*(riskscore|safetyscore|combinedscore|totalscore|overallscore|grade|riskLevel)\\w*\\b")
        all.forEach { f -> assertTrue(banned.find(code(f)) == null, "${f.name}: ${banned.find(code(f))?.value}") }
    }

    @Test fun noLocationFieldsInDrivingTypes() {
        val banned = Regex("\\b(val|var)\\s+(lat|lng|lon|latitude|longitude|location|route|destination|address|gps)\\w*\\s*:", RegexOption.IGNORE_CASE)
        (drivingAxis + kt("core/src/main")).forEach { f -> assertTrue(banned.find(f.readText()) == null, "${f.name}: ${banned.find(f.readText())?.value}") }
    }
}
