package com.neulbom.drive.domain.driving

import com.neulbom.drive.core.model.Attribution
import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.core.model.SignalId
import java.time.Instant
import java.time.ZoneId

internal val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
internal val FULL_SIGNALS = setOf(SignalId.VEHICLE_STATE, SignalId.SPEED, SignalId.LONG_ACCEL)

/** 특징량을 직접 지정한 (세션, 특징량) 쌍. */
internal fun item(
    id: String,
    start: String,
    durationS: Double = 1800.0,
    distanceKm: Double? = 15.0,
    harsh: Int? = 0,
    night: Double = 0.0,
    quality: SessionQuality = SessionQuality.GOOD,
    valid: Boolean = true,
    signals: Set<SignalId> = FULL_SIGNALS,
    origin: DataOrigin = DataOrigin.MOCK,
    version: String = DrivingConfig.ALGORITHM_VERSION,
): Pair<DrivingSession, SessionFeatures> {
    val s = Instant.parse(start)
    val session = DrivingSession(id, s, s.plusSeconds(durationS.toLong()), SEOUL, origin, signals, signals.associateWith { 10.0 }, Attribution.VERIFIED, true)
    val f = SessionFeatures(
        id, version, durationS, distanceKm, night,
        if (distanceKm == null) null else SpeedBand.entries.associateWith { 0.0 },
        harsh, null, if (harsh == null) null else DecelSource.LONG_ACCEL, 1.0, 0.0, 0.1, 0, quality, valid,
    )
    return session to f
}

/** 해당 월에 days일, 하루 한 번 km씩 주행한 목록. */
internal fun month(prefix: String, days: Int, km: Double, signals: Set<SignalId> = FULL_SIGNALS, version: String = DrivingConfig.ALGORITHM_VERSION, durationS: Double = 1800.0) =
    (1..days).map { d -> item("$prefix-$d", "$prefix-${"%02d".format(d)}T01:00:00Z", distanceKm = km, signals = signals, version = version, durationS = durationS) }
