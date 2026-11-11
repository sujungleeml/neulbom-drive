package com.neulbom.drive.domain.driving

import kotlin.test.Test
import kotlin.test.assertTrue

/** 리포트 문구는 템플릿만 쓰고, 어떤 템플릿에도 금지 문구가 없어야 한다(명세 v0.6 02 §5, 05 §4, 09 §4). */
class DrivingCopyTest {
    private val forbidden = listOf(
        "인지 저하", "치매", "진단", "위험", "정상", "인증", "점수", "등급", "면허", "반납", "급발진", "오조작",
        "부적합", "적합", "정밀검사", "스크리닝", "조기 발견", "경고", "사고", "운전을 줄이", "운전 능력",
    )

    @Test fun everyTemplateRendersWithoutForbiddenWords() {
        val sample = MetricLine("duration", 5400.0, "s", Direction.MORE, DrivingCopy.METRIC_TEMPLATE.getValue("duration"), extra = 3.0)
        val rendered = DrivingCopy.ALL_TEMPLATE_IDS.map { id -> DrivingCopy.render(id, sample.copy(templateId = id)) }
        assertTrue(rendered.size >= 15)
        rendered.forEach { text ->
            assertTrue(text.isNotBlank())
            forbidden.forEach { w -> assertTrue(w !in text, "금지 문구 '$w': $text") }
        }
    }
}
