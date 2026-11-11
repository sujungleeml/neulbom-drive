package com.neulbom.drive.data.local

import com.neulbom.drive.domain.consent.RetentionPolicy
import java.time.Instant

/** 로컬 기록 삭제·보존기간 정리(명세 v0.6 04 §5). 동의 변경 감사 이력은 남긴다. */
class LocalDataManager(private val db: NeulbomDatabase, private val driving: RoomDrivingQueue) {
    suspend fun deleteAllRecords() {
        db.assessmentDao().deleteAllTrials()
        db.assessmentDao().deleteAllSessions()
        driving.deleteAll()
        db.schedulingDao().deleteAll()
    }

    suspend fun purgeExpired(policy: RetentionPolicy, now: Instant) {
        val cutoff = policy.cutoff(now).toEpochMilli()
        db.assessmentDao().deleteTrialsOlderThan(cutoff)
        db.assessmentDao().deleteSessionsOlderThan(cutoff)
    }
}
