package com.neulbom.drive.data.local

import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.domain.consent.CONSENT_POLICY_VERSION
import com.neulbom.drive.domain.consent.ConsentRecord
import com.neulbom.drive.domain.consent.ConsentRepository
import com.neulbom.drive.domain.consent.ConsentState
import com.neulbom.drive.domain.consent.ConsentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 동의 저장소(Room). 메모리 상태를 **철회는 DB보다 먼저, 동의는 DB 이후에** 갱신해
 * 어떤 순간에도 "철회했는데 수집되는" 창이 생기지 않게 한다(T03).
 * 로딩 완료 전에는 ConsentState.NONE(전부 미동의)이다.
 */
class RoomConsentRepository(
    private val dao: ConsentDao,
    private val clock: Clock,
    scope: CoroutineScope,
) : ConsentRepository {
    private val flow = MutableStateFlow(ConsentState.NONE)
    override val state: StateFlow<ConsentState> = flow.asStateFlow()

    init {
        scope.launch {
            val latest = dao.history().mapNotNull { it.toDomainOrNull() }.groupBy { it.type }.mapValues { (_, v) -> v.first() }
            flow.value = ConsentState(latest + flow.value.records)
        }
    }

    override suspend fun update(type: ConsentType, granted: Boolean) {
        val record = ConsentRecord(type, granted, CONSENT_POLICY_VERSION, clock.now())
        if (!granted) flow.value = ConsentState(flow.value.records + (type to record))
        dao.insert(record.toEntity())
        if (granted) flow.value = ConsentState(flow.value.records + (type to record))
    }

    override suspend fun history(): List<ConsentRecord> = dao.history().mapNotNull { it.toDomainOrNull() }
}
