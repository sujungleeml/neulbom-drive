package com.neulbom.drive.domain.cognitive.report

import com.neulbom.drive.domain.cognitive.ProvisionalProtocols
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.TrialOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal fun trial(task: TaskId, type: String, rt: Long?, outcome: TrialOutcome, practice: Boolean = false) =
    TrialRecord(task, if (practice) "PRACTICE" else "MAIN", type, rt, outcome, practice)

class CheckMetricsTest {
    private val va = ProvisionalProtocols.VISUAL_ATTENTION

    @Test
    fun medianRtUsesCorrectMainTrialsOnly() {
        val trials = List(16) { trial(TaskId.VISUAL_ATTENTION, "ARROW_LEFT", 400L + it * 10, TrialOutcome.CORRECT) } +
            trial(TaskId.VISUAL_ATTENTION, "ARROW_LEFT", 100, TrialOutcome.CORRECT, practice = true) +
            trial(TaskId.VISUAL_ATTENTION, "ARROW_LEFT", 900, TrialOutcome.WRONG) +
            trial(TaskId.VISUAL_ATTENTION, "ARROW_LEFT", null, TrialOutcome.OMISSION) +
            trial(TaskId.VISUAL_ATTENTION, "ARROW_LEFT", 90, TrialOutcome.ANTICIPATION)
        val m = CheckMetrics.compute(TaskId.VISUAL_ATTENTION, trials, va)
        assertEquals(18, m.mainTrials) // 연습·예측 반응 제외
        assertEquals(475L, m.medianRtMs) // 400..550 중앙값
        assertEquals(16.0 / 18, m.accuracy!!, 1e-9)
        assertEquals(1.0 / 18, m.secondary["omission_rate"]!!, 1e-9)
        assertNull(m.hiddenReason)
    }

    @Test
    fun fewTrialsHideMetricsWithReason() {
        val trials = List(10) { trial(TaskId.VISUAL_ATTENTION, "ARROW_RIGHT", 500, TrialOutcome.CORRECT) }
        val m = CheckMetrics.compute(TaskId.VISUAL_ATTENTION, trials, va)
        assertEquals("TOO_FEW_TRIALS", m.hiddenReason)
        assertNull(m.medianRtMs)
        assertNull(m.accuracy)
        assertEquals(emptyMap(), m.secondary)
    }

    @Test
    fun commissionRateHiddenWithFewNoGoTrials() {
        val gng = ProvisionalProtocols.GO_NO_GO
        val go = List(30) { trial(TaskId.GO_NO_GO, "GO", 350, TrialOutcome.CORRECT) }
        val few = go + List(5) { trial(TaskId.GO_NO_GO, "NOGO", null, TrialOutcome.CORRECT_REJECTION) }
        assertNull(CheckMetrics.compute(TaskId.GO_NO_GO, few, gng).secondary["commission_rate"])
        val enough = go + List(8) { trial(TaskId.GO_NO_GO, "NOGO", null, TrialOutcome.CORRECT_REJECTION) } +
            List(2) { trial(TaskId.GO_NO_GO, "NOGO", 300, TrialOutcome.COMMISSION) }
        val m = CheckMetrics.compute(TaskId.GO_NO_GO, enough, gng)
        assertEquals(0.2, m.secondary["commission_rate"]!!, 1e-9)
        assertEquals(350L, m.medianRtMs)
        assertEquals(38.0 / 40, m.accuracy!!, 1e-9)
    }

    @Test
    fun switchCostNeedsEnoughSwitchAndRepeat() {
        val ts = ProvisionalProtocols.TASK_SWITCHING
        val pure = List(14) { trial(TaskId.TASK_SWITCHING, "DIGIT_3_PARITY", 600, TrialOutcome.CORRECT) }
        val switches = List(6) { trial(TaskId.TASK_SWITCHING, "DIGIT_7_MAGNITUDE_SWITCH", 800, TrialOutcome.CORRECT) }
        val repeats = List(6) { trial(TaskId.TASK_SWITCHING, "DIGIT_2_PARITY_REPEAT", 650, TrialOutcome.CORRECT) }
        val m = CheckMetrics.compute(TaskId.TASK_SWITCHING, pure + switches + repeats, ts)
        assertEquals(150.0, m.secondary["switch_cost_ms"])
        val fewSwitch = CheckMetrics.compute(TaskId.TASK_SWITCHING, pure + switches.take(5) + repeats, ts)
        assertNotNull(fewSwitch.medianRtMs)
        assertNull(fewSwitch.secondary["switch_cost_ms"])
    }
}
