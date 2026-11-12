package com.neulbom.drive.server.suggest

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 「이번 달의 기록과 작은 제안」 엔진 (PO 지시 2026-11-12).
 *
 * - 활동 기록은 **개인 기준선**(처음 4번)과 최근 기록을, 주행 기록은 **지난 비교 기간**과 이번 기간을 각각 따로 비교한다.
 * - 두 변화가 함께 나타났다는 이유로 원인이나 앞으로의 일을 추론하지 않는다. 합친 값을 만들지 않는다.
 * - 규칙의 기준값은 검증되지 않은 **시연 규칙**이다([Ruleset.validated] = false). 실제 사용자 기록에는 쓰지 않는다(D23 유지).
 */
object SuggestionEngine {

    enum class Activity { ARROW, GO_NO_GO, SWITCH }

    /** 한 번의 활동 기록(활동 하나). commission은 Go/No-Go에서 누르지 않아야 할 때 누른 비율. */
    data class ActivityRecord(val activity: Activity, val medianRtMs: Double, val accuracy: Double, val commission: Double? = null)

    /** 한 번의 점검(세 활동). */
    data class CheckSession(val date: String, val records: List<ActivityRecord>)

    /** 한 비교 기간의 주행 요약. weeklyKm은 기간 안 주별 거리(변동 비교용). */
    data class DrivePeriod(
        val label: String,
        val calendarDays: Int,
        val drivingDays: Int,
        val km: Double,
        val durationS: Double,
        val harshDecel: Int,
        val nightS: Double,
        val weeklyKm: List<Double>,
    )

    /** 규칙 묶음. 시연 규칙과 (앞으로의) 운영 규칙을 설정으로 나눈다. 운영 규칙은 아직 없다. */
    data class Ruleset(
        val id: String,
        val validated: Boolean,
        val rtPct: Double = 8.0,
        val stableRtPct: Double = 5.0,
        val accuracyPt: Double = 5.0,
        val stableAccuracyPt: Double = 3.0,
        val variabilityPct: Double = 30.0,
        val decelPer100: Double = 0.5,
        val stableDecelPer100: Double = 0.3,
        val nightPt: Double = 5.0,
        val stableNightPt: Double = 3.0,
        val durationPct: Double = 20.0,
        val stableDurationPct: Double = 10.0,
    )

    val DEMO_RULESET = Ruleset(id = "demo-2026-11", validated = false)

    const val BASELINE_CHECKS = 4
    const val MIN_RECENT_CHECKS = 1
    /** 주행 비교에 필요한 최소 기록(월간 리포트 비교 규칙과 같다). */
    const val MIN_DRIVING_DAYS = 8
    const val MIN_KM = 100.0
    /** 100 km당 급감속은 이 거리 미만이면 계산하지 않는다(월간 리포트와 같다). */
    const val MIN_DECEL_KM = 50.0

    /** 활동 기록 변화(기준선 대비). 값이 null이면 그 항목은 비교하지 않았다. */
    data class ActivityChange(
        val baselineReady: Boolean,
        val rtPct: Double? = null,
        val accuracyPt: Double? = null,
        val commissionPt: Double? = null,
        val switchAccuracyPt: Double? = null,
        val variabilityPct: Double? = null,
        val baselineRtMs: Double? = null,
        val recentRtMs: Double? = null,
        val recentChecks: Int = 0,
    )

    /** 주행 기록 변화(지난 기간 대비). 기간 길이가 달라도 비교되도록 주행 시간은 하루 평균, 급감속은 100 km당으로 본다. */
    data class DrivingChange(
        val comparable: Boolean,
        val decelPer100: Pair<Double, Double>? = null,
        val nightShare: Pair<Double, Double>? = null,
        val durationPerDayS: Pair<Double, Double>? = null,
        val variabilityPct: Double? = null,
        val labels: Pair<String, String>? = null,
    ) {
        val decelDelta get() = decelPer100?.let { it.second - it.first }
        val nightDeltaPt get() = nightShare?.let { (it.second - it.first) * 100 }
        val durationPct get() = durationPerDayS?.let { if (it.first <= 0.0) null else (it.second / it.first - 1) * 100 }
    }

