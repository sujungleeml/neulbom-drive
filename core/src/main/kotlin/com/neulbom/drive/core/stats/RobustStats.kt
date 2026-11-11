package com.neulbom.drive.core.stats

import kotlin.math.abs

/** 결정적(정렬 기반) 로버스트 통계. 같은 입력이면 항상 같은 결과(T05). */
object RobustStats {
    /** MAD를 정규분포 표준편차 척도로 맞추는 상수(PRD 5절 C). */
    const val MAD_SCALE = 1.4826

    fun median(values: List<Double>): Double {
        require(values.isNotEmpty()) { "median of empty list" }
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /** 중앙값 절대 편차(척도 보정 전). */
    fun mad(values: List<Double>, center: Double = median(values)): Double =
        median(values.map { abs(it - center) })

    fun robustZ(x: Double, median: Double, mad: Double, epsilon: Double): Double =
        (x - median) / (MAD_SCALE * mad + epsilon)
}
