package com.neulbom.drive.domain.driving

import com.neulbom.drive.core.model.Attribution
import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.model.SignalSample
import java.time.Duration
import kotlin.math.max
import kotlin.math.roundToLong

enum class SpeedBand { LT30, B30_60, B60_90, GE90 }

enum class DecelSource { LONG_ACCEL, SPEED_DERIVED }

enum class SessionQuality { GOOD, LIMITED, EXCLUDED }

data class SessionFeatures(
    val driveId: String,
    val algorithmVersion: String,
    val durationS: Double,
    val distanceKm: Double?,
    val nightShare: Double,
    val speedBandSeconds: Map<SpeedBand, Double>?,
    val harshDecelCount: Int?,
    val harshDecelByBand: Map<SpeedBand, Int>?,
    val decelSource: DecelSource?,
    val coverage: Double,
    val missingRate: Double,
    val maxGapS: Double,
    val droppedSamples: Int,
    val quality: SessionQuality,
    val valid: Boolean,
)

fun speedBandOf(kmh: Double): SpeedBand = when {
    kmh < 30 -> SpeedBand.LT30
    kmh < 60 -> SpeedBand.B30_60
    kmh < 90 -> SpeedBand.B60_90
    else -> SpeedBand.GE90
}

/** 세션 하나의 특징량(명세 v0.6 08 §2~§5). 같은 입력이면 항상 같은 결과. */
object SessionFeatureCalculator {

    fun compute(session: DrivingSession, samples: List<SignalSample>): SessionFeatures {
        val durationS = Duration.between(session.startedAt, session.endedAt).toMillis() / 1000.0
        var dropped = 0
        val bySignal = samples.filter { it.signal in session.signals }.groupBy { it.signal }.mapValues { (_, list) ->
            // 도착 순서를 유지하면서 시각이 뒤로 가거나 같은 표본은 버린다.
            var last = Long.MIN_VALUE
            list.filter { s -> (s.tMonoMs > last).also { ok -> if (ok) last = s.tMonoMs else dropped++ } }
        }

        val speed = bySignal[SignalId.SPEED].takeIf { SignalId.SPEED in session.signals }
        var distanceKm: Double? = null
        var bands: MutableMap<SpeedBand, Double>? = null
        var maxGapS = 0.0
        if (speed != null) {
            distanceKm = 0.0
            bands = SpeedBand.entries.associateWith { 0.0 }.toMutableMap()
            for (i in 1 until speed.size) {
                val dt = (speed[i].tMonoMs - speed[i - 1].tMonoMs) / 1000.0
                maxGapS = max(maxGapS, dt)
                if (dt > DrivingConfig.MAX_SAMPLE_GAP_S) continue
                distanceKm += (speed[i].value + speed[i - 1].value) / 2.0 * dt / 3600.0
                bands[speedBandOf(speed[i - 1].value)] = bands.getValue(speedBandOf(speed[i - 1].value)) + dt
            }
        }

        val decel = decelSeries(session, bySignal)
        val events = decel?.let { (_, series) -> harshDecelStarts(series) }
        val eventsByBand = if (events != null && speed != null && speed.isNotEmpty()) {
            SpeedBand.entries.associateWith { b -> events.count { t -> speedBandOf(nearest(speed, t).value) == b } }
        } else {
            null
        }

        val measured = session.signals - SignalId.VEHICLE_STATE
        val coverage = if (measured.isEmpty()) 1.0 else measured.count { !bySignal[it].isNullOrEmpty() }.toDouble() / measured.size
        val expected = measured.sumOf { s -> (session.sampleRateHz[s] ?: 0.0) * durationS }
        val actual = measured.sumOf { bySignal[it]?.size ?: 0 }.toDouble()
        val missingRate = if (expected <= 0.0) 0.0 else (1.0 - actual / expected).coerceIn(0.0, 1.0)

        val quality = when {
            dropped > 0 || durationS < DrivingConfig.MIN_VALID_DURATION_S -> SessionQuality.EXCLUDED
            missingRate >= DrivingConfig.LIMITED_MISSING_RATE || !session.terminatedNormally -> SessionQuality.LIMITED
            else -> SessionQuality.GOOD
        }
        val attributed = session.attribution == Attribution.VERIFIED || session.attribution == Attribution.SELF_CONFIRMED
        val valid = attributed &&
            durationS >= DrivingConfig.MIN_VALID_DURATION_S &&
            (distanceKm == null || distanceKm >= DrivingConfig.MIN_VALID_DISTANCE_KM)

        return SessionFeatures(
            driveId = session.driveId,
            algorithmVersion = DrivingConfig.ALGORITHM_VERSION,
            durationS = durationS,
            distanceKm = distanceKm,
            nightShare = nightShare(session),
            speedBandSeconds = bands,
            harshDecelCount = events?.size,
            harshDecelByBand = eventsByBand,
            decelSource = decel?.first,
            coverage = coverage,
            missingRate = missingRate,
            maxGapS = maxGapS,
            droppedSamples = dropped,
            quality = quality,
            valid = valid,
        )
    }

