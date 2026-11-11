package com.neulbom.drive.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.domain.consent.ConsentType
import com.neulbom.drive.domain.consent.RetentionPolicy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RoomRepositoriesTest {
    private lateinit var db: NeulbomDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NeulbomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun revokedConsentIsReflectedImmediatelyAndAudited() = runTest {
        val consents = RoomConsentRepository(db.consentDao(), FakeClock(), backgroundScope)
        consents.update(ConsentType.COGNITIVE_CHECK, true)
        consents.update(ConsentType.COGNITIVE_CHECK, false)
        assertFalse(consents.state.value.isGranted(ConsentType.COGNITIVE_CHECK))
        assertEquals(2, consents.history().size)
    }

    @Test
    fun legacyConsentPurposesAreIgnored() = runTest {
        db.consentDao().insert(DriverConsentEntity(type = "USAGE_ANALYTICS", granted = true, policyVersion = 1, changedAt = 0))
        val consents = RoomConsentRepository(db.consentDao(), FakeClock(), backgroundScope)
        assertTrue(consents.history().isEmpty())
    }

    @Test
    fun retentionPurgeRemovesOldAssessmentsWithTheirTrials() = runTest {
        val dao = db.assessmentDao()
        fun session(id: String, at: Instant) = AssessmentSessionEntity(id, at.toEpochMilli(), null, "COMPLETED", null, false, "", "0.2.0", "test", "LOCAL_PENDING_SYNC")
        val now = Instant.parse("2026-11-01T00:00:00Z")
        dao.insertSession(session("old", now.minus(Duration.ofDays(40))))
        dao.insertSession(session("recent", now.minus(Duration.ofDays(2))))
        dao.insertTrial(AssessmentTrialEntity("t1", "old", "GO_NO_GO", 1, 1, 0, "MAIN", "GO", 0, 0, 1, 1, "TAP", "CORRECT", false, 0))
        LocalDataManager(db, RoomDrivingQueue(db)).purgeExpired(RetentionPolicy(30), now)
        assertEquals(null, dao.session("old"))
        assertTrue(dao.trials("old").isEmpty())
        assertEquals("recent", dao.session("recent")!!.assessmentId)
    }
}
