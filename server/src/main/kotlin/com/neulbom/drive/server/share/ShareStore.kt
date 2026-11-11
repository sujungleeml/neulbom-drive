package com.neulbom.drive.server.share

import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 공유 범위(ADR-09). 고른 범위만 공유 화면에 나간다. */
enum class ShareScope { DRIVING_SUMMARY, CHECK_RECORDS }

data class ShareLink(
    val id: String,
    val token: String,
    val scopes: Set<ShareScope>,
    val createdAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val views: List<Instant>,
)

/** share_links + share_link_views. 시연용 메모리 저장소. */
interface ShareStore {
    fun create(scopes: Set<ShareScope>, now: Instant, expiresAt: Instant): ShareLink
    fun byToken(token: String): ShareLink?
    fun revoke(id: String, now: Instant): Boolean
    fun recordView(id: String, at: Instant)
    fun all(): List<ShareLink>
    fun deleteAll()
}

class InMemoryShareStore(private val random: SecureRandom = SecureRandom()) : ShareStore {
    private val links = ConcurrentHashMap<String, ShareLink>()

    override fun create(scopes: Set<ShareScope>, now: Instant, expiresAt: Instant): ShareLink {
        val bytes = ByteArray(24).also(random::nextBytes) // 192비트 — 추측할 수 없는 링크
        val link = ShareLink(UUID.randomUUID().toString(), Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), scopes, now, expiresAt, null, emptyList())
        links[link.id] = link
        return link
    }

    override fun byToken(token: String) = links.values.firstOrNull { it.token == token }

    override fun revoke(id: String, now: Instant): Boolean {
        val l = links[id] ?: return false
        if (l.revokedAt == null) links[id] = l.copy(revokedAt = now)
        return true
    }

    override fun recordView(id: String, at: Instant) {
        links.computeIfPresent(id) { _, l -> l.copy(views = l.views + at) }
    }

    override fun all() = links.values.sortedByDescending { it.createdAt }
    override fun deleteAll() = links.clear()
}
