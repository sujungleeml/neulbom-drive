package com.neulbom.drive.server.db

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * SQLite 파일 하나(시연·파일럿 규모). 연결 하나를 잠가서 쓰고, 시작할 때 마이그레이션을 번호 순서로 적용한다.
 * 마이그레이션이 실패하면 예외로 서버 시작을 멈춘다. 운영(PostgreSQL)으로 옮겨도 테이블 모양은 같다(명세 04 §3).
 *
 * 주행 특징량과 점검 시행은 **다른 테이블**에 둔다(합친 값·점수 없음). 원시 주행 표본은 저장하지 않는다.
 */
class SqliteDb(path: String) : AutoCloseable {
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:$path").apply {
        createStatement().use { s ->
            s.execute("PRAGMA foreign_keys = ON")
            s.execute("PRAGMA journal_mode = WAL")
            s.execute("PRAGMA busy_timeout = 5000")
        }
    }

    init {
        migrate()
    }

    /** 적용된 마지막 마이그레이션 번호. */
    val schemaVersion: Int get() = synchronized(this) { query("SELECT MAX(version) FROM schema_version") { it.getInt(1) }.single() }

    private var txDepth = 0

    /** 블록 전체를 한 트랜잭션으로. 예외가 나면 모두 되돌린다. 안쪽 tx는 바깥 트랜잭션에 합쳐진다. */
    fun <T> tx(block: SqliteDb.() -> T): T = synchronized(this) {
        if (txDepth > 0) {
            txDepth++
            try {
                return@synchronized block()
            } finally {
                txDepth--
            }
        }
        conn.autoCommit = false
        txDepth = 1
        try {
            block().also { conn.commit() }
        } catch (e: Throwable) {
            conn.rollback()
            throw e
        } finally {
            txDepth = 0
            conn.autoCommit = true
        }
    }

    fun update(sql: String, vararg args: Any?): Int = synchronized(this) { prepare(sql, args).use { it.executeUpdate() } }

    fun <T> query(sql: String, vararg args: Any?, row: (ResultSet) -> T): List<T> = synchronized(this) {
        prepare(sql, args).use { st -> st.executeQuery().use { rs -> buildList { while (rs.next()) add(row(rs)) } } }
    }

    private fun prepare(sql: String, args: Array<out Any?>): PreparedStatement =
        conn.prepareStatement(sql).also { st -> args.forEachIndexed { i, a -> st.setObject(i + 1, a) } }

    private fun migrate() = synchronized(this) {
        conn.createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)") }
        val current = query("SELECT COALESCE(MAX(version), 0) FROM schema_version") { it.getInt(1) }.single()
        MIGRATIONS.filter { it.first > current }.sortedBy { it.first }.forEach { (version, statements) ->
            tx {
                conn.createStatement().use { s -> statements.forEach(s::execute) }
                update("INSERT INTO schema_version (version, applied_at) VALUES (?, datetime('now'))", version)
            }
        }
    }

    override fun close() = conn.close()

    companion object {
        /** (번호, SQL 목록). 이미 배포된 번호는 고치지 말고 새 번호를 추가한다. */
        val MIGRATIONS: List<Pair<Int, List<String>>> = listOf(
            1 to listOf(
                """CREATE TABLE driving_sessions (
                    user_id TEXT NOT NULL,
                    drive_id TEXT NOT NULL PRIMARY KEY,
                    started_at TEXT NOT NULL,
                    ended_at TEXT NOT NULL,
                    zone TEXT NOT NULL,
                    origin TEXT NOT NULL,
                    signal_set TEXT NOT NULL,
                    sample_rate_hz TEXT NOT NULL,
                    attribution TEXT NOT NULL,
                    terminated_normally INTEGER NOT NULL
                )""",
                "CREATE INDEX idx_driving_sessions_user_started ON driving_sessions (user_id, started_at)",
                """CREATE TABLE driving_session_features (
                    drive_id TEXT NOT NULL PRIMARY KEY REFERENCES driving_sessions (drive_id) ON DELETE CASCADE,
                    algorithm_version TEXT NOT NULL,
                    duration_s REAL NOT NULL,
                    distance_km REAL,
                    night_share REAL NOT NULL,
                    speed_band_seconds TEXT,
                    harsh_decel_count INTEGER,
                    harsh_decel_by_band TEXT,
                    decel_source TEXT,
                    coverage REAL NOT NULL,
                    missing_rate REAL NOT NULL,
                    max_gap_s REAL NOT NULL,
                    dropped_samples INTEGER NOT NULL,
                    quality TEXT NOT NULL,
                    valid INTEGER NOT NULL
                )""",
                "CREATE TABLE signal_support (user_id TEXT NOT NULL PRIMARY KEY, list_json TEXT NOT NULL)",
                """CREATE TABLE assessment_sessions (
                    user_id TEXT NOT NULL,
                    assessment_id TEXT NOT NULL PRIMARY KEY,
                    started_at TEXT NOT NULL,
                    ended_at TEXT,
                    status TEXT NOT NULL,
                    stop_reason TEXT,
                    is_first_assessment INTEGER NOT NULL,
                    protocol_versions TEXT NOT NULL,
                    app_version TEXT NOT NULL,
                    device_profile TEXT NOT NULL,
                    vehicle_source TEXT NOT NULL
                )""",
                "CREATE INDEX idx_assessment_sessions_user_started ON assessment_sessions (user_id, started_at)",
                """CREATE TABLE assessment_trials (
                    assessment_id TEXT NOT NULL REFERENCES assessment_sessions (assessment_id) ON DELETE CASCADE,
                    seq INTEGER NOT NULL,
                    task_id TEXT NOT NULL,
                    task_version INTEGER NOT NULL,
                    stimulus_set_version INTEGER NOT NULL,
                    trial_index INTEGER NOT NULL,
                    block TEXT NOT NULL,
                    trial_type TEXT NOT NULL,
                    scheduled_onset_ms INTEGER NOT NULL,
                    actual_onset_ms INTEGER,
                    response_ms INTEGER,
                    reaction_time_ms INTEGER,
                    response_code TEXT,
                    outcome TEXT NOT NULL,
                    is_practice INTEGER NOT NULL,
                    display_latency_ms INTEGER NOT NULL,
                    PRIMARY KEY (assessment_id, seq)
                )""",
                "CREATE TABLE consent_events (user_id TEXT NOT NULL, seq INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, granted INTEGER NOT NULL, changed_at TEXT NOT NULL)",
                "CREATE TABLE user_settings (user_id TEXT NOT NULL PRIMARY KEY, offers_paused_until TEXT)",
                """CREATE TABLE share_links (
                    user_id TEXT NOT NULL,
                    id TEXT NOT NULL PRIMARY KEY,
                    token TEXT NOT NULL UNIQUE,
                    scopes TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    expires_at TEXT NOT NULL,
                    revoked_at TEXT
                )""",
                "CREATE TABLE share_views (link_id TEXT NOT NULL REFERENCES share_links (id) ON DELETE CASCADE, viewed_at TEXT NOT NULL)",
            ),
        )
    }
}
