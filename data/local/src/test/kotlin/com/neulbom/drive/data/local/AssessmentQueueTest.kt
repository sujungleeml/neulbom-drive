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
class AssessmentQueueTest {
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
    fun pendingListsFinishedUnsyncedWithTrials() = runTest {
        val dao = db.assessmentDao()
        fun s(id: String, sync: String, status: String = "COMPLETED") =
            AssessmentSessionEntity(id, 1_000, 2_000, status, null, true, "VISUAL_ATTENTION:1;GO_NO_GO:1", "0.3.0", "emu", sync)
        dao.insertSession(s("done", "LOCAL_PENDING_SYNC"))
        dao.insertSession(s("stopped", "LOCAL_PENDING_SYNC", status = "INTERRUPTED"))
        dao.insertSession(s("running", "IN_PROGRESS", status = "IN_PROGRESS"))
        dao.insertSession(s("sent", "SERVER_SAVED"))
        dao.insertTrial(AssessmentTrialEntity("t1", "done", "GO_NO_GO", 1, 1, 0, "MAIN", "NOGO", 10, 26, null, null, null, "CORRECT_REJECTION", false, 16))
        val queue = RoomAssessmentQueue(db, "MOCK")
        val pending = queue.pending()
        assertEquals(listOf("done", "stopped"), pending.map { it.session.assessmentId })
        val done = pending.first()
        assertEquals(mapOf("VISUAL_ATTENTION" to 1, "GO_NO_GO" to 1), done.session.protocolVersions)
        assertEquals("MOCK", done.session.vehicleSource)
        assertEquals(listOf("NOGO"), done.trials.map { it.trialType })
        queue.markSynced("done")
        assertEquals("SERVER_SAVED", dao.session("done")!!.syncState)
        assertEquals(listOf("stopped"), queue.pending().map { it.session.assessmentId })
    }
}
