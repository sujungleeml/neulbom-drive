package com.neulbom.drive.server.checks

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** 시연용 단일 사용자 점검 기록 저장소(메모리). */
interface CheckStore {
    /** @return 새로 저장했으면 true, 같은 assessmentId가 이미 있으면 false(기존 값 유지). */
    fun putIfAbsent(upload: AssessmentUploadDto): Boolean
    fun get(assessmentId: String): AssessmentUploadDto?
    fun all(): List<AssessmentUploadDto>
    fun deleteAll()
    /** 보존 기간이 지난 점검(시작 시각 기준)을 지운다. @return 지운 수 */
    fun deleteStartedBefore(cutoff: Instant): Int
}

class InMemoryCheckStore : CheckStore {
    private val map = ConcurrentHashMap<String, AssessmentUploadDto>()
    override fun putIfAbsent(upload: AssessmentUploadDto) = map.putIfAbsent(upload.session.assessmentId, upload) == null
    override fun get(assessmentId: String) = map[assessmentId]
    override fun all() = map.values.sortedBy { it.session.startedAt }
    override fun deleteAll() = map.clear()
    override fun deleteStartedBefore(cutoff: Instant): Int {
        val old = map.filterValues { Instant.parse(it.session.startedAt).isBefore(cutoff) }.keys
        old.forEach(map::remove)
        return old.size
    }
}
