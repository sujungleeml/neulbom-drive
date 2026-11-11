package com.neulbom.drive.server

import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.domain.driving.SessionFeatureCalculator
import com.neulbom.drive.server.checks.CheckStore
import com.neulbom.drive.server.checks.DemoChecks
import com.neulbom.drive.server.checks.InMemoryCheckStore
import com.neulbom.drive.server.db.SqliteCheckStore
import com.neulbom.drive.server.db.SqliteDb
import com.neulbom.drive.server.db.SqliteDrivingStore
import com.neulbom.drive.server.db.SqliteSettingsStore
import com.neulbom.drive.server.db.SqliteShareStore
import com.neulbom.drive.server.driving.DrivingStore
import com.neulbom.drive.server.driving.InMemoryDrivingStore
import com.neulbom.drive.server.driving.SignalSupportDto
import com.neulbom.drive.server.settings.ConsentEventDto
import com.neulbom.drive.server.settings.InMemorySettingsStore
import com.neulbom.drive.server.settings.SettingsStore
import com.neulbom.drive.server.settings.purgeExpired
import com.neulbom.drive.server.share.InMemoryShareStore
import com.neulbom.drive.server.share.ShareScope
import com.neulbom.drive.server.share.ShareStore
import com.neulbom.drive.vehicle.mock.MockTripGenerator
import com.neulbom.drive.vehicle.mock.TripType
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 저장소 계약: 메모리와 SQLite가 같은 동작을 해야 한다. 두 구현 모두 이 테스트를 통과한다. */
abstract class StoreContractTest {
    abstract fun driving(): DrivingStore
    abstract fun checks(): CheckStore
    abstract fun settings(): SettingsStore
    abstract fun shares(): ShareStore

    protected val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val signals = setOf(SignalId.VEHICLE_STATE, SignalId.SPEED, SignalId.LONG_ACCEL)

    private fun trip(id: String, at: String) =
        MockTripGenerator.generate(id, TripType.ROAD, Instant.parse(at), 900, zone, 1, signals, 1).let { it.session to SessionFeatureCalculator.compute(it.session, it.samples) }

    @Test fun drivingSessionsRoundTrip() {
        val s = driving()
        val (later, laterF) = trip("d2", "2026-11-05T01:00:00Z")
        val (earlier, earlierF) = trip("d1", "2026-11-03T01:00:00Z")
        assertTrue(s.upsert(later, laterF))
        assertTrue(s.upsert(earlier, earlierF))
        assertFalse(s.upsert(earlier, earlierF), "같은 driveId는 다시 저장하지 않는다")
        assertEquals(earlier to earlierF, s.get("d1"))
        assertEquals(listOf("d1", "d2"), s.all().map { it.first.driveId }, "시작 시각 순서")
        assertNull(s.get("nope"))
    }

    @Test fun signalSupportRoundTrip() {
        val s = driving()
        assertEquals(emptyList(), s.signalSupport())
        val list = listOf(SignalSupportDto("SPEED", "SUPPORTED", "MOCK", 10.0), SignalSupportDto("GEAR", "UNSUPPORTED", null, null))
        s.putSignalSupport(list)
        assertEquals(list, s.signalSupport())
    }

    @Test fun drivingDeleteAndRetention() {
        val s = driving()
        trip("old", "2026-01-01T01:00:00Z").let { s.upsert(it.first, it.second) }
        trip("new", "2026-11-01T01:00:00Z").let { s.upsert(it.first, it.second) }
        assertEquals(1, s.deleteStartedBefore(Instant.parse("2026-06-01T00:00:00Z")))
        assertEquals(listOf("new"), s.all().map { it.first.driveId })
        s.deleteAll()
        assertEquals(emptyList(), s.all())
    }

    @Test fun checksRoundTripWithTrials() {
        val s = checks()
        val demo = DemoChecks.generate(LocalDate.of(2026, 11, 3), zone)
        demo.reversed().forEach { assertTrue(s.putIfAbsent(it)) }
        assertFalse(s.putIfAbsent(demo.first()))
        assertEquals(demo.sortedBy { it.session.startedAt }, s.all(), "세션과 시행이 순서까지 그대로 복원된다")
        assertEquals(demo.last(), s.get(demo.last().session.assessmentId))
    }

    @Test fun checksDeleteAndRetention() {
        val s = checks()
        val demo = DemoChecks.generate(LocalDate.of(2026, 11, 3), zone).sortedBy { it.session.startedAt }
        demo.forEach { s.putIfAbsent(it) }
        val cutoff = Instant.parse(demo[2].session.startedAt)
        assertEquals(2, s.deleteStartedBefore(cutoff))
        assertEquals(demo.drop(2), s.all())
        s.deleteAll()
        assertEquals(emptyList(), s.all())
    }

