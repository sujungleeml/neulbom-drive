package com.neulbom.drive.server.settings

import com.neulbom.drive.domain.consent.RetentionPolicy
import com.neulbom.drive.server.checks.CheckStore
import com.neulbom.drive.server.driving.DrivingStore
import java.time.Instant

/**
 * 보존 기간이 지난 주행·점검 기록을 지운다(명세 04 §5, 두 축 같은 규칙). 동의 변경 이력은 감사용으로 남긴다.
 * @return 지운 주행 세션 수와 점검 수
 */
fun purgeExpired(driving: DrivingStore, checks: CheckStore, now: Instant, policy: RetentionPolicy = RetentionPolicy()): Pair<Int, Int> {
    val cutoff = policy.cutoff(now)
    return driving.deleteStartedBefore(cutoff) to checks.deleteStartedBefore(cutoff)
}
