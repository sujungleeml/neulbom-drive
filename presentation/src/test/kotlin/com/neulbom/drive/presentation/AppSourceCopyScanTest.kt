package com.neulbom.drive.presentation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 앱·서버·주행 도메인 코드에 직접 쓴 문자열 리터럴까지 금칙어를 검사한다.
 * (Android 빌드 없이 JVM에서 실행 가능한 안전장치)
 */
class AppSourceCopyScanTest {
    private val forbidden = listOf(
        "치매", "진단", "위험", "확률", "적합", "판정", "정상", "저하", "질환", "환자", "면허", "반납", "점수",
        "정밀검사", "스크리닝", "조기 발견", "인증", "급발진", "오조작", "등급",
        "인지 저하", "운전 부적합", "NORMAL", "ABNORMAL", "MCI", "COGNITIVE_DECLINE",
    )
    private val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")

    @Test
    fun appStringLiteralsContainNoDiagnosticLanguage() {
        val repo = File(System.getProperty("user.dir")).parentFile
        val roots = listOf("app/src", "server/src/main", "domain/driving/src/main", "data/sync/src/main").map(repo::resolve)
        val files = roots.flatMap { r -> r.walkTopDown().filter { it.extension == "kt" }.toList() }
        assertTrue(files.size >= 20, "소스를 찾지 못함: $roots")
        val violations = files.flatMap { file ->
            file.readLines().flatMapIndexed { i, line ->
                literal.findAll(line).map { it.groupValues[1] }
                    .flatMap { text -> forbidden.filter { it in text }.map { word -> "${file.name}:${i + 1} '$word' in \"$text\"" } }
                    .toList()
            }
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }
}