    @Test fun settingsRoundTrip() {
        val s = settings()
        assertNull(s.offersPausedUntil)
        s.addConsentEvent(ConsentEventDto("DRIVING_RECORD", true, "2026-11-01T00:00:00Z"))
        s.addConsentEvent(ConsentEventDto("DRIVING_RECORD", false, "2026-11-02T00:00:00Z"))
        assertEquals(listOf(true, false), s.consentEvents().map { it.granted }, "이력 순서 유지")
        val until = Instant.parse("2026-11-10T00:00:00Z")
        s.offersPausedUntil = until
        assertEquals(until, s.offersPausedUntil)
        s.offersPausedUntil = null
        assertNull(s.offersPausedUntil)
    }

    @Test fun shareLinksRoundTrip() {
        val s = shares()
        val now = Instant.parse("2026-11-03T00:00:00Z")
        val link = s.create(setOf(ShareScope.CHECK_RECORDS, ShareScope.DRIVING_SUMMARY), now, now.plusSeconds(7 * 86_400))
        assertEquals(32, link.token.length)
        assertEquals(link, s.byToken(link.token))
        s.recordView(link.id, now.plusSeconds(60))
        assertEquals(listOf(now.plusSeconds(60)), s.byToken(link.token)!!.views)
        assertTrue(s.revoke(link.id, now.plusSeconds(120)))
        assertEquals(now.plusSeconds(120), s.all().single().revokedAt)
        assertTrue(s.revoke(link.id, now.plusSeconds(999)))
        assertEquals(now.plusSeconds(120), s.all().single().revokedAt, "두 번 취소해도 처음 시각 유지")
        assertFalse(s.revoke("nope", now))
        s.deleteAll()
        assertNull(s.byToken(link.token))
    }

    @Test fun retentionPurgesBothAxesOnly() {
        val d = driving()
        val c = checks()
        val st = settings()
        trip("old", "2026-01-01T01:00:00Z").let { d.upsert(it.first, it.second) }
        DemoChecks.generate(LocalDate.of(2026, 1, 20), zone).forEach { c.putIfAbsent(it) }
        st.addConsentEvent(ConsentEventDto("COGNITIVE_CHECK", true, "2026-01-01T00:00:00Z"))
        val (drives, checksGone) = purgeExpired(d, c, Instant.parse("2026-11-03T00:00:00Z"))
        assertEquals(1, drives)
        assertTrue(checksGone > 0)
        assertEquals(emptyList(), d.all())
        assertEquals(emptyList(), c.all())
        assertEquals(1, st.consentEvents().size, "동의 이력은 감사용으로 남는다")
    }
}

class InMemoryStoreContractTest : StoreContractTest() {
    override fun driving() = InMemoryDrivingStore()
    override fun checks() = InMemoryCheckStore()
    override fun settings() = InMemorySettingsStore()
    override fun shares() = InMemoryShareStore()
}

class SqliteStoreContractTest : StoreContractTest() {
    private val file: File = File.createTempFile("neulbom-test", ".db").also { it.delete() }
    private val db = SqliteDb(file.path)

    override fun driving() = SqliteDrivingStore(db)
    override fun checks() = SqliteCheckStore(db)
    override fun settings() = SqliteSettingsStore(db)
    override fun shares() = SqliteShareStore(db)

    @AfterTest fun cleanUp() {
        db.close()
        listOf("", "-wal", "-shm").forEach { File(file.path + it).delete() }
    }

    @Test fun dataSurvivesRestart() {
        val demo = DemoChecks.generate(LocalDate.of(2026, 11, 3), zone)
        demo.forEach { checks().putIfAbsent(it) }
        settings().addConsentEvent(ConsentEventDto("COGNITIVE_CHECK", true, "2026-11-01T00:00:00Z"))
        db.close()
        SqliteDb(file.path).use { reopened ->
            assertEquals(SqliteDb.MIGRATIONS.maxOf { it.first }, reopened.schemaVersion, "다시 열어도 마이그레이션을 반복하지 않는다")
            assertEquals(demo.sortedBy { it.session.startedAt }, SqliteCheckStore(reopened).all())
            assertEquals(1, SqliteSettingsStore(reopened).consentEvents().size)
        }
    }

    @Test fun usersAreSeparated() {
        val me = SqliteCheckStore(db, "me")
        val other = SqliteCheckStore(db, "other")
        val demo = DemoChecks.generate(LocalDate.of(2026, 11, 3), zone)
        me.putIfAbsent(demo[0])
        other.putIfAbsent(demo[1])
        assertEquals(listOf(demo[0].session.assessmentId), me.all().map { it.session.assessmentId })
        assertNull(me.get(demo[1].session.assessmentId), "다른 사용자의 기록은 보이지 않는다")
        me.deleteAll()
        assertEquals(1, other.all().size, "내 기록을 지워도 다른 사용자 기록은 남는다")
    }

    @Test fun failedTransactionLeavesNothing() {
        val demo = DemoChecks.generate(LocalDate.of(2026, 11, 3), zone).first()
        assertFailsWith<IllegalStateException> {
            db.tx {
                SqliteCheckStore(this).putIfAbsent(demo)
                error("중간 실패")
            }
        }
        assertNull(checks().get(demo.session.assessmentId), "세션만 남거나 시행 일부만 남지 않는다")
        assertNotNull(SqliteDb.MIGRATIONS.firstOrNull())
    }
}
