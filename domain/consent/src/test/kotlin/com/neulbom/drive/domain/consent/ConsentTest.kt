package com.neulbom.drive.domain.consent

import com.neulbom.drive.core.time.FakeClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsentRepositoryTest {
    @Test
    fun everythingIsOffByDefault() {
        val repo = InMemoryConsentRepository(FakeClock())
        ConsentType.entries.forEach { assertFalse(repo.state.value.isGranted(it)) }
    }

    @Test
    fun grantAndRevokeAreSeparatePerTypeAndAudited() = runTest {
        val clock = FakeClock()
        val repo = InMemoryConsentRepository(clock)
        repo.update(ConsentType.DRIVING_RECORD, true)
        clock.advanceMs(1000)
        repo.update(ConsentType.DRIVING_RECORD, false)
        assertFalse(repo.state.value.isGranted(ConsentType.DRIVING_RECORD))
        assertFalse(repo.state.value.isGranted(ConsentType.COGNITIVE_CHECK))
        val history = repo.history()
        assertEquals(listOf(false, true), history.map { it.granted })
        assertTrue(history.all { it.policyVersion == CONSENT_POLICY_VERSION })
    }

    @Test
    fun consentPurposesMatchSpecV05() {
        assertEquals(listOf(ConsentType.DRIVING_RECORD, ConsentType.COGNITIVE_CHECK), ConsentType.entries.toList())
    }

    @Test
    fun retentionOnlyAcceptsSupportedOptions() {
        assertEquals(180, RetentionPolicy().days)
        assertFailsWith<IllegalArgumentException> { RetentionPolicy(7) }
    }
}
