package com.neulbom.drive.data.local

import androidx.room.withTransaction
import com.neulbom.drive.core.model.Attribution
import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.model.SignalSample
import com.neulbom.drive.data.sync.UploadQueue
import com.neulbom.drive.domain.driving.DrivingSink
import com.neulbom.drive.domain.driving.SampleCodec
import java.time.Instant
import java.time.ZoneId

/** 주행 세션을 업로드 전에 먼저 영구 저장하는 대기열(명세 v0.6 04 §5, durable queue). */
class RoomDrivingQueue(private val db: NeulbomDatabase) : DrivingSink, UploadQueue {
    private val dao = db.drivingDao()

    override suspend fun saveSession(session: DrivingSession, samples: List<SignalSample>) {
        db.withTransaction {
            dao.insertSession(session.toEntity("PENDING"))
            dao.insertChunks(SampleCodec.encodeChunks(samples).mapIndexed { i, text -> DrivingSampleChunkEntity(session.driveId, i, text) })
        }
    }

    override suspend fun pending(): List<Pair<DrivingSession, List<SignalSample>>> =
        dao.pending().map { e -> e.toDomain() to dao.chunks(e.driveId).flatMap { SampleCodec.decode(it.encoded) } }

    override suspend fun markUploaded(driveId: String) {
        db.withTransaction {
            dao.setState(driveId, "UPLOADED")
            dao.deleteChunks(driveId)
        }
    }

    suspend fun deleteAll() {
        dao.deleteAllChunks()
        dao.deleteAllSessions()
    }

    private fun DrivingSession.toEntity(state: String) = DrivingSessionEntity(
        driveId = driveId,
        startedAt = startedAt.toEpochMilli(),
        endedAt = endedAt.toEpochMilli(),
        zone = zone.id,
        origin = origin.name,
        signals = signals.sortedBy { it.ordinal }.joinToString(",") { it.name },
        rates = sampleRateHz.entries.sortedBy { it.key.ordinal }.joinToString(";") { "${it.key.name}:${it.value}" },
        attribution = attribution.name,
        terminatedNormally = terminatedNormally,
        uploadState = state,
    )

    private fun DrivingSessionEntity.toDomain() = DrivingSession(
        driveId = driveId,
        startedAt = Instant.ofEpochMilli(startedAt),
        endedAt = Instant.ofEpochMilli(endedAt),
        zone = ZoneId.of(zone),
        origin = DataOrigin.valueOf(origin),
        signals = if (signals.isEmpty()) emptySet() else signals.split(',').map { SignalId.valueOf(it) }.toSet(),
        sampleRateHz = if (rates.isEmpty()) emptyMap() else rates.split(';').associate { p -> p.split(':').let { SignalId.valueOf(it[0]) to it[1].toDouble() } },
        attribution = Attribution.valueOf(attribution),
        terminatedNormally = terminatedNormally,
    )
}
