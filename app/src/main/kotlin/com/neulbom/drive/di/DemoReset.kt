package com.neulbom.drive.di

import com.neulbom.drive.domain.consent.ConsentRepository
import com.neulbom.drive.domain.consent.ConsentType

/**
 * mock 시연 도구 "시연 처음부터": 진행 중 흐름을 멈추고, 기기 안의 점검·주행·제안 기록을 지우고,
 * 동의를 끄고(철회도 감사 이력으로 남김), 온보딩부터 다시 보이게 한다. 서버 기록은 건드리지 않는다.
 */
class DemoReset(
    private val consents: ConsentRepository,
    private val stopFlow: suspend () -> Unit,
    private val deleteLocal: suspend () -> Unit,
    private val resetSettings: () -> Unit,
) {
    suspend fun run() {
        stopFlow()
        deleteLocal()
        ConsentType.entries.forEach { if (consents.state.value.isGranted(it)) consents.update(it, false) }
        resetSettings()
    }
}
