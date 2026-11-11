package com.neulbom.drive.domain.consent

import java.time.Duration
import java.time.Instant

/** 로컬 기록 보존기간. 기간이 지난 기록은 앱 시작 시 자동 삭제한다. */
data class RetentionPolicy(val days: Int = DEFAULT_DAYS) {
    init {
        require(days in OPTIONS) { "unsupported retention: $days" }
    }

    fun cutoff(now: Instant): Instant = now.minus(Duration.ofDays(days.toLong()))

    companion object {
        const val DEFAULT_DAYS = 180
        val OPTIONS = listOf(30, 90, 180, 365)
    }
}
