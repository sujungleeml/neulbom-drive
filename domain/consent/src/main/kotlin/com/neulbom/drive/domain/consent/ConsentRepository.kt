package com.neulbom.drive.domain.consent

import com.neulbom.drive.core.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 항목별 동의/철회 저장소. [state]는 항상 최신 동의 상태를 동기적으로 제공해야 한다
 * (이벤트 저장 직전 확인용). 로딩 전에는 [ConsentState.NONE] — 즉 아무것도 동의하지 않은 것으로 본다.
 */
interface ConsentRepository {
    val state: StateFlow<ConsentState>

    suspend fun update(type: ConsentType, granted: Boolean)

    /** 동의 변경 감사 이력(최신순). */
    suspend fun history(): List<ConsentRecord>
}

class InMemoryConsentRepository(private val clock: Clock) : ConsentRepository {
    private val flow = MutableStateFlow(ConsentState.NONE)
    private val log = mutableListOf<ConsentRecord>()

    override val state: StateFlow<ConsentState> = flow.asStateFlow()

    override suspend fun update(type: ConsentType, granted: Boolean) {
        val record = ConsentRecord(type, granted, CONSENT_POLICY_VERSION, clock.now())
        synchronized(log) { log += record }
        flow.value = ConsentState(flow.value.records + (type to record))
    }

    override suspend fun history(): List<ConsentRecord> = synchronized(log) { log.reversed() }
}
