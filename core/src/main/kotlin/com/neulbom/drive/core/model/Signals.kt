package com.neulbom.drive.core.model

import java.time.Instant
import java.time.ZoneId

/** 주행 신호(명세 v0.6 08 §1). 가속 페달 입력과 종방향 가속도는 서로 다른 신호다. */
enum class SignalId { VEHICLE_STATE, SPEED, BRAKE, ACCEL_PEDAL, LONG_ACCEL, STEERING, GEAR }

enum class SupportStatus { SUPPORTED, UNVERIFIED, UNSUPPORTED }

enum class VerifiedOn { REAL_VEHICLE, EMULATOR, NONE }

/** 신호별 지원 근거. 공식 문서·재현 근거 없이 SUPPORTED로 두지 않는다. */
data class SignalSupport(
    val signal: SignalId,
    val status: SupportStatus,
    val sdkName: String? = null,
    val docRef: String? = null,
    val sampleRateHz: Double? = null,
    val background: Boolean? = null,
    val verifiedOn: VerifiedOn = VerifiedOn.NONE,
)

/** 운전자 귀속 상태. 개인 리포트에는 VERIFIED·SELF_CONFIRMED만 포함한다. */
enum class Attribution { VERIFIED, SELF_CONFIRMED, UNVERIFIED, CONFLICTED }

/** 신호 표본. 시각은 세션 내 단조 시각(ms). 단위: SPEED km/h, LONG_ACCEL m/s². */
data class SignalSample(val signal: SignalId, val tMonoMs: Long, val value: Double)

/** 주행 세션 메타데이터. 위치·경로·목적지 필드는 두지 않는다. */
data class DrivingSession(
    val driveId: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val zone: ZoneId,
    val origin: DataOrigin,
    val signals: Set<SignalId>,
    val sampleRateHz: Map<SignalId, Double>,
    val attribution: Attribution,
    val terminatedNormally: Boolean,
)
