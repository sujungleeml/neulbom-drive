package com.neulbom.drive.domain.driving

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    /** 변화 문장은 실제 두 값과 차이를 그대로 말하고, 비교할 수 없으면 만들지 않는다. */
    @Test fun changeTextUsesActualValuesOnly() {
        val decel = MetricLine("harsh_decel_per_100km", 2.0, "회/100km", Direction.MORE, "METRIC_HARSH_DECEL", previous = 1.2)
        assertEquals("100 km당 급감속 기록: 지난달 1.2번, 이번 달 2.0번으로 0.8번 늘었어요.", DrivingCopy.change(decel))
        val night = MetricLine("night_share", 0.10, "%", Direction.LESS, "METRIC_NIGHT_SHARE", previous = 0.18)
        assertEquals("야간 주행 비율: 지난달 18%, 이번 달 10%로 8%p 줄었어요.", DrivingCopy.change(night))
        val dist = MetricLine("distance", 410.0, "km", Direction.SIMILAR, "METRIC_DISTANCE", previous = 400.0)
        assertEquals("주행 거리: 지난달 400.0 km, 이번 달 410.0 km로 비슷해요.", DrivingCopy.change(dist))
        assertNull(DrivingCopy.change(decel.copy(direction = null)))
        assertNull(DrivingCopy.change(decel.copy(previous = null)))
        assertNull(DrivingCopy.change(MetricLine("duration", 1.0, "s", Direction.MORE, "METRIC_DURATION", previous = 2.0)))
        listOf(decel, night, dist).mapNotNull { DrivingCopy.change(it) }.forEach { t -> forbidden.forEach { w -> assertTrue(w !in t, "금지 문구 '$w': $t") } }
    }

    /** 비교할 수 없는 달(기록 부족)에는 지난달 값과 변화 문장을 만들지 않는다. */
    @Test fun noPreviousWhenNotComparable() {
        val ym = java.time.YearMonth.of(2026, 10)
        fun sum(days: Int, km: Double) = PeriodSummary(ym, "a", com.neulbom.drive.core.model.DataOrigin.MOCK, days, days, 3600.0 * days, km, 1.0, 0.1, 0.0, emptySet(), emptyMap())
        val r = DrivingReportBuilder.build(sum(10, 300.0), sum(3, 40.0))
        assertEquals(ReportState.DESCRIPTIVE, r.state)
        assertTrue(r.lines.all { it.previous == null && DrivingCopy.change(it) == null })
    }
}