    /** (출처, [(t, 가속도 m/s²)]) — 종방향 가속도가 있으면 그것을, 없으면 5 Hz 이상 속도의 차분을 쓴다. */
    private fun decelSeries(session: DrivingSession, bySignal: Map<SignalId, List<SignalSample>>): Pair<DecelSource, List<Pair<Long, Double>>>? {
        if (SignalId.LONG_ACCEL in session.signals) {
            return DecelSource.LONG_ACCEL to (bySignal[SignalId.LONG_ACCEL] ?: emptyList()).map { it.tMonoMs to it.value }
        }
        val speedHz = session.sampleRateHz[SignalId.SPEED] ?: return null
        if (SignalId.SPEED !in session.signals || speedHz < DrivingConfig.SPEED_DERIVED_MIN_HZ) return null
        val speed = bySignal[SignalId.SPEED] ?: return DecelSource.SPEED_DERIVED to emptyList()
        val series = (1 until speed.size).mapNotNull { i ->
            val dt = (speed[i].tMonoMs - speed[i - 1].tMonoMs) / 1000.0
            if (dt <= 0 || dt > DrivingConfig.MAX_SAMPLE_GAP_S) null
            else speed[i].tMonoMs to (speed[i].value - speed[i - 1].value) / 3.6 / dt
        }
        return DecelSource.SPEED_DERIVED to series
    }

    /** 임계값 이하가 최소 지속시간 이상 이어진 구간의 시작 시각. 직전 이벤트 시작과 3초 미만이면 같은 이벤트로 본다. */
    private fun harshDecelStarts(series: List<Pair<Long, Double>>): List<Long> {
        if (series.size < 2) return emptyList()
        val period = series.zipWithNext { a, b -> b.first - a.first }.sorted().let { it[it.size / 2] }
        val starts = mutableListOf<Long>()
        var runStart: Long? = null
        var runEnd = 0L
        fun closeRun() {
            val s = runStart ?: return
            val lengthS = (runEnd - s + period) / 1000.0
            val separated = starts.isEmpty() || (s - starts.last()) / 1000.0 >= DrivingConfig.HARSH_DECEL_MIN_SEPARATION_S
            if (lengthS >= DrivingConfig.HARSH_DECEL_MIN_DURATION_S - 1e-9 && separated) starts += s
            runStart = null
        }
        for ((t, a) in series) {
            if (a <= DrivingConfig.HARSH_DECEL_MS2) {
                if (runStart == null) runStart = t
                runEnd = t
            } else {
                closeRun()
            }
        }
        closeRun()
        return starts
    }

    private fun nearest(speed: List<SignalSample>, t: Long): SignalSample =
        speed.minBy { kotlin.math.abs(it.tMonoMs - t) }

    /** 차량 현지 시각 19:00–06:00에 해당하는 주행 시간 비율(초 단위 적분). 위치·일몰은 쓰지 않는다. */
    private fun nightShare(session: DrivingSession): Double {
        val total = Duration.between(session.startedAt, session.endedAt).seconds
        if (total <= 0) return 0.0
        var night = 0L
        var t = session.startedAt
        while (t.isBefore(session.endedAt)) {
            val hour = t.atZone(session.zone).hour
            if (hour >= DrivingConfig.NIGHT_START_HOUR || hour < DrivingConfig.NIGHT_END_HOUR) night++
            t = t.plusSeconds(1)
        }
        return (night.toDouble() / total * 1000).roundToLong() / 1000.0
    }
}
