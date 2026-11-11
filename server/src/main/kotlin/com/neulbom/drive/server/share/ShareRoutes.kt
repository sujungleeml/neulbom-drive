package com.neulbom.drive.server.share

import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.server.REPORT_ZONE
import com.neulbom.drive.server.checks.CheckStore
import com.neulbom.drive.server.checks.ChecksReportDto
import com.neulbom.drive.server.checks.checksReport
import com.neulbom.drive.server.driving.DrivingReportDto
import com.neulbom.drive.server.driving.DrivingStore
import com.neulbom.drive.server.driving.drivingReport
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import java.time.Duration
import java.time.YearMonth

/** 만료 기간(일). 기본 7일(ADR-09). */
val SHARE_DAYS = setOf(1, 3, 7)

@Serializable
data class CreateShareDto(val scopes: List<String>, val days: Int = 7)

@Serializable
data class CreatedShareDto(val id: String, val token: String, val path: String, val scopes: List<String>, val expiresAt: String)

/** 목록에는 토큰을 다시 보여 주지 않는다(만들 때 한 번만). */
@Serializable
data class ShareLinkDto(val id: String, val scopes: List<String>, val createdAt: String, val expiresAt: String, val state: String, val views: Int)

/**
 * 가족 공유 링크(W-06, ADR-09): 기본 비공유, 범위 선택, 만료·취소, 열람 기록. 공유 화면은 읽기 전용이며
 * 두 축을 **서로 다른 키**로만 담는다(합친 값 없음).
 */
fun Route.shareRoutes(shares: ShareStore, driving: DrivingStore, checks: CheckStore, clock: Clock) {
    post("/v1/share-links") {
        val req = call.receive<CreateShareDto>()
        val scopes = req.scopes.map { s -> requireNotNull(ShareScope.entries.firstOrNull { it.name == s }) { "unknown scope" } }.toSet()
        require(scopes.isNotEmpty()) { "choose at least one scope" }
        require(req.days in SHARE_DAYS) { "days must be one of $SHARE_DAYS" }
        val now = clock.now()
        val link = shares.create(scopes, now, now.plus(Duration.ofDays(req.days.toLong())))
        call.respond(HttpStatusCode.Created, CreatedShareDto(link.id, link.token, "/s/${link.token}", link.scopes.map { it.name }.sorted(), link.expiresAt.toString()))
    }
    get("/v1/share-links") {
        val now = clock.now()
        call.respond(shares.all().map { l ->
            val state = when {
                l.revokedAt != null -> "REVOKED"
                !now.isBefore(l.expiresAt) -> "EXPIRED"
                else -> "ACTIVE"
            }
            ShareLinkDto(l.id, l.scopes.map { it.name }.sorted(), l.createdAt.toString(), l.expiresAt.toString(), state, l.views.size)
        })
    }
    delete("/v1/share-links/{id}") {
        if (shares.revoke(call.parameters["id"]!!, clock.now())) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
    }
    get("/v1/shared/{token}") {
        val link = shares.byToken(call.parameters["token"]!!) ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("state" to "NOT_FOUND"))
        val now = clock.now()
        if (link.revokedAt != null || !now.isBefore(link.expiresAt)) return@get call.respond(HttpStatusCode.Gone, mapOf("state" to "ENDED"))
        shares.recordView(link.id, now)
        val body = buildJsonObject {
            put("expiresAt", link.expiresAt.toString())
            putJsonArray("scopes") { link.scopes.map { it.name }.sorted().forEach { add(it) } }
            if (ShareScope.DRIVING_SUMMARY in link.scopes) {
                put("driving", Json.encodeToJsonElement(DrivingReportDto.serializer(), drivingReport(driving, YearMonth.from(now.atZone(REPORT_ZONE)))))
            }
            if (ShareScope.CHECK_RECORDS in link.scopes) {
                put("checks", Json.encodeToJsonElement(ChecksReportDto.serializer(), checksReport(checks)))
            }
        }
        call.respondText(body.toString(), ContentType.Application.Json)
    }
    get("/s/{token}") {
        val html = this::class.java.classLoader.getResource("web/share.html")!!.readText()
        call.respondText(html, ContentType.Text.Html)
    }
}
