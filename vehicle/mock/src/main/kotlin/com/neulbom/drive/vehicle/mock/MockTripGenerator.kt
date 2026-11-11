package com.neulbom.drive.vehicle.mock

import com.neulbom.drive.core.model.Attribution
import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.model.SignalSample
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** 모의 주행 유형(평균 속도 km/h). */
enum class TripType(val meanKmh: Double) { CITY(25.0), ROAD(60.0), HIGHWAY(95.0) }

data class MockTrip(val session: DrivingSession, val samples: List<SignalSample>)

/**
 * 시드 고정 **모의** 주행 생성기(명세 v0.6 08 §7). 실제 차량 데이터가 아니며 모두 `DataOrigin.MOCK`.
 * 평상시 가감속은 ±1.2 m/s² 이내로 두고, 요청한 횟수만큼만 급감속(−4.5 m/s², 0.8초)을 넣는다.
 */
object MockTripGenerator {
    const val SAMPLE_HZ = 10.0
    private const val STEP_MS = 100L
    private const val NORMAL_ACCEL_MS2 = 1.2
    private const val HARSH_MS2 = -4.5
    private const val HARSH_STEPS = 8

    fun generate(
        driveId: String,
        type: TripType,
        start: Instant,
        durationS: Int,
        zone: ZoneId,
        seed: Long,
        signals: Set<SignalId>,
        harshDecelCount: Int = 0,
    ): MockTrip {
        val random = Random(seed)
        val n = (durationS * 1000L / STEP_MS).toInt() + 1
        val speed = DoubleArray(n)
        val dvNormal = NORMAL_ACCEL_MS2 * 3.6 * STEP_MS / 1000.0
        var target = type.meanKmh
        var nextChange = 0
        for (i in 1 until n) {
            if (i >= nextChange) {
                val stop = type == TripType.CITY && random.nextDouble() < 0.25
                target = if (stop) 0.0 else type.meanKmh * (0.6 + random.nextDouble() * 0.8)
                nextChange = i + 200 + random.nextInt(400) // 20~60초마다 목표 속도 변경
            }
            val prev = speed[i - 1]
            speed[i] = if (prev < target) min(target, prev + dvNormal) else max(target, prev - dvNormal)
        }
        // 급감속: 30 km/h 이상 구간에서, 서로 15초 이상 떨어지게 고르게 배치
        val minGap = 150
        val eligible = (100 until n - HARSH_STEPS - 50).filter { speed[it] >= 30.0 }
        val chosen = mutableListOf<Int>()
        if (harshDecelCount > 0) {
            val stride = max(1, eligible.size / (harshDecelCount + 1))
            var k = stride
            while (chosen.size < harshDecelCount && k < eligible.size) {
                val idx = eligible[k]
                if (chosen.isEmpty() || idx - chosen.last() >= minGap) chosen += idx
                k += stride / 2 + 1
            }
            require(chosen.size == harshDecelCount) { "trip too short or slow for $harshDecelCount harsh decel events" }
        }
        val dvHarsh = -HARSH_MS2 * 3.6 * STEP_MS / 1000.0
        for (idx in chosen) {
            for (j in 1..HARSH_STEPS) speed[idx + j] = speed[idx + j - 1] - dvHarsh
            // 이후 속도는 평상 가속(±1.2 m/s²)으로 원래 흐름에 다시 합류
            for (j in idx + HARSH_STEPS + 1 until n) {
                val prev = speed[j - 1]
                val natural = speed[j]
                if (prev >= natural) break
                speed[j] = min(natural, prev + dvNormal)
            }
        }
        val samples = buildList {
            for (i in 0 until n) {
                val t = i * STEP_MS
                val accel = if (i == 0) 0.0 else (speed[i] - speed[i - 1]) / 3.6 / (STEP_MS / 1000.0)
                if (SignalId.SPEED in signals) add(SignalSample(SignalId.SPEED, t, speed[i]))
                if (SignalId.LONG_ACCEL in signals) add(SignalSample(SignalId.LONG_ACCEL, t, accel))
                if (SignalId.BRAKE in signals) add(SignalSample(SignalId.BRAKE, t, if (accel < -0.5) 1.0 else 0.0))
                if (SignalId.ACCEL_PEDAL in signals) add(SignalSample(SignalId.ACCEL_PEDAL, t, if (accel > 0.1) min(100.0, accel * 40) else 0.0))
            }
        }
        val rates = signals.filter { it != SignalId.VEHICLE_STATE }.associateWith { SAMPLE_HZ }
        val session = DrivingSession(
            driveId = driveId,
            startedAt = start,
            endedAt = start.plusSeconds(durationS.toLong()),
            zone = zone,
            origin = DataOrigin.MOCK,
            signals = signals,
            sampleRateHz = rates,
            attribution = Attribution.VERIFIED,
            terminatedNormally = true,
        )
        return MockTrip(session, samples)
    }
}

/** 몇 주치 **모의** 주행 기록. 주 3~6일, 하루 1~2회, 일부 야간. */
object MockDrivingHistory {
    fun generate(endDate: LocalDate, weeks: Int, seed: Long, zone: ZoneId, signals: Set<SignalId>): List<MockTrip> {
        val random = Random(seed)
        val first = endDate.minusDays(weeks * 7L - 1)
        val trips = mutableListOf<MockTrip>()
        var day = first
        while (!day.isAfter(endDate)) {
            if (random.nextDouble() < 0.65) {
                val count = if (random.nextDouble() < 0.3) 2 else 1
                repeat(count) { k ->
                    val night = random.nextDouble() < 0.15
                    val hour = if (night) 19 + random.nextInt(2) else if (k == 0) 9 + random.nextInt(3) else 14 + random.nextInt(3)
                    val type = when (random.nextInt(10)) { in 0..4 -> TripType.CITY; in 5..7 -> TripType.ROAD; else -> TripType.HIGHWAY }
                    val minutes = 12 + random.nextInt(35)
                    val harsh = when (random.nextInt(10)) { in 0..6 -> 0; in 7..8 -> 1; else -> 2 }
                    val start = day.atTime(hour, random.nextInt(60)).atZone(zone).toInstant()
                    val tripSeed = random.nextLong()
                    // 느린 시내 주행처럼 급감속을 넣을 구간이 부족하면 개수를 줄인다.
                    trips += (harsh downTo 0).firstNotNullOf { h ->
                        runCatching { MockTripGenerator.generate("mock-$day-$k", type, start, minutes * 60, zone, tripSeed, signals, h) }.getOrNull()
                    }
                }
            }
            day = day.plusDays(1)
        }
        return trips
    }
}
