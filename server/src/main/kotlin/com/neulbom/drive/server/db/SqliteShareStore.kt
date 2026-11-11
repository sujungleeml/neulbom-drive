package com.neulbom.drive.server.db

import com.neulbom.drive.server.share.ShareLink
import com.neulbom.drive.server.share.ShareScope
import com.neulbom.drive.server.share.ShareStore
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** share_links + share_views. */
class SqliteShareStore(
    private val db: SqliteDb,
    private val userId: String = SqliteDrivingStore.DEFAULT_USER,
    private val random: SecureRandom = SecureRandom(),
) : ShareStore {

    override fun create(scopes: Set<ShareScope>, now: Instant, expiresAt: Instant): ShareLink {
        val bytes = ByteArray(24).also(random::nextBytes) // 192비트 — 추측할 수 없는 링크
        val link = ShareLink(UUID.randomUUID().toString(), Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), scopes, now, expiresAt, null, emptyList())
        db.update(
            "INSERT INTO share_links VALUES (?, ?, ?, ?, ?, ?, ?)",
            userId, link.id, link.token, scopes.map { it.name }.sorted().joinToString(","), now.toString(), expiresAt.toString(), null,
        )
        return link
    }

    override fun byToken(token: String) = links("WHERE token = ?", token).firstOrNull()

    override fun revoke(id: String, now: Instant): Boolean = db.tx {
        if (query("SELECT 1 FROM share_links WHERE user_id = ? AND id = ?", userId, id) { true }.isEmpty()) return@tx false
        update("UPDATE share_links SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL", now.toString(), id)
        true
    }

    override fun recordView(id: String, at: Instant) {
        db.update("INSERT INTO share_views SELECT id, ? FROM share_links WHERE id = ?", at.toString(), id)
    }

    override fun all() = links("WHERE user_id = ? ORDER BY created_at DESC", userId)

    override fun deleteAll() {
        db.tx { update("DELETE FROM share_links WHERE user_id = ?", userId) }
    }

    private fun links(where: String, vararg args: Any?): List<ShareLink> = db.query("SELECT * FROM share_links $where", *args) { rs ->
        ShareLink(
            id = rs.getString("id"),
            token = rs.getString("token"),
            scopes = rs.getString("scopes").split(",").filter { it.isNotBlank() }.map { ShareScope.valueOf(it) }.toSet(),
            createdAt = Instant.parse(rs.getString("created_at")),
            expiresAt = Instant.parse(rs.getString("expires_at")),
            revokedAt = rs.getString("revoked_at")?.let(Instant::parse),
            views = emptyList(),
        )
    }.map { l -> l.copy(views = db.query("SELECT viewed_at FROM share_views WHERE link_id = ? ORDER BY viewed_at", l.id) { Instant.parse(it.getString(1)) }) }
}
