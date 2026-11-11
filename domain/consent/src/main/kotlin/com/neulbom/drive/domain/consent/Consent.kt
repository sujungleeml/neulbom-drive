package com.neulbom.drive.domain.consent

import java.time.Instant

/** 동의는 목적별로 분리한다(명세 v0.6 04). 기본값은 모두 미동의. */
enum class ConsentType {
    /** 허용된 차량 API 범위 안의 주행 습관 기록(명세 v0.6 08). 위치·경로·목적지는 수집하지 않는다. */
    DRIVING_RECORD,

    /** 주차 중 선택적 주의·반응 점검 수행과 기록 저장. */
    COGNITIVE_CHECK,
}

data class ConsentRecord(
    val type: ConsentType,
    val granted: Boolean,
    val policyVersion: Int,
    val changedAt: Instant,
)

data class ConsentState(val records: Map<ConsentType, ConsentRecord> = emptyMap()) {
    fun isGranted(type: ConsentType): Boolean = records[type]?.granted == true

    companion object {
        val NONE = ConsentState()
    }
}

/** 동의 문서 버전. 문구가 바뀌면 올리고 재동의를 받는다. */
const val CONSENT_POLICY_VERSION = 1
