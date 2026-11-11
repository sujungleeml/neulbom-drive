package com.neulbom.drive.domain.driving

import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.core.model.SignalId
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeriodSummaryTest {
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)

    @Test fun noSessionsYieldsCollectingWithReason() {
        val summary = PeriodSummarizer.summarize(nov, emptyList())
        assertEquals(0, summary.drivingDays)
        assertNull(summary.nightShare)
        val report = DrivingReportBuilder.build(summary, null)
        assertEquals(ReportState.COLLECTING, report.state)
        assertTrue("COLLECTING" in report.notices)
    }

    @Test fun decelPer100KmOnlyFromFiftyKm() {
        val small = PeriodSummarizer.summarize(nov, listOf(item("a", "2026-11-02T01:00:00Z", distanceKm = 40.0, harsh = 2)))
        assertNull(small.harshDecelPer100Km)
        assertEquals("UNAVAILABLE_DECEL_DISTANCE", small.unavailable["harsh_decel_per_100km"])
        val enough = PeriodSummarizer.summarize(nov, listOf(item("a", "2026-11-02T01:00:00Z", distanceKm = 60.0, harsh = 3)))
        assertEquals(5.0, enough.harshDecelPer100Km!!, 1e-9)
    }

    @Test fun noSpeedSignalMakesDistanceAndDecelUnavailable() {
        val s = PeriodSummarizer.summarize(nov, listOf(item("a", "2026-11-02T01:00:00Z", distanceKm = null, harsh = null, signals = setOf(SignalId.VEHICLE_STATE))))
        assertNull(s.distanceKm)
        assertEquals("UNAVAILABLE_SPEED", s.unavailable["distance"])
        assertEquals("UNAVAILABLE_DECEL_SIGNAL", s.unavailable["harsh_decel_per_100km"])
    }

    @Test fun sessionCrossingMonthBelongsToStartMonth() {
        val crossing = item("x", "2026-10-31T14:50:00Z") // 23:50 KST, 10월 31일
        assertEquals(1, PeriodSummarizer.summarize(oct, listOf(crossing)).sessions)
        assertEquals(0, PeriodSummarizer.summarize(nov, listOf(crossing)).sessions)
    }

    @Test fun excludedAndInvalidSessionsAreLeftOut() {
        val s = PeriodSummarizer.summarize(nov, listOf(
            item("a", "2026-11-02T01:00:00Z"),
            item("b", "2026-11-03T01:00:00Z", quality = SessionQuality.EXCLUDED),
            item("c", "2026-11-04T01:00:00Z", valid = false),
            item("d", "2026-11-05T01:00:00Z", quality = SessionQuality.LIMITED),
        ))
        assertEquals(2, s.sessions)
        assertEquals(0.5, s.limitedShare, 1e-9)
    }

    @Test fun trendRequiresEightDaysAndHundredKmBothMonths() {
        val prev = PeriodSummarizer.summarize(oct, month("2026-10", 7, 20.0))
        val cur = PeriodSummarizer.summarize(nov, month("2026-11", 10, 20.0))
        val report = DrivingReportBuilder.build(cur, prev)
        assertEquals(ReportState.DESCRIPTIVE, report.state)
        assertTrue(report.lines.all { it.direction == null })
    }

    @Test fun trendRequiresSameSignalSetAndVersion() {
        val prev = PeriodSummarizer.summarize(oct, month("2026-10", 10, 20.0, signals = setOf(SignalId.VEHICLE_STATE, SignalId.SPEED)))
        val cur = PeriodSummarizer.summarize(nov, month("2026-11", 10, 20.0))
        assertEquals(ReportState.QUALITY_LIMITED, DrivingReportBuilder.build(cur, prev).state)
        val oldVersion = PeriodSummarizer.summarize(oct, month("2026-10", 10, 20.0, version = "drv-0.0.9"))
        assertEquals(ReportState.QUALITY_LIMITED, DrivingReportBuilder.build(cur, oldVersion).state)
    }

    @Test fun similarWithinTenPercent() {
        val prev = PeriodSummarizer.summarize(oct, month("2026-10", 10, 20.0, durationS = 1000.0))
        val similar = DrivingReportBuilder.build(PeriodSummarizer.summarize(nov, month("2026-11", 10, 20.0, durationS = 1050.0)), prev)
        assertEquals(ReportState.TREND_AVAILABLE, similar.state)
        assertEquals(Direction.SIMILAR, similar.lines.first { it.key == "duration" }.direction)
        val more = DrivingReportBuilder.build(PeriodSummarizer.summarize(nov, month("2026-11", 10, 20.0, durationS = 1200.0)), prev)
        assertEquals(Direction.MORE, more.lines.first { it.key == "duration" }.direction)
    }

    @Test fun reportHasFiveMetricLinesInOrder() {
        val report = DrivingReportBuilder.build(PeriodSummarizer.summarize(nov, month("2026-11", 10, 20.0)), null)
        assertEquals(listOf("driving_days", "duration", "distance", "harsh_decel_per_100km", "night_share"), report.lines.map { it.key })
    }

    @Test fun mockOriginAddsNoticeAndExternalConditionsAlways() {
        val report = DrivingReportBuilder.build(PeriodSummarizer.summarize(nov, month("2026-11", 3, 20.0)), null)
        assertEquals(DataOrigin.MOCK, report.origin)
        assertTrue("MOCK_DATA" in report.notices)
        assertTrue("EXTERNAL_CONDITIONS_NOT_ADJUSTED" in report.notices)
        val real = DrivingReportBuilder.build(PeriodSummarizer.summarize(nov, listOf(item("r", "2026-11-02T01:00:00Z", origin = DataOrigin.REAL))), null)
        assertTrue("MOCK_DATA" !in real.notices)
    }
}
