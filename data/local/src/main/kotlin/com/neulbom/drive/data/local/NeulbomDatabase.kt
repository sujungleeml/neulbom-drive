package com.neulbom.drive.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DriverConsentEntity::class,
        AssessmentSessionEntity::class,
        AssessmentTrialEntity::class,
        DrivingSessionEntity::class,
        DrivingSampleChunkEntity::class,
        SchedulingEventEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class NeulbomDatabase : RoomDatabase() {
    abstract fun consentDao(): ConsentDao
    abstract fun assessmentDao(): AssessmentDao
    abstract fun drivingDao(): DrivingDao
    abstract fun schedulingDao(): SchedulingDao

    companion object {
        const val NAME = "neulbom.db"

        // TODO(SECURITY): 로컬 DB 암호화(SQLCipher 등) 적용 검토 — PRD 7절 "로컬 데이터 암호화 검토".
        fun create(context: Context): NeulbomDatabase =
            Room.databaseBuilder(context.applicationContext, NeulbomDatabase::class.java, NAME)
                // 시연 단계: 스키마 변경 시 로컬 기록 초기화(명세 v0.5 전환, ADR-03·05)
                .fallbackToDestructiveMigration()
                .build()
    }
}
