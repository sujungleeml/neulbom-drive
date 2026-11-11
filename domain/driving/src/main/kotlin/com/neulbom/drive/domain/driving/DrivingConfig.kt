package com.neulbom.drive.domain.driving

/**
 * 주행 지표 설정(명세 v0.6 08). **모두 기술적 정의이자 제품 가설(PROVISIONAL)**이며
 * 임상적·법적 컷오프가 아니다. 운전 적합성·사고 원인 판정에 쓰지 않는다.
 */
object DrivingConfig {
    const val ALGORITHM_VERSION = "drv-0.1.0"

    const val MIN_VALID_DURATION_S = 60.0
    const val MIN_VALID_DISTANCE_KM = 0.2
    const val MAX_SAMPLE_GAP_S = 5.0

    const val HARSH_DECEL_MS2 = -3.5
    const val HARSH_DECEL_MIN_DURATION_S = 0.5
    const val HARSH_DECEL_MIN_SEPARATION_S = 3.0
    const val SPEED_DERIVED_MIN_HZ = 5.0

    const val NIGHT_START_HOUR = 19
    const val NIGHT_END_HOUR = 6

    const val LIMITED_MISSING_RATE = 0.2

    const val DECEL_MIN_PERIOD_KM = 50.0
    const val TREND_MIN_DRIVING_DAYS = 8
    const val TREND_MIN_DISTANCE_KM = 100.0
    const val SIMILAR_RELATIVE_DIFF = 0.10
}
