package com.neulbom.drive.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SchedulingDaoTest {
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
    fun declineIsRecordedOnceAndKeptInOrder() = runTest {
        val dao = db.schedulingDao()
        dao.insert(SchedulingEventEntity("o2", 2_000, null, null, simulated = true))
        dao.insert(SchedulingEventEntity("o1", 1_000, null, null, simulated = true))
        assertEquals(1, dao.respond("o2", "LATER", 2_500))
        assertEquals(0, dao.respond("o2", "ACCEPTED", 2_600)) // 이미 답한 제안은 바뀌지 않는다
        assertEquals(listOf("o1" to null, "o2" to "LATER"), dao.all().map { it.offerId to it.response })
    }

    @Test
    fun completedStartTimesListsOnlyCompletedSessions() = runTest {
        val dao = db.assessmentDao()
        fun s(id: String, at: Long, status: String) = AssessmentSessionEntity(id, at, null, status, null, false, "", "0.2.0", "test", "LOCAL_PENDING_SYNC")
        dao.insertSession(s("a", 300, "COMPLETED"))
        dao.insertSession(s("b", 100, "INTERRUPTED"))
        dao.insertSession(s("c", 200, "COMPLETED"))
        assertEquals(listOf(200L, 300L), dao.completedStartTimes())
    }

    @Test
    fun deleteAllRecordsAlsoClearsOfferHistory() = runTest {
        db.schedulingDao().insert(SchedulingEventEntity("o1", 1_000, "LATER", 1_100, simulated = false))
        LocalDataManager(db, RoomDrivingQueue(db)).deleteAllRecords()
        assertEquals(0, db.schedulingDao().all().size)
    }
}