    data class Result(val matched: List<String>, val activity: ActivityChange, val driving: DrivingChange) {
        val primary get() = matched.firstOrNull()
    }

    // ---- 변화 계산 ----

    private fun median(xs: List<Double>): Double { val s = xs.sorted(); val n = s.size; return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2 }
    private fun mean(xs: List<Double>) = xs.sum() / xs.size
    private fun cv(xs: List<Double>): Double? {
        if (xs.size < 2) return null
        val m = mean(xs)
        if (m <= 0.0) return null
        return sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1)) / m
    }

    fun activityChange(sessions: List<CheckSession>): ActivityChange {
        val sorted = sessions.sortedBy { it.date }
        if (sorted.size < BASELINE_CHECKS + MIN_RECENT_CHECKS) return ActivityChange(baselineReady = sorted.size >= BASELINE_CHECKS, recentChecks = (sorted.size - BASELINE_CHECKS).coerceAtLeast(0))
        val base = sorted.take(BASELINE_CHECKS)
        val recent = sorted.drop(BASELINE_CHECKS).takeLast(2)
        fun recs(ss: List<CheckSession>, a: Activity) = ss.mapNotNull { s -> s.records.firstOrNull { it.activity == a } }
        val rtPcts = Activity.entries.mapNotNull { a ->
            val b = recs(base, a).map { it.medianRtMs }; val r = recs(recent, a).map { it.medianRtMs }
            if (b.isEmpty() || r.isEmpty()) null else (median(r) / median(b) - 1) * 100
        }
        val accPts = Activity.entries.mapNotNull { a ->
            val b = recs(base, a).map { it.accuracy }; val r = recs(recent, a).map { it.accuracy }
            if (b.isEmpty() || r.isEmpty()) null else (mean(r) - mean(b)) * 100
        }
        val comm = recs(base, Activity.GO_NO_GO).mapNotNull { it.commission }.let { b ->
            val r = recs(recent, Activity.GO_NO_GO).mapNotNull { it.commission }
            if (b.isEmpty() || r.isEmpty()) null else (mean(r) - mean(b)) * 100
        }
        val sw = recs(base, Activity.SWITCH).map { it.accuracy }.let { b ->
            val r = recs(recent, Activity.SWITCH).map { it.accuracy }
            if (b.isEmpty() || r.isEmpty()) null else (mean(r) - mean(b)) * 100
        }
        // 변동: 점검마다 세 활동 반응 시간을 각자 기준선 가운데 값으로 나눈 비율의 흩어짐
        fun ratios(ss: List<CheckSession>) = ss.flatMap { s ->
            s.records.mapNotNull { r -> recs(base, r.activity).map { it.medianRtMs }.takeIf { it.isNotEmpty() }?.let { r.medianRtMs / median(it) } }
        }
        val varPct = cv(ratios(base))?.let { b -> cv(ratios(recent))?.let { r -> (r - b) / maxOf(b, 0.02) * 100 } }
        val baseRt = Activity.entries.mapNotNull { a -> recs(base, a).map { it.medianRtMs }.takeIf { it.isNotEmpty() }?.let(::median) }
        val recRt = Activity.entries.mapNotNull { a -> recs(recent, a).map { it.medianRtMs }.takeIf { it.isNotEmpty() }?.let(::median) }
        return ActivityChange(
            baselineReady = true,
            rtPct = rtPcts.takeIf { it.isNotEmpty() }?.let(::mean),
            accuracyPt = accPts.takeIf { it.isNotEmpty() }?.let(::mean),
            commissionPt = comm,
            switchAccuracyPt = sw,
            variabilityPct = varPct,
            baselineRtMs = baseRt.takeIf { it.isNotEmpty() }?.let(::mean),
            recentRtMs = recRt.takeIf { it.isNotEmpty() }?.let(::mean),
            recentChecks = recent.size,
        )
    }

    fun drivingChange(previous: DrivePeriod?, current: DrivePeriod?): DrivingChange {
        if (previous == null || current == null) return DrivingChange(comparable = false)
        fun enough(p: DrivePeriod) = p.drivingDays >= MIN_DRIVING_DAYS && p.km >= MIN_KM && p.calendarDays > 0 && p.durationS > 0
        if (!enough(previous) || !enough(current)) return DrivingChange(comparable = false)
        fun decel(p: DrivePeriod) = if (p.km < MIN_DECEL_KM) null else p.harshDecel / p.km * 100
        val d = decel(previous)?.let { a -> decel(current)?.let { b -> a to b } }
        val n = (previous.nightS / previous.durationS) to (current.nightS / current.durationS)
        val dur = (previous.durationS / previous.calendarDays) to (current.durationS / current.calendarDays)
        val v = cv(previous.weeklyKm)?.let { a -> cv(current.weeklyKm)?.let { b -> (b - a) / maxOf(a, 0.05) * 100 } }
        return DrivingChange(true, d, n, dur, v, previous.label to current.label)
    }

    // ---- 규칙 ----

    /** 우선순위 순서. 앞에 있을수록 대표 제안이 된다(구체적인 변화 조합이 먼저). */
    val PRIORITY = listOf("R04", "R01", "R05", "R02", "R03", "R10", "R06", "R07", "R08", "R09")

    fun evaluate(a: ActivityChange, d: DrivingChange, rs: Ruleset = DEMO_RULESET): Result {
        // 기준선이 없거나 주행 비교가 안 되면 제안을 만들지 않는다.
        if (!a.baselineReady || a.rtPct == null || !d.comparable) return Result(emptyList(), a, d)
        val rt = a.rtPct
        val acc = a.accuracyPt ?: 0.0
        val comm = a.commissionPt ?: 0.0
        val sw = a.switchAccuracyPt ?: 0.0
        val aVarUp = (a.variabilityPct ?: 0.0) >= rs.variabilityPct
        val rtUp = rt >= rs.rtPct
        val rtDown = rt <= -rs.rtPct
        val accDown = acc <= -rs.accuracyPt
        val commUp = comm >= rs.accuracyPt
        val swDown = sw <= -rs.accuracyPt
        val aStable = abs(rt) < rs.stableRtPct && abs(acc) < rs.stableAccuracyPt && abs(comm) < rs.stableAccuracyPt && abs(sw) < rs.stableAccuracyPt && !aVarUp
        val aChanged = rtUp || rtDown || accDown || commUp || swDown
        val swStable = abs(sw) < rs.stableAccuracyPt && abs(rt) < rs.stableRtPct

        val decel = d.decelDelta
        val night = d.nightDeltaPt ?: 0.0
        val dur = d.durationPct ?: 0.0
        val dVarUp = (d.variabilityPct ?: 0.0) >= rs.variabilityPct
        val decelUp = decel != null && decel >= rs.decelPer100
        val decelDown = decel != null && decel <= -rs.decelPer100
        val nightUp = night >= rs.nightPt
        val nightDown = night <= -rs.nightPt
        val durUp = dur >= rs.durationPct
        val dStable = decel != null && abs(decel) < rs.stableDecelPer100 && abs(night) < rs.stableNightPt && abs(dur) < rs.stableDurationPct && !dVarUp

        val hit = mapOf(
            "R01" to (rtUp && decelUp),
            "R02" to (rtUp && nightUp),
            "R03" to (accDown && durUp),
            "R04" to (commUp && decelUp),
            "R05" to (swDown && nightUp),
            "R06" to (aStable && decelUp),
            "R07" to (aChanged && dStable),
            "R08" to (rtDown && decelDown),
            "R09" to (swStable && !aVarUp && nightDown),
            "R10" to (aVarUp && dVarUp),
        )
        var matched = PRIORITY.filter { hit.getValue(it) }
        // 서로 어긋나는 제안을 함께 내지 않는다: '좋아졌어요'(R08)는 다른 제안이 있으면 빼고,
        // '평소와 비슷해요'(R06, R09)는 활동 변화를 말하는 제안과 함께 내지 않는다.
        if (matched.size > 1) matched = matched - "R08"
        if (matched.any { it in ACTIVITY_CHANGE_RULES }) matched = matched - setOf("R06", "R09")
        return Result(matched, a, d)
    }

    private val ACTIVITY_CHANGE_RULES = setOf("R01", "R02", "R03", "R04", "R05", "R07", "R10")
}
