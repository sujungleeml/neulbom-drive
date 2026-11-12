package com.neulbom.drive.presentation

import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.domain.cognitive.DenialReason
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.assessment.SaveState
import com.neulbom.drive.domain.consent.ConsentType
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 마스터 지시문 7: 건강 상태 진단 문구를 생성하지 않는다.
 * 앱이 보여주는 모든 문구에서 진단·위험도·운전 적합성·면허 관련 표현을 금지한다.
 */
class UiTextTest {
    private val forbidden = listOf(
        "치매", "진단", "위험도", "위험 점수", "확률", "적합", "판정", "비정상", "정상 범위",
        "저하", "질환", "환자", "면허", "반납", "점수", "검사 결과 이상", "경도인지",
        "위험", "정밀검사", "스크리닝", "조기 발견", "인증", "급발진", "오조작", "등급",
        "정상", "인지 저하", "운전 부적합", "NORMAL", "ABNORMAL", "MCI", "COGNITIVE_DECLINE",
    )

    private fun allCopy(): List<String> = buildList {
        UiText::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java && it.name != "DISCLAIMER" }
            .forEach { it.isAccessible = true; add(it.get(null) as String) }
        SafetyState.entries.forEach { add(UiText.safety(it)) }
        ConsentType.entries.forEach {
            add(UiText.consentTitle(it)); add(UiText.consentDetail(it, 180)); add(UiText.withoutConsent(it))
        }
        DenialReason.entries.forEach { add(it.message) }
        TaskId.entries.forEach { add(UiText.taskLabel(it)) }
        SaveState.entries.forEach { add(UiText.completion(it)) }
        addAll(UiText.GUIDE_POINTS)
    }

    @Test
    fun noDiagnosticOrRiskLanguageInUserFacingCopy() {
        val copy = allCopy()
        assertTrue(copy.size > 15)
        copy.forEach { text ->
            forbidden.forEach { word -> assertTrue(word !in text, "금칙어 '$word' 포함: $text") }
        }
    }

    @Test
    fun disclaimerStatesThisIsNotADiagnosis() {
        assertTrue("진단 도구가 아닙니다" in UiText.DISCLAIMER)
    }
}
