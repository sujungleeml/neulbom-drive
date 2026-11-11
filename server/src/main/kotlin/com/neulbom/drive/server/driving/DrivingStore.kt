package com.neulbom.drive.server.driving

import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.domain.driving.SessionFeatures
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** 시연용 단일 사용자("me") 저장소. 고빈도 표본은 특징량 계산 후 보관하지 않는다. */
interface DrivingStore {
    /** @return 새로 저장했으면 true, 같은 driveId가 이미 있으면 false(기존 값 유지). */
    fun upsert(session: DrivingSession, features: SessionFeatures): Boolean
    fun get(driveId: String): Pair<DrivingSession, SessionFeatures>?
    fun all(): List<Pair<DrivingSession, SessionFeatures>>
    fun putSignalSupport(list: List<SignalSupportDto>)
    fun signalSupport(): List<SignalSupportDto>
    fun deleteAll()
    /** 보존 기간이 지난 세션(시작 시각 기준)을 지운다. @return 지운 수 */
    fun deleteStartedBefore(cutoff: Instant): Int
}

class InMemoryDrivingStore : DrivingStore {
    private val sessions = ConcurrentHashMap<String, Pair<DrivingSession, SessionFeatures>>()
    @Volatile private var support: List<SignalSupportDto> = emptyList()

    override fun upsert(session: DrivingSession, features: SessionFeatures): Boolean =
        sessions.putIfAbsent(session.driveId, session to features) == null

    override fun get(driveId: String) = sessions[driveId]
    override fun all() = sessions.values.sortedBy { it.first.startedAt }
    override fun putSignalSupport(list: List<SignalSupportDto>) { support = list }
    override fun signalSupport() = support
    override fun deleteAll() = sessions.clear()
    override fun deleteStartedBefore(cutoff: Instant): Int {
        val old = sessions.filterValues { it.first.startedAt.isBefore(cutoff) }.keys
        old.forEach(sessions::remove)
        return old.size
    }
}
