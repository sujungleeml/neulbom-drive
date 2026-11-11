package com.neulbom.drive.domain.cognitive.report

import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.TrialOutcome
import com.neulbom.drive.domain.cognitive.assessment.AssessmentStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChecksReportTest {
    private fun record(
        day: Int,
        rt: Long = 500,
        status: AssessmentStatus = AssessmentStatus.COMPLETED,
        version: Int = 1,
        device: String = "emu",
        versions: Map<TaskId, Int> = TaskId.entries.associateWith { version },
        stimulusSets: Map<TaskId, Int> = emptyMap(),
    ) = AssessmentRecord(
        assessmentId = "a$day",
        startedAt = Instant.parse("2026-10-01T01:00:00Z").plusSeconds(86_400L * day),
        status = status,
        protocolVersions = versions,
        deviceProfile = device,
        vehicleSource = "MOCK",
        trials = List(24) { trial(TaskId.VISUAL_ATTENTION, "ARROW_LEFT", rt, TrialOutcome.CORRECT) } +
            List(30) { trial(TaskId.GO_NO_GO, "GO", rt - 100, TrialOutcome.CORRECT) } +
            List(10) { trial(TaskId.GO_NO_GO, "NOGO", null, TrialOutcome.CORRECT_REJECTION) } +
            List(20) { trial(TaskId.TASK_SWITCHING, "DIGIT_4_PARITY", rt + 200, TrialOutcome.CORRECT) },
        stimulusSetVersions = stimulusSets,
    )

    @Test
    fun interruptedAssessmentsAreExcluded() {
        val r = ChecksReportBuilder.build(listOf(record(1), record(2, status = AssessmentStatus.INTERRUPTED), record(3, status = AssessmentStatus.CANCELLED)))
        assertEquals(1, r.validCount)
        assertEquals(3, r.history.size) // 기록 목록에는 남는다
        assertEquals(ChecksReportState.COLLECTING, r.state)
    }

    @Test
    fun stateFollowsValidCount() {
        assertEquals(ChecksReportState.COLLECTING, ChecksReportBuilder.build(emptyList()).state)
        assertEquals(ChecksReportState.DESCRIPTIVE, ChecksReportBuilder.build(listOf(record(1), record(2))).state)
        assertEquals(ChecksReportState.TREND_AVAILABLE, ChecksReportBuilder.build((1..4).map { record(it) }).state)
    }

    @Test
    fun versionMismatchLimitsComparison() {
        val r = ChecksReportBuilder.build((1..3).map { record(it) } + record(4, version = 2))
        assertEquals(ChecksReportState.QUALITY_LIMITED, r.state)
        assertTrue(r.tasks.all { it.direction == null })
        val d = ChecksReportBuilder.build((1..3).map { record(it) } + record(4, device = "other"))
        assertEquals(ChecksReportState.QUALITY_LIMITED, d.state)
    }

    @Test
    fun directionWordsAreObservational() {
        val shorter = ChecksReportBuilder.build((1..4).map { record(it, rt = 600) } + record(5, rt = 500))
        val va = shorter.tasks.first { it.taskId == TaskId.VISUAL_ATTENTION }
        assertEquals("SHORTER", va.direction)
        assertTrue("짧았어요" in va.text)
        val similar = ChecksReportBuilder.build((1..4).map { record(it, rt = 600) } + record(5, rt = 570))
        assertEquals("SIMILAR", similar.tasks.first().direction)
        val longer = ChecksReportBuilder.build((1..4).map { record(it, rt = 500) } + record(5, rt = 600))
        assertEquals("LONGER", longer.tasks.first().direction)
        assertNull(ChecksReportBuilder.build(listOf(record(1), record(2))).tasks.first().direction)
    }

    @Test
    fun copyHasNoForbiddenWords() {
        val forbidden = listOf(
            "치매", "진단", "위험", "확률", "적합", "판정", "정상", "저하", "질환", "환자", "면허", "점수", "등급",
            "개선", "악화", "좋아", "나빠", "MCI", "NORMAL",
        )
        val texts = ChecksCopy.all() + ChecksReportBuilder.build((1..5).map { record(it) }).let { r -> r.notices + r.tasks.map { it.text } }
        texts.forEach { t -> forbidden.forEach { w -> assertTrue(w !in t, "'$w' in $t") } }
        assertTrue(ChecksReportState.entries.none { it.name in setOf("NORMAL", "ABNORMAL", "MCI_RISK", "COGNITIVE_DECLINE") })
    }

    private val vaV3 = mapOf(TaskId.VISUAL_ATTENTION to 3, TaskId.GO_NO_GO to 2, TaskId.TASK_SWITCHING to 2)
    private val allV2 = TaskId.entries.associateWith { 2 }

    @Test
    fun onlyTheChangedTaskStopsComparing() {
        // 시각 주의만 v3이 된 점검이 들어와도 Go/No-Go·과제 전환은 계속 비교한다.
        val r = ChecksReportBuilder.build((1..4).map { record(it, rt = 600, versions = allV2) } + record(5, rt = 500, versions = vaV3))
        val va = r.tasks.first { it.taskId == TaskId.VISUAL_ATTENTION }
        assertEquals(ChecksReportState.COLLECTING, va.state)
        assertEquals(TaskLimitReason.VERSION_CHANGED, va.limitReason)
        assertEquals(ChecksCopy.NEW_VERSION, va.text)
        assertEquals(1 to 4, va.comparableCount to va.excludedCount)
        assertNull(va.direction)
        listOf(TaskId.GO_NO_GO, TaskId.TASK_SWITCHING).forEach { id ->
            val t = r.tasks.first { it.taskId == id }
            assertEquals(ChecksReportState.TREND_AVAILABLE, t.state, id.name)
            assertEquals("SHORTER", t.direction, id.name)
            assertEquals(5 to 0, t.comparableCount to t.excludedCount, id.name)
            assertNull(t.limitReason, id.name)
        }
        assertEquals(ChecksReportState.QUALITY_LIMITED, r.state)
    }

    @Test
    fun newVersionComparesOnlyWithinItself() {
        // v3 기록이 충분히 모이면 v3끼리만 비교하고, 전체 안내는 더 이상 제한으로 두지 않는다.
        val r = ChecksReportBuilder.build(
            (1..4).map { record(it, rt = 900, versions = allV2) } + (5..8).map { record(it, rt = 500, versions = vaV3) },
        )
        val va = r.tasks.first { it.taskId == TaskId.VISUAL_ATTENTION }
        assertEquals(ChecksReportState.TREND_AVAILABLE, va.state)
        assertEquals(4 to 4, va.comparableCount to va.excludedCount)
        assertEquals("SIMILAR", va.direction) // v2의 900ms와 섞였다면 SHORTER가 나왔을 것
        assertEquals(3, va.taskVersion)
        assertEquals(ChecksReportState.TREND_AVAILABLE, r.state)
    }

    @Test
    fun unknownVersionIsNotComputed() {
        val r = ChecksReportBuilder.build(listOf(record(1, versions = allV2 + (TaskId.VISUAL_ATTENTION to 99))))
        val va = r.tasks.first { it.taskId == TaskId.VISUAL_ATTENTION }
        assertEquals("UNKNOWN_VERSION", va.latest?.hiddenReason)
        assertNull(va.latest?.medianRtMs)
        assertEquals(ChecksCopy.HIDDEN, va.text)
    }

    @Test
    fun stimulusSetChangeAlsoSeparatesComparison() {
        // 과제 버전이 같아도 자극 세트가 바뀌면 섞지 않는다.
        val set1 = TaskId.entries.associateWith { 1 }
        val r = ChecksReportBuilder.build(
            (1..4).map { record(it, versions = allV2, stimulusSets = set1) } +
                record(5, versions = allV2, stimulusSets = set1 + (TaskId.GO_NO_GO to 2)),
        )
        val gng = r.tasks.first { it.taskId == TaskId.GO_NO_GO }
        assertEquals(TaskLimitReason.VERSION_CHANGED, gng.limitReason)
        assertEquals(1 to 4, gng.comparableCount to gng.excludedCount)
        assertEquals(5, r.tasks.first { it.taskId == TaskId.VISUAL_ATTENTION }.comparableCount)
    }

    @Test
    fun reasonFollowsTheMostRecentChange() {
        // 오래전 버전 변경 뒤 기기가 바뀌었으면, 지금 비교가 끊긴 이유는 기기 변경이다.
        val r = ChecksReportBuilder.build(
            listOf(record(1, versions = allV2)) + (2..4).map { record(it, versions = vaV3) } + record(5, versions = vaV3, device = "car"),
        )
        val va = r.tasks.first { it.taskId == TaskId.VISUAL_ATTENTION }
        assertEquals(TaskLimitReason.DEVICE_CHANGED, va.limitReason)
        assertEquals(ChecksCopy.NEW_DEVICE, va.text)
    }
}
