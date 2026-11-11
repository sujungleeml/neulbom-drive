package com.neulbom.drive.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.neulbom.drive.core.id.SeededUuidGenerator
import com.neulbom.drive.domain.cognitive.Block
import com.neulbom.drive.domain.cognitive.ResponseKey
import com.neulbom.drive.domain.cognitive.Stimulus
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.TrialOutcome
import com.neulbom.drive.domain.cognitive.TrialResult
import com.neulbom.drive.domain.cognitive.TrialSpec
import com.neulbom.drive.domain.cognitive.assessment.AssessmentSession
import com.neulbom.drive.domain.cognitive.assessment.AssessmentStatus
import com.neulbom.drive.domain.cognitive.assessment.SaveState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class RoomTrialLogSinkTest {
    private lateinit var db: NeulbomDatabase
    private val session = AssessmentSession(
        "a1", Instant.parse("2026-11-03T01:00:00Z"), null, AssessmentStatus.IN_PROGRESS, null, true,
        mapOf(TaskId.VISUAL_ATTENTION to 1, TaskId.GO_NO_GO to 1, TaskId.TASK_SWITCHING to 1), "0.2.0", "test",
    )

    private fun result(i: Int, block: Block = Block.MAIN) = TrialResult(
        TrialSpec(i, block, Stimulus.Arrow(ResponseKey.LEFT), ResponseKey.LEFT, 1000), ResponseKey.LEFT, 480, TrialOutcome.CORRECT, 16,
        scheduledOnsetMs = 1000L + i, actualOnsetMs = 1016L + i, responseMs = 1496L + i,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NeulbomDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() { if (db.isOpen) db.close() }

    @Test
    fun appendWritesEachTrialImmediately() = runTest {
        val sink = RoomTrialLogSink(db, SeededUuidGenerator(1))
        sink.open(session)
        repeat(3) { sink.append("a1", TaskId.VISUAL_ATTENTION, result(it, if (it == 0) Block.PRACTICE else Block.MAIN)) }
        val rows = db.assessmentDao().trials("a1")
        assertEquals(3, rows.size)
        assertEquals(listOf(true, false, false), rows.map { it.isPractice })
        assertEquals(1016L, rows.first().actualOnsetMs)
    }

    @Test
    fun closeReturnsLocalPendingSync() = runTest {
        val sink = RoomTrialLogSink(db, SeededUuidGenerator(1))
        sink.open(session)
        assertEquals(SaveState.LOCAL_PENDING_SYNC, sink.close(session.copy(status = AssessmentStatus.COMPLETED, endedAt = Instant.parse("2026-11-03T01:04:00Z"))))
        val row = db.assessmentDao().session("a1")!!
        assertEquals("COMPLETED", row.status)
        assertEquals("LOCAL_PENDING_SYNC", row.syncState)
        assertEquals(1, db.assessmentDao().countCompleted())
    }

    @Test
    fun sinkFailureYieldsSaveFailed() = runTest {
        val sink = RoomTrialLogSink(db, SeededUuidGenerator(1))
        sink.open(session)
        db.close()
        assertEquals(SaveState.SAVE_FAILED, sink.close(session.copy(status = AssessmentStatus.COMPLETED)))
    }
}
