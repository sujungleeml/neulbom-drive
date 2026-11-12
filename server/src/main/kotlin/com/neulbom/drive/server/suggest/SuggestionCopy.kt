package com.neulbom.drive.server.suggest

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 제안 문구(PO 지시 2026-11-12, 시나리오 R01~R10). 문장은 이 템플릿으로만 만든다.
 * 관찰한 사실과 고를 수 있는 행동만 말한다. 능력, 앞으로의 일, 운전 지속 여부를 말하지 않는다.
 */
object SuggestionCopy {
    data class Message(val title: String, val together: String, val action: String)

    val MESSAGES: Map<String, Message> = mapOf(
        "R01" to Message("반응 시간과 급감속",
            "최근에는 주의와 반응 활동에서 응답하는 데 시간이 조금 더 걸렸고, 급감속 기록도 늘었어요.",
            "다음 운전에서는 앞차와 조금 더 여유 있는 거리를 유지해 보세요."),
        "R02" to Message("반응 시간과 야간 주행",
            "최근 활동에서는 평소보다 반응에 시간이 조금 더 걸렸어요. 밤에 운전하는 비중도 늘었네요.",
            "야간 운전이 부담스럽게 느껴진다면 밝은 시간대로 일정을 조정해 보세요."),
        "R03" to Message("정확도와 주행 시간",
            "이번에는 활동에서 정확도가 조금 낮아졌고, 운전한 시간은 늘었어요. 최근 피곤한 날이 많지는 않았나요?",
            "장거리 운전 전에는 컨디션을 살펴보세요."),
        "R04" to Message("멈출 때 누름과 급감속",
            "최근 활동에서는 버튼을 누르지 않아야 할 때 누른 경우가 늘었어요. 급감속 기록도 함께 늘었어요.",
            "다음 운전에서는 앞쪽 교통 흐름을 미리 살펴보고 여유 있게 속도를 조절해 보세요."),
        "R05" to Message("규칙 바꾸기와 야간 주행",
            "최근 규칙을 바꾸는 활동에서 실수가 조금 늘었어요. 야간 운전도 평소보다 많았네요.",
            "복잡하거나 익숙하지 않은 길은 출발 전에 경로를 확인해 두면 좋아요."),
        "R06" to Message("비슷한 활동 기록과 급감속",
            "주의와 반응 활동 기록은 평소와 비슷해요. 다만 급감속은 조금 늘었네요.",
            "최근 주행 환경이 달라진 건 아닌지 살펴보고, 정지할 때 미리 속도를 줄여 보세요."),
        "R07" to Message("달라진 활동 기록과 비슷한 주행",
            "이번 활동 기록은 평소와 조금 달랐지만, 주행 습관에는 큰 변화가 보이지 않았어요.",
            "한 번의 결과보다는 앞으로의 기록을 함께 살펴보세요."),
        "R08" to Message("짧아진 반응 시간과 줄어든 급감속",
            "최근 활동에서는 반응 시간이 짧아졌고, 주행거리 대비 급감속도 줄었어요.",
            "앞으로도 여유 있게 운전하는 습관을 이어가 보세요."),
        "R09" to Message("비슷한 활동 기록과 낮 주행",
            "활동 기록은 평소와 비슷하고, 이번 달에는 낮에 운전한 비중이 늘었어요.",
            "운전하기 편한 시간대를 스스로 고르고 계신지 돌아보세요."),
        "R10" to Message("두 기록 모두 들쭉날쭉",
            "최근에는 활동과 주행 기록 모두 이전보다 변화가 다양하게 나타났어요.",
            "생활 일정이나 운전 환경이 달라졌는지 살펴보고, 조금 더 기록을 모아 비교해 보세요."),
    )

    private fun sec(ms: Double) = "%.2f초".format(ms / 1000)
    private fun signed(v: Double, unit: String, digits: Int = 0): String {
        val s = if (digits == 0) abs(v).roundToInt().toString() else "%.${digits}f".format(abs(v))
        return (if (v >= 0) "+" else "−") + s + unit
    }

    /** 활동 기록 근거(기준선 대비). 반응 시간은 늘 보이고, 나머지는 '비슷함' 범위를 넘은 것만 보인다. */
    fun activityEvidence(a: SuggestionEngine.ActivityChange, rs: SuggestionEngine.Ruleset = SuggestionEngine.DEMO_RULESET): List<String> = buildList {
        if (a.baselineRtMs != null && a.recentRtMs != null && a.rtPct != null)
            add("세 활동 평균 반응 시간: 기준 ${sec(a.baselineRtMs)}, 최근 ${sec(a.recentRtMs)} (${signed(a.rtPct, "%")})")
        a.accuracyPt?.takeIf { abs(it) >= rs.stableAccuracyPt }?.let { add("맞게 누른 비율: 기준보다 ${signed(it, "%p")}") }
        a.commissionPt?.takeIf { abs(it) >= rs.stableAccuracyPt }?.let { add("멈춰야 할 때 누른 비율: 기준보다 ${signed(it, "%p")}") }
        a.switchAccuracyPt?.takeIf { abs(it) >= rs.stableAccuracyPt }?.let { add("규칙 바꾸기에서 맞게 누른 비율: 기준보다 ${signed(it, "%p")}") }
        a.variabilityPct?.takeIf { it >= rs.variabilityPct }?.let { add("점검마다 반응 시간이 들쭉날쭉한 정도: 기준보다 커졌어요") }
    }

    /** 주행 기록 근거(지난 기간 대비). 급감속과 야간 비율은 늘 보이고, 나머지는 '비슷함' 범위를 넘은 것만 보인다. */
    fun drivingEvidence(d: SuggestionEngine.DrivingChange, rs: SuggestionEngine.Ruleset = SuggestionEngine.DEMO_RULESET): List<String> = buildList {
        val (p, c) = d.labels ?: return@buildList
        d.decelPer100?.let { add("100 km당 급감속 기록: $p ${"%.1f".format(it.first)}번, $c ${"%.1f".format(it.second)}번") }
        d.nightShare?.let { add("야간 주행 비율: $p ${(it.first * 100).roundToInt()}%, $c ${(it.second * 100).roundToInt()}%") }
        d.durationPerDayS?.takeIf { abs(d.durationPct ?: 0.0) >= rs.stableDurationPct }?.let { add("하루 평균 주행 시간: $p ${(it.first / 60).roundToInt()}분, $c ${(it.second / 60).roundToInt()}분") }
        d.variabilityPct?.takeIf { it >= rs.variabilityPct }?.let { add("주마다 주행 거리가 들쭉날쭉한 정도: ${p}보다 커졌어요") }
    }

    const val ACTIVITY_BASIS = "처음 4번의 점검을 기준으로, 최근 점검(최대 2번)과 비교했어요."
    fun drivingBasis(d: SuggestionEngine.DrivingChange) = d.labels?.let { "${it.first}과 ${it.second}을 비교했어요. 급감속은 100 km당, 주행 시간은 하루 평균으로 맞췄어요." } ?: ""

    const val NOT_ENOUGH = "아직 기록을 모으는 중이에요. 기준이 되는 점검 4번과 두 달치 주행 기록이 모이면 함께 살펴볼 수 있어요."
    const val NO_MATCH = "이번에는 함께 살펴볼 만한 변화가 보이지 않았어요."
}
