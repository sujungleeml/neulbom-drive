package com.neulbom.drive.domain.cognitive

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskPlansTest {
    private val va = ProvisionalProtocols.VISUAL_ATTENTION
    private val gng = ProvisionalProtocols.GO_NO_GO
    private val ts = ProvisionalProtocols.TASK_SWITCHING

    @Test
    fun visualAttentionHasPracticeAndBalancedMainTrials() {
        val plan = TaskPlans.forProtocol(va, includePractice = true, random = Random(7))
        assertEquals(3, plan.count { it.isPractice })
        val main = plan.filterNot { it.isPractice }
        assertEquals(24, main.size)
        assertEquals(12, main.count { it.expected == ResponseKey.LEFT })
        assertTrue(plan.all { it.isiMs in 800..1600 })
        assertTrue(plan.all { (it.stimulus as Stimulus.Arrow).side == it.expected })
    }

    @Test
    fun goNoGoPlanRespectsRatio() {
        val main = TaskPlans.forProtocol(gng, includePractice = true, random = Random(1)).filterNot { it.isPractice }
        assertEquals(40, main.size)
        assertEquals(30, main.count { (it.stimulus as Stimulus.GoNoGo).isGo })
        assertTrue(main.all { s -> (s.stimulus as Stimulus.GoNoGo).isGo == (s.expected == ResponseKey.TAP) })
        assertTrue(main.filter { !(it.stimulus as Stimulus.GoNoGo).isGo }.all { it.expected == null })
    }

    @Test
    fun practiceExcludedWhenNotFirstAssessment() {
        ProvisionalProtocols.ALL.forEach { p ->
            assertEquals(0, TaskPlans.forProtocol(p, includePractice = false, random = Random(3)).count { it.block == Block.PRACTICE })
        }
    }

    @Test
    fun plansAreReproducibleWithSameSeed() {
        ProvisionalProtocols.ALL.forEach { p ->
            assertEquals(TaskPlans.forProtocol(p, true, Random(3)), TaskPlans.forProtocol(p, true, Random(3)))
        }
    }

    @Test
    fun taskSwitchingHasPracticePureAndMixedBlocksWithSwitchAndRepeatTrials() {
        val plan = TaskPlans.forProtocol(ts, includePractice = true, random = Random(1))
        assertEquals(4, plan.count { it.block == Block.PRACTICE })
        assertEquals(8, plan.count { it.block == Block.PURE_PARITY })
        assertEquals(8, plan.count { it.block == Block.PURE_MAGNITUDE })
        val mixed = plan.filter { it.block == Block.MIXED }
        assertEquals(16, mixed.size)
        assertNull(mixed.first().isSwitch)
        assertEquals(7, mixed.count { it.isSwitch == true })
        assertEquals(8, mixed.count { it.isSwitch == false })
        plan.forEach {
            val d = it.stimulus as Stimulus.Digit
            assertTrue(d.value in 1..9 && d.value != 5)
            assertEquals(TaskPlans.expectedFor(d.value, d.rule), it.expected)
        }
    }

    @Test
    fun expectedAnswers() {
        assertEquals(ResponseKey.LEFT, TaskPlans.expectedFor(3, SwitchRule.PARITY))
        assertEquals(ResponseKey.RIGHT, TaskPlans.expectedFor(4, SwitchRule.PARITY))
        assertEquals(ResponseKey.LEFT, TaskPlans.expectedFor(4, SwitchRule.MAGNITUDE))
        assertEquals(ResponseKey.RIGHT, TaskPlans.expectedFor(7, SwitchRule.MAGNITUDE))
    }
}

class TaskEngineTest {
    private fun spec(i: Int, expected: ResponseKey?, block: Block = Block.MAIN, isSwitch: Boolean? = null) =
        TrialSpec(i, block, if (expected == null) Stimulus.GoNoGo(false) else Stimulus.Arrow(expected), expected, 1000, isSwitch)

    @Test
    fun recordsCorrectWrongOmissionAndAnticipation() {
        val engine = TaskEngine(List(4) { spec(it, ResponseKey.LEFT) }, responseWindowMs = 2500)
        assertNull(engine.respond(ResponseKey.LEFT, 0), "자극 전 입력 무시")
        engine.stimulusShown(1000, requestedAtMs = 990)
        assertEquals(TrialOutcome.CORRECT, engine.respond(ResponseKey.LEFT, 1500)!!.outcome)
        engine.stimulusShown(3000)
        assertEquals(TrialOutcome.WRONG, engine.respond(ResponseKey.RIGHT, 3600)!!.outcome)
        engine.stimulusShown(5000)
        assertNull(engine.windowElapsed(6000), "제한 시간 전에는 누락 아님")
        assertEquals(TrialOutcome.OMISSION, engine.windowElapsed(7500)!!.outcome)
        engine.stimulusShown(9000)
        assertEquals(TrialOutcome.ANTICIPATION, engine.respond(ResponseKey.LEFT, 9050)!!.outcome)
        assertTrue(engine.isComplete)
        assertEquals(10, engine.results().first().displayLatencyMs)
    }

    @Test
    fun noGoWithheldIsCorrectRejection() {
        val engine = TaskEngine(listOf(spec(0, null)), responseWindowMs = 800)
        engine.stimulusShown(1000)
        assertEquals(TrialOutcome.CORRECT_REJECTION, engine.windowElapsed(1800)!!.outcome)
    }

    @Test
    fun noGoTapIsCommission() {
        val engine = TaskEngine(listOf(spec(0, null)), responseWindowMs = 800)
        engine.stimulusShown(1000)
        val r = engine.respond(ResponseKey.TAP, 1300)!!
        assertEquals(TrialOutcome.COMMISSION, r.outcome)
        assertEquals(1300L, r.responseMs)
    }

    @Test
    fun goWithoutTapIsOmission() {
        val engine = TaskEngine(listOf(spec(0, ResponseKey.TAP)), responseWindowMs = 800)
        engine.stimulusShown(1000)
        assertEquals(TrialOutcome.OMISSION, engine.windowElapsed(1800)!!.outcome)
    }

    @Test
    fun recordsScheduledAndActualOnset() {
        val engine = TaskEngine(listOf(spec(0, ResponseKey.LEFT)), responseWindowMs = 2000)
        engine.stimulusShown(shownAtMs = 1016, requestedAtMs = 1000)
        val r = engine.respond(ResponseKey.LEFT, 1500)!!
        assertEquals(1000L, r.scheduledOnsetMs)
        assertEquals(1016L, r.actualOnsetMs)
        assertEquals(484L, r.rtMs)
    }

    @Test
    fun abortBlocksAllFurtherInput() {
        val engine = TaskEngine(List(3) { spec(it, ResponseKey.LEFT) }, responseWindowMs = 2500)
        engine.stimulusShown(0)
        engine.abort()
        assertNull(engine.respond(ResponseKey.LEFT, 500))
        assertNull(engine.windowElapsed(10_000))
        engine.stimulusShown(20_000)
        assertNull(engine.respond(ResponseKey.LEFT, 20_500))
        assertTrue(engine.isAborted)
        assertFalse(engine.isComplete)
        assertNull(engine.current)
        assertTrue(engine.results().isEmpty())
    }
}
