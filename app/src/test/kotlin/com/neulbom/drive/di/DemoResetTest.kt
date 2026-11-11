package com.neulbom.drive.di

import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.domain.consent.ConsentType
import com.neulbom.drive.domain.consent.InMemoryConsentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DemoResetTest {
    @Test
    fun resetReturnsToFirstRunButKeepsConsentAudit() = runTest {
        val consents = InMemoryConsentRepository(FakeClock())
        consents.update(ConsentType.DRIVING_RECORD, true)
        consents.update(ConsentType.COGNITIVE_CHECK, true)
        val steps = mutableListOf<String>()
        DemoReset(
            consents = consents,
            stopFlow = { steps += "flow" },
            deleteLocal = { steps += "local" },
            resetSettings = { steps += "settings" },
        ).run()
        assertEquals(listOf("flow", "local", "settings"), steps) // 진행 중 점검을 먼저 멈추고 지운다
        ConsentType.entries.forEach { assertFalse(consents.state.value.isGranted(it)) }
        assertEquals(4, consents.history().size) // 철회도 이력으로 남는다
    }
}
