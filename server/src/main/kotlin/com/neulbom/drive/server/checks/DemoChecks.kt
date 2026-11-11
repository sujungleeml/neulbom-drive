package com.neulbom.drive.server.checks

import com.neulbom.drive.domain.cognitive.ProvisionalProtocols
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * 시연용 **모의** 점검 기록(NEULBOM_DEMO_SEED). 시드 고정, 모두 vehicleSource = MOCK.
 * 값은 화면 시연을 위한 가짜이며 어떤 집단의 기준값도 아니다.
 */
object DemoChecks {
    fun generate(today: LocalDate, zone: ZoneId, seed: Long = 20261101L): List<AssessmentUploadDto> {
        val rnd = Random(seed)
        // 처음 기준 만들기(2~3일 간격 4회) → 중간에 한 번 멈춤 → 28일 뒤 정기 점검
        val days = listOf(-38L, -35L, -33L, -30L, -29L, -2L)
        return days.mapIndexed { n, d ->
            val start = today.plusDays(d).atTime(18, 10 + n).atZone(zone).toInstant()
            val interrupted = n == 4
            AssessmentUploadDto(
                AssessmentSessionDto(
                    assessmentId = "demo-check-$n",
                    startedAt = start.toString(),
                    endedAt = start.plusSeconds(if (interrupted) 70 else 185).toString(),
                    status = if (interrupted) "INTERRUPTED" else "COMPLETED",
                    stopReason = if (interrupted) "DRIVING" else null,
                    isFirstAssessment = n == 0,
                    protocolVersions = ProvisionalProtocols.ALL.associate { it.taskId.name to it.taskVersion },
                    appVersion = "demo",
                    deviceProfile = "demo-emulator",
                    vehicleSource = "MOCK",
                ),
                trials(rnd, learning = maxOf(0, 3 - n) * 25L, partial = interrupted),
            )
        }
    }

    private fun trials(rnd: Random, learning: Long, partial: Boolean): List<TrialDto> {
        val out = mutableListOf<TrialDto>()
        var t = 0L
        fun add(task: String, block: String, type: String, base: Long, pCorrect: Double, practice: Boolean = false, noGo: Boolean = false) {
            val i = out.count { it.taskId == task }
            t += 1_800
            val ok = rnd.nextDouble() < pCorrect
            val (rt, outcome) = when {
                noGo -> if (ok) null to "CORRECT_REJECTION" else (base - 60 + rnd.nextLong(0, 80)) to "COMMISSION"
                !ok && rnd.nextBoolean() -> null to "OMISSION"
                else -> (base + learning + rnd.nextLong(-90, 120)) to if (ok) "CORRECT" else "WRONG"
            }
            val p = ProvisionalProtocols.ALL.first { it.taskId.name == task }
            out += TrialDto(task, p.taskVersion, p.stimulusSetVersion, i, block, type, t, t + 16, rt?.let { t + 16 + it }, rt, rt?.let { if (task == "GO_NO_GO") "TAP" else "LEFT" }, outcome, practice, 16)
        }
        repeat(3) { add("VISUAL_ATTENTION", "PRACTICE", "ARROW_LEFT", 600, 0.9, practice = true) }
        repeat(24) { add("VISUAL_ATTENTION", "MAIN", if (it % 2 == 0) "ARROW_LEFT" else "ARROW_RIGHT", 520, 0.96) }
        if (partial) return out
        repeat(40) { add("GO_NO_GO", "MAIN", if (it % 4 == 3) "NOGO" else "GO", 410, if (it % 4 == 3) 0.88 else 0.97, noGo = it % 4 == 3) }
        repeat(8) { add("TASK_SWITCHING", "PURE_PARITY", "DIGIT_${3 + it % 5}_PARITY", 680, 0.95) }
        repeat(8) { add("TASK_SWITCHING", "PURE_MAGNITUDE", "DIGIT_${2 + it % 6}_MAGNITUDE", 690, 0.95) }
        repeat(16) {
            val sw = it % 2 == 0 && it > 0
            add("TASK_SWITCHING", "MIXED", "DIGIT_${1 + it % 8}_${if ((it / 2) % 2 == 0) "PARITY" else "MAGNITUDE"}_${if (sw) "SWITCH" else "REPEAT"}", if (sw) 860 else 720, 0.93)
        }
        return out
    }
}
