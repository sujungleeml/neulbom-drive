package com.neulbom.drive.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsentDao {
    @Insert
    suspend fun insert(entity: DriverConsentEntity)

    @Query("SELECT * FROM driver_consent ORDER BY changedAt DESC, id DESC")
    suspend fun history(): List<DriverConsentEntity>
}

@Dao
interface AssessmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(entity: AssessmentSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrial(entity: AssessmentTrialEntity)

    @Query("UPDATE assessment_session SET status = :status, endedAt = :endedAt, stopReason = :stopReason, syncState = :syncState WHERE assessmentId = :id")
    suspend fun finish(id: String, status: String, endedAt: Long?, stopReason: String?, syncState: String): Int

    @Query("SELECT * FROM assessment_session WHERE assessmentId = :id")
    suspend fun session(id: String): AssessmentSessionEntity?

    @Query("SELECT * FROM assessment_trial WHERE assessmentId = :id ORDER BY rowid ASC")
    suspend fun trials(id: String): List<AssessmentTrialEntity>

    @Query("SELECT COUNT(*) FROM assessment_session WHERE status = 'COMPLETED'")
    suspend fun countCompleted(): Int

    /** 끝났지만 서버 ACK를 아직 받지 못한 점검(완료·중단·취소). */
    @Query("SELECT * FROM assessment_session WHERE syncState = 'LOCAL_PENDING_SYNC' ORDER BY startedAt ASC")
    suspend fun pendingSync(): List<AssessmentSessionEntity>

    @Query("UPDATE assessment_session SET syncState = 'SERVER_SAVED' WHERE assessmentId = :id")
    suspend fun markServerSaved(id: String): Int

    /** 완료한 점검의 시작 시각(epoch ms). 스케줄러는 시각만 쓴다. */
    @Query("SELECT startedAt FROM assessment_session WHERE status = 'COMPLETED' ORDER BY startedAt ASC")
    suspend fun completedStartTimes(): List<Long>

    @Query("DELETE FROM assessment_trial WHERE assessmentId IN (SELECT assessmentId FROM assessment_session WHERE startedAt < :cutoff)")
    suspend fun deleteTrialsOlderThan(cutoff: Long)

    @Query("DELETE FROM assessment_session WHERE startedAt < :cutoff")
    suspend fun deleteSessionsOlderThan(cutoff: Long)

    @Query("DELETE FROM assessment_trial")
    suspend fun deleteAllTrials()

    @Query("DELETE FROM assessment_session")
    suspend fun deleteAllSessions()
}


@Dao
interface DrivingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(entity: DrivingSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DrivingSampleChunkEntity>)

    @Query("SELECT * FROM driving_session WHERE uploadState = 'PENDING' ORDER BY startedAt ASC")
    suspend fun pending(): List<DrivingSessionEntity>

    @Query("SELECT * FROM driving_sample_chunk WHERE driveId = :driveId ORDER BY chunkIndex ASC")
    suspend fun chunks(driveId: String): List<DrivingSampleChunkEntity>

    @Query("UPDATE driving_session SET uploadState = :state WHERE driveId = :driveId")
    suspend fun setState(driveId: String, state: String)

    /** 업로드가 끝난 세션의 고빈도 표본은 지운다(명세 v0.6 04 §5). */
    @Query("DELETE FROM driving_sample_chunk WHERE driveId = :driveId")
    suspend fun deleteChunks(driveId: String)

    @Query("SELECT COUNT(*) FROM driving_session")
    suspend fun count(): Int

    @Query("DELETE FROM driving_session")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM driving_sample_chunk")
    suspend fun deleteAllChunks()
}

@Dao
interface SchedulingDao {
    @Insert
    suspend fun insert(entity: SchedulingEventEntity)

    @Query("UPDATE scheduling_event SET response = :response, respondedAt = :at WHERE offerId = :offerId AND response IS NULL")
    suspend fun respond(offerId: String, response: String, at: Long): Int

    @Query("SELECT * FROM scheduling_event ORDER BY offeredAt ASC")
    suspend fun all(): List<SchedulingEventEntity>

    @Query("DELETE FROM scheduling_event")
    suspend fun deleteAll()
}
