package com.neulbom.drive.domain.cognitive

import kotlin.math.roundToInt
import kotlin.random.Random

/** 시행 계획 생성기. 프로토콜과 난수 시드가 같으면 같은 계획(재현성). */
object TaskPlans {

    fun forProtocol(p: TaskProtocol, includePractice: Boolean, random: Random): List<TrialSpec> = when (p.taskId) {
        TaskId.VISUAL_ATTENTION -> visualAttention(p, includePractice, random)
        TaskId.GO_NO_GO -> goNoGo(p, includePractice, random)
        TaskId.TASK_SWITCHING -> taskSwitching(p, includePractice, random)
    }

    private fun isi(p: TaskProtocol, random: Random) = random.nextLong(p.isiMinMs, p.isiMaxMs + 1)

    private fun visualAttention(p: TaskProtocol, includePractice: Boolean, random: Random): List<TrialSpec> {
        fun sides(n: Int) = (List(n / 2) { ResponseKey.LEFT } + List(n - n / 2) { ResponseKey.RIGHT }).shuffled(random)
        val practice = if (includePractice) sides(p.practiceTrials).map { Block.PRACTICE to it } else emptyList()
        val main = sides(p.trialCount).map { Block.MAIN to it }
        return (practice + main).mapIndexed { i, (block, side) -> TrialSpec(i, block, Stimulus.Arrow(side), side, isi(p, random)) }
    }

    private fun goNoGo(p: TaskProtocol, includePractice: Boolean, random: Random): List<TrialSpec> {
        fun trials(n: Int): List<Boolean> {
            val go = (n * (p.goRatio ?: 0.75)).roundToInt()
            return (List(go) { true } + List(n - go) { false }).shuffled(random)
        }
        // 연습은 Go와 No-Go를 모두 한 번 이상 경험하도록 교대로 시작한다.
        val practice = if (includePractice) List(p.practiceTrials) { it % 2 == 0 }.map { Block.PRACTICE to it } else emptyList()
        val main = trials(p.trialCount).map { Block.MAIN to it }
        return (practice + main).mapIndexed { i, (block, isGo) ->
            TrialSpec(i, block, Stimulus.GoNoGo(isGo), if (isGo) ResponseKey.TAP else null, isi(p, random))
        }
    }

    private fun taskSwitching(p: TaskProtocol, includePractice: Boolean, random: Random): List<TrialSpec> {
        val digits = listOf(1, 2, 3, 4, 6, 7, 8, 9)
        val specs = mutableListOf<TrialSpec>()
        var previousRule: SwitchRule? = null
        fun add(block: Block, rule: SwitchRule, trackSwitch: Boolean) {
            val value = digits[random.nextInt(digits.size)]
            specs += TrialSpec(
                index = specs.size,
                block = block,
                stimulus = Stimulus.Digit(value, rule),
                expected = expectedFor(value, rule),
                isiMs = isi(p, random),
                isSwitch = if (trackSwitch) previousRule?.let { it != rule } else null,
            )
            previousRule = rule
        }
        if (includePractice) repeat(p.practiceTrials) { i -> add(Block.PRACTICE, if (i % 2 == 0) SwitchRule.PARITY else SwitchRule.MAGNITUDE, false) }
        val pure = p.trialCount / 4
        val mixed = p.trialCount - 2 * pure
        repeat(pure) { add(Block.PURE_PARITY, SwitchRule.PARITY, false) }
        repeat(pure) { add(Block.PURE_MAGNITUDE, SwitchRule.MAGNITUDE, false) }
        // 혼합 블록: AABB 교대 → 전환/비전환 시행이 대략 반반. 첫 시행은 비교 기준이 없어 null.
        previousRule = null
        repeat(mixed) { i -> add(Block.MIXED, if ((i / 2) % 2 == 0) SwitchRule.PARITY else SwitchRule.MAGNITUDE, true) }
        return specs
    }

    fun expectedFor(value: Int, rule: SwitchRule): ResponseKey = when (rule) {
        SwitchRule.PARITY -> if (value % 2 == 1) ResponseKey.LEFT else ResponseKey.RIGHT
        SwitchRule.MAGNITUDE -> if (value < 5) ResponseKey.LEFT else ResponseKey.RIGHT
    }
}
