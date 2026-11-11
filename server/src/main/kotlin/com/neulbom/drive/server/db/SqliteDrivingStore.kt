package com.neulbom.drive.server.db

import com.neulbom.drive.core.model.Attribution
import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.domain.driving.DecelSource
import com.neulbom.drive.domain.driving.SessionFeatures
import com.neulbom.drive.domain.driving.SessionQuality
import com.neulbom.drive.domain.driving.SpeedBand
import com.neulbom.drive.server.driving.DrivingStore
import com.neulbom.drive.server.driving.SignalSupportDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneId

/** driving_sessions + driving_session_features(1:1). 세션과 특징량은 한 트랜잭션으로 저장한다. */
class SqliteDrivingStore(private val db: SqliteDb, private val userId: String = DEFAULT_USER) : DrivingStore {

    override fun upsert(session: DrivingSession, features: SessionFeatures): Boolean = db.tx {
        if (query("SELECT 1 FROM driving_sessions WHERE drive_id = ?", session.driveId) { true }.isNotEmpty()) return@tx false
        update(
            "INSERT INTO driving_sessions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            userId, session.driveId, session.startedAt.toString(), session.endedAt.toString(), session.zone.id, session.origin.name,
            Json.encodeToString(session.signals.map { it.name }.sorted()),
            Json.encodeToString(session.sampleRateHz.mapKeys { it.key.name }),
            session.attribution.name, if (session.terminatedNormally) 1 else 0,
        )
        update(
            "INSERT INTO driving_session_features VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            features.driveId, features.algorithmVersion, features.durationS, features.distanceKm, features.nightShare,
            features.speedBandSeconds?.let { Json.encodeToString(it.mapKeys { k -> k.key.name }) },
            features.harshDecelCount,
            features.harshDecelByBand?.let { Json.encodeToString(it.mapKeys { k -> k.key.name }) },
            features.decelSource?.name, features.coverage, features.missingRate, features.maxGapS, features.droppedSamples,
            features.quality.name, if (features.valid) 1 else 0,
        )
        true
    }

    override fun get(driveId: String) = select("WHERE s.user_id = ? AND s.drive_id = ?", userId, driveId).firstOrNull()

    override fun all() = select("WHERE s.user_id = ? ORDER BY s.started_at", userId)

    override fun putSignalSupport(list: List<SignalSupportDto>) {
        db.update("INSERT OR REPLACE INTO signal_support VALUES (?, ?)", userId, Json.encodeToString(list))
    }

    override fun signalSupport(): List<SignalSupportDto> =
        db.query("SELECT list_json FROM signal_support WHERE user_id = ?", userId) { Json.decodeFromString<List<SignalSupportDto>>(it.getString(1)) }.firstOrNull() ?: emptyList()

    override fun deleteAll() {
        db.tx { update("DELETE FROM driving_sessions WHERE user_id = ?", userId) }
    }

    // 시각은 ISO-8601 UTC 문자열이라 문자열 비교가 시간 순서와 같다(Instant.toString 형식).
    override fun deleteStartedBefore(cutoff: Instant): Int =
        db.tx { update("DELETE FROM driving_sessions WHERE user_id = ? AND started_at < ?", userId, cutoff.toString()) }

    private fun select(where: String, vararg args: Any?): List<Pair<DrivingSession, SessionFeatures>> = db.query(
        "SELECT s.*, f.* FROM driving_sessions s JOIN driving_session_features f ON f.drive_id = s.drive_id $where",
        *args,
    ) { rs -> rs.session() to rs.features() }

    private fun ResultSet.session() = DrivingSession(
        driveId = getString("drive_id"),
        startedAt = Instant.parse(getString("started_at")),
        endedAt = Instant.parse(getString("ended_at")),
        zone = ZoneId.of(getString("zone")),
        origin = DataOrigin.valueOf(getString("origin")),
        signals = Json.decodeFromString<List<String>>(getString("signal_set")).map { SignalId.valueOf(it) }.toSet(),
        sampleRateHz = Json.decodeFromString<Map<String, Double>>(getString("sample_rate_hz")).mapKeys { SignalId.valueOf(it.key) },
        attribution = Attribution.valueOf(getString("attribution")),
        terminatedNormally = getInt("terminated_normally") == 1,
    )

    private fun ResultSet.features() = SessionFeatures(
        driveId = getString("drive_id"),
        algorithmVersion = getString("algorithm_version"),
        durationS = getDouble("duration_s"),
        distanceKm = nullableDouble("distance_km"),
        nightShare = getDouble("night_share"),
        speedBandSeconds = getString("speed_band_seconds")?.let { j -> Json.decodeFromString<Map<String, Double>>(j).mapKeys { SpeedBand.valueOf(it.key) } },
        harshDecelCount = nullableInt("harsh_decel_count"),
        harshDecelByBand = getString("harsh_decel_by_band")?.let { j -> Json.decodeFromString<Map<String, Int>>(j).mapKeys { SpeedBand.valueOf(it.key) } },
        decelSource = getString("decel_source")?.let { DecelSource.valueOf(it) },
        coverage = getDouble("coverage"),
        missingRate = getDouble("missing_rate"),
        maxGapS = getDouble("max_gap_s"),
        droppedSamples = getInt("dropped_samples"),
        quality = SessionQuality.valueOf(getString("quality")),
        valid = getInt("valid") == 1,
    )

    companion object {
        /** 인증이 붙기 전 단일 사용자. 인증 후에는 토큰의 사용자 ID를 넘긴다. */
        const val DEFAULT_USER = "me"
    }
}

internal fun ResultSet.nullableDouble(col: String): Double? = getDouble(col).takeUnless { wasNull() }
internal fun ResultSet.nullableInt(col: String): Int? = getInt(col).takeUnless { wasNull() }
internal fun ResultSet.nullableLong(col: String): Long? = getLong(col).takeUnless { wasNull() }
