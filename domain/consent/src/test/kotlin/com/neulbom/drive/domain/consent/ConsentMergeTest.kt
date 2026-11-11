package com.neulbom.drive.domain.consent

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ConsentMergeTest {
    private fun r(type: ConsentType, granted: Boolean, at: String) = ConsentRecord(type, granted, 1, Instant.parse(at))
    private val local = mapOf(
        ConsentType.DRIVING_RECORD to r(ConsentType.DRIVING_RECORD, true, "2026-11-01T01:00:00Z"),
        ConsentType.COGNITIVE_CHECK to r(ConsentType.COGNITIVE_CHECK, true, "2026-11-01T05:00:00Z"),
    )

    @Test fun remoteNewerDifferentIsApplied() {
        val remote = listOf(r(ConsentType.DRIVING_RECORD, false, "2026-11-01T03:00:00Z"))
        assertEquals(remote, ConsentMerge.toApply(local, remote))
    }

    @Test fun remoteOlderOrSameValueIsIgnored() {
        val remote = listOf(
            r(ConsentType.COGNITIVE_CHECK, false, "2026-11-01T04:00:00Z"), // 차량에서 나중에 켬 → 차량이 이김
            r(ConsentType.DRIVING_RECORD, true, "2026-11-01T09:00:00Z"), // 같은 값
        )
        assertEquals(emptyList(), ConsentMerge.toApply(local, remote))
    }

    @Test fun onlyLatestRemotePerTypeCounts() {
        val remote = listOf(
            r(ConsentType.DRIVING_RECORD, false, "2026-11-01T03:00:00Z"),
            r(ConsentType.DRIVING_RECORD, true, "2026-11-01T04:00:00Z"),
        )
        assertEquals(emptyList(), ConsentMerge.toApply(local, remote))
    }

    @Test fun localNewerIsPushed() {
        val remote = listOf(r(ConsentType.COGNITIVE_CHECK, false, "2026-11-01T04:00:00Z"))
        assertEquals(local.values.toSet(), ConsentMerge.toPush(local, remote).toSet()) // DRIVING_RECORD는 서버에 없음
        assertEquals(emptyList(), ConsentMerge.toPush(emptyMap(), remote))
    }
}
