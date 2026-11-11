package com.neulbom.drive.core.time

import java.time.Instant

/**
 * 시간 제공자. 모든 도메인 로직은 System 시간을 직접 읽지 않고 이 인터페이스를 주입받는다(PRD 7절 테스트 가능성).
 */
interface Clock {
    /** 벽시계 시각(UTC). 이벤트 timestamp, 쿨다운 계산에 사용. */
    fun now(): Instant

    /** 단조 증가 시각(ms). 반응 시간 등 경과 시간 측정에만 사용. */
    fun monotonicMs(): Long
}

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun monotonicMs(): Long = System.nanoTime() / 1_000_000
}

/** 테스트·합성 데이터 재생용 수동 시계. */
class FakeClock(
    private var instant: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    private var monotonic: Long = 0L,
) : Clock {
    override fun now(): Instant = instant
    override fun monotonicMs(): Long = monotonic

    fun advanceMs(ms: Long) {
        instant = instant.plusMillis(ms)
        monotonic += ms
    }

    fun set(newInstant: Instant) {
        val delta = newInstant.toEpochMilli() - instant.toEpochMilli()
        instant = newInstant
        if (delta > 0) monotonic += delta
    }
}
