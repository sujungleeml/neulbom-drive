package com.neulbom.drive.vehicle.api

/**
 * 어댑터가 실제로 제공할 수 있는 기능. 공식 SDK 문서로 확인되지 않은 기능은 플래그를 켜지 않는다.
 * 앱은 플래그가 없으면 해당 기능을 "사용 불가"로 처리한다(안전 기본값).
 */
enum class CapabilityFlag {
    MOTION_STATE,
    PARKING_STATE,
    STATE_STREAM,
    POWER_STATE,
    DRIVER_IDENTITY,
    NAVI_HANDOFF,
    LOCATION,
    SPEECH_TO_TEXT,
    TEXT_TO_SPEECH,
}
