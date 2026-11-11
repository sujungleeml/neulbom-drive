package com.neulbom.drive.server.db

import com.neulbom.drive.server.settings.ConsentEventDto
import com.neulbom.drive.server.settings.SettingsStore
import java.time.Instant

/** consent_events(감사 이력, 기록 삭제 후에도 남김) + user_settings. */
class SqliteSettingsStore(private val db: SqliteDb, private val userId: String = SqliteDrivingStore.DEFAULT_USER) : SettingsStore {

    override fun addConsentEvent(e: ConsentEventDto) {
        db.update("INSERT INTO consent_events (user_id, type, granted, changed_at) VALUES (?, ?, ?, ?)", userId, e.type, if (e.granted) 1 else 0, e.changedAt)
    }

    override fun consentEvents(): List<ConsentEventDto> =
        db.query("SELECT type, granted, changed_at FROM consent_events WHERE user_id = ? ORDER BY seq", userId) { rs ->
            ConsentEventDto(rs.getString(1), rs.getInt(2) == 1, rs.getString(3))
        }

    override var offersPausedUntil: Instant?
        get() = db.query("SELECT offers_paused_until FROM user_settings WHERE user_id = ?", userId) { rs -> rs.getString(1)?.let(Instant::parse) }.firstOrNull()
        set(value) {
            db.update("INSERT OR REPLACE INTO user_settings (user_id, offers_paused_until) VALUES (?, ?)", userId, value?.toString())
        }
}
