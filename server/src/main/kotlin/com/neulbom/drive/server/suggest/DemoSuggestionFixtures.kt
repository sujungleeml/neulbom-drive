package com.neulbom.drive.server.suggest

import com.neulbom.drive.server.suggest.SuggestionEngine.Activity
import com.neulbom.drive.server.suggest.SuggestionEngine.ActivityRecord
import com.neulbom.drive.server.suggest.SuggestionEngine.CheckSession
import com.neulbom.drive.server.suggest.SuggestionEngine.DrivePeriod
import kotlin.math.roundToInt

/**
 * 시연용 합성 기록(결정적). 시나리오마다 활동 기록(기준 4번 + 최근 2번)과 두 달치 주행 요약을 만든다.
 * 실제 사용자 저장소와 완전히 따로 있고, 저장소를 읽거나 쓰지 않는다.
 */
object DemoSuggestionFixtures {
    data class Scenario(
        val id: String,
        val label: String,
        val sessions: List<CheckSession>,
        val previous: DrivePeriod,
        val current: DrivePeriod,
        /** 기대 결과(테스트와 화면 확인용). 첫째가 대표 제안. */
        val expected: List<String>,
    )

    private data class Spec(
        val rt: Double = 1.0, val acc: Double = 0.0, val comm: Double = 0.0, val sw: Double = 0.0, val alt: Double = 0.0,
        val decel: Pair<Double, Double> = 1.5 to 1.5, val night: Pair<Double, Double> = 0.15 to 0.15,
        val minPerDay: Pair<Double, Double> = 50.0 to 50.0,
        val weeklyCur: List<Double> = listOf(250.0, 250.0, 250.0, 250.0),
    )

    private val BASE_RT = mapOf(Activity.ARROW to 550.0, Activity.GO_NO_GO to 450.0, Activity.SWITCH to 760.0)
    private val BASE_ACC = mapOf(Activity.ARROW to 0.97, Activity.GO_NO_GO to 0.95, Activity.SWITCH to 0.93)
    private const val BASE_COMM = 0.08
    private val JITTER = listOf(-0.01, 0.01, -0.005, 0.005)
    private val WEEKLY_PREV = listOf(245.0, 255.0, 250.0, 250.0)

    private fun sessions(s: Spec): List<CheckSession> {
        val base = JITTER.mapIndexed { i, j ->
            CheckSession("2026-08-${"%02d".format(3 + i * 7)}", Activity.entries.map { a ->
                ActivityRecord(a, BASE_RT.getValue(a) * (1 + j), BASE_ACC.getValue(a), if (a == Activity.GO_NO_GO) BASE_COMM else null)
            })
        }
        val recent = listOf(-1.0, 1.0).mapIndexed { i, sign ->
            CheckSession("2026-11-${"%02d".format(2 + i * 7)}", Activity.entries.map { a ->
                val acc = BASE_ACC.getValue(a) + s.acc / 100 + if (a == Activity.SWITCH) s.sw / 100 else 0.0
                ActivityRecord(a, BASE_RT.getValue(a) * s.rt * (1 + sign * s.alt), acc.coerceIn(0.0, 1.0), if (a == Activity.GO_NO_GO) BASE_COMM + s.comm / 100 else null)
            })
        }
        return base + recent
    }

    private fun period(label: String, days: Int, weekly: List<Double>, decel: Double, night: Double, minPerDay: Double): DrivePeriod {
        val km = weekly.sum()
        val dur = minPerDay * 60 * days
        return DrivePeriod(label, days, drivingDays = 14, km = km, durationS = dur, harshDecel = (decel * km / 100).roundToInt(), nightS = night * dur, weeklyKm = weekly)
    }

    private fun scenario(id: String, label: String, s: Spec, expected: List<String>) = Scenario(
        id, label, sessions(s),
        period("10월", 31, WEEKLY_PREV, s.decel.first, s.night.first, s.minPerDay.first),
        period("11월", 30, s.weeklyCur, s.decel.second, s.night.second, s.minPerDay.second),
        expected,
    )

    val ALL: List<Scenario> = listOf(
        // 대표 화면: 반응 시간 증가 + 급감속 증가(야간도 조금 늘어 R02가 둘째 제안으로 붙는다)
        scenario("R01", "반응 시간 증가, 급감속 증가", Spec(rt = 1.12, decel = 1.2 to 2.0, night = 0.15 to 0.22), listOf("R01", "R02")),
        scenario("R02", "반응 시간 증가, 야간 주행 증가", Spec(rt = 1.10, decel = 1.5 to 1.6, night = 0.12 to 0.24), listOf("R02")),
        scenario("R03", "정확도 감소, 주행 시간 증가", Spec(acc = -8.0, minPerDay = 50.0 to 65.0), listOf("R03")),
        scenario("R04", "멈출 때 누름 증가, 급감속 증가", Spec(rt = 1.03, comm = 9.0, decel = 1.2 to 2.0), listOf("R04")),
        scenario("R05", "규칙 바꾸기 정확도 감소, 야간 주행 증가", Spec(sw = -8.0, night = 0.12 to 0.22), listOf("R05")),
        scenario("R06", "활동 기록 비슷함, 급감속 증가", Spec(decel = 1.2 to 2.0), listOf("R06")),
        scenario("R07", "활동 기록 변화, 주행 비슷함", Spec(rt = 1.10), listOf("R07")),
        scenario("R08", "반응 시간 감소, 급감속 감소", Spec(rt = 0.90, decel = 2.0 to 1.2), listOf("R08")),
        scenario("R09", "활동 기록 비슷함, 야간 주행 감소", Spec(night = 0.20 to 0.10), listOf("R09")),
        scenario("R10", "두 기록 모두 흩어짐 증가", Spec(alt = 0.15, weeklyCur = listOf(60.0, 440.0, 70.0, 430.0)), listOf("R10")),
    )

    fun get(id: String) = ALL.firstOrNull { it.id == id }
}
