package com.neulbom.drive.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 타입 변환기 없이 원시 타입(Long/String)으로만 저장한다. 시각은 epoch millis(UTC).
// 목적지 문자열·좌표·음성에 해당하는 컬럼은 존재하지 않는다.

@Entity(tableName = "driver_consent")
data class DriverConsentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val granted: Boolean,
    val policyVersion: Int,
    val changedAt: Long,
)

@Entity(tableName = "assessment_session", indices = [Index("startedAt"), Index("syncState")])
data class AssessmentSessionEntity(
    @PrimaryKey val assessmentId: String,
    val startedAt: Long,
    val endedAt: Long?,
    /** IN_PROGRESS | COMPLETED | INTERRUPTED | CANCELLED */
    val status: String,
    val stopReason: String?,
    val isFirstAssessment: Boolean,
    /** "TASK_ID:version;…" */
    val protocolVersions: String,
    val appVersion: String,
    val deviceProfile: String,
    /** IN_PROGRESS | LOCAL_PENDING_SYNC | SERVER_SAVED */
    val syncState: String,
)

/** 시행 원자료(명세 v0.6 04 §3 assessment_trials). 단조 시각(ms) 기준. */
@Entity(tableName = "assessment_trial", indices = [Index("assessmentId")])
data class AssessmentTrialEntity(
    @PrimaryKey val trialId: String,
    val assessmentId: String,
    val taskId: String,
    val taskVersion: Int,
    val stimulusSetVersion: Int,
    val trialIndex: Int,
    val block: String,
    /** 자극 요약(예: ARROW_LEFT, GO, NOGO, DIGIT_7_PARITY) */
    val trialType: String,
    val scheduledOnsetMs: Long,
    val actualOnsetMs: Long?,
    val responseMs: Long?,
    val reactionTimeMs: Long?,
    val responseCode: String?,
    val outcome: String,
    val isPractice: Boolean,
    val displayLatencyMs: Long,
)


/** 주행 세션 대기열(업로드 전 영구 저장). 위치·경로 컬럼 없음. */
@Entity(tableName = "driving_session", indices = [Index("uploadState"), Index("startedAt")])
data class DrivingSessionEntity(
    @PrimaryKey val driveId: String,
    val startedAt: Long,
    val endedAt: Long,
    val zone: String,
    val origin: String,
    /** 쉼표로 구분한 SignalId 이름 */
    val signals: String,
    /** "SIGNAL:hz;…" */
    val rates: String,
    val attribution: String,
    val terminatedNormally: Boolean,
    /** PENDING | UPLOADED | FAILED */
    val uploadState: String,
)

@Entity(tableName = "driving_sample_chunk", primaryKeys = ["driveId", "chunkIndex"])
data class DrivingSampleChunkEntity(
    val driveId: String,
    val chunkIndex: Int,
    val encoded: String,
)

/** 점검 제안 기록(명세 v0.6 03 §4). 제안 시각과 응답만 — 점검 결과는 두지 않는다. */
@Entity(tableName = "scheduling_event", indices = [Index("offeredAt")])
data class SchedulingEventEntity(
    @PrimaryKey val offerId: String,
    val offeredAt: Long,
    /** null | ACCEPTED | LATER | NOT_TODAY */
    val response: String?,
    val respondedAt: Long?,
    /** 시연용 주차 제안이면 true(mock 플래버). */
    val simulated: Boolean,
)
