package com.neulbom.drive.core.model

/** PRD 4절 권장 task_type. */
enum class TaskType {
    FAVORITE_SELECT,
    DESTINATION_SEARCH,
    VOICE_COMMAND_IN_APP,
    REACTION_TASK,
    SWITCH_TASK,
}


/**
 * 차량 안전 상태(명세 v0.5 06 Phase 1). 점검은 PARKED_SAFE에서만 시작·지속할 수 있다.
 * PARKED_UNVERIFIED는 정지했지만 주차가 검증되지 않은 상태(속도 0만으로는 주차가 아님).
 */
enum class SafetyState { DRIVING, PARKED_SAFE, PARKED_UNVERIFIED, POWERING_OFF }


/** 합성 데이터와 실측 데이터를 명확히 구분한다(마스터 지시문 6). */
enum class DataOrigin { REAL, MOCK }
