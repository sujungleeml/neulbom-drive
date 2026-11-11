package com.neulbom.drive.server.settings

import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.domain.consent.ConsentType
import com.neulbom.drive.domain.consent.RetentionPolicy
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.time.Duration
import java.time.Instant

/** 웹에서 고를 수 있는 점검 제안 일시 중지 기간(일). 0은 해제. */
val PAUSE_OPTIONS = setOf(0, 7, 14, 30)

/** 설정·권한(W-04). 동의는 유형별로 가장 늦게 바뀐 값이 현재 값이다(차량과 같은 규칙). */
fun Route.settingsRoutes(store: SettingsStore, clock: Clock) {
    get("/v1/settings/me") { call.respond(current(store)) }
    put("/v1/settings/me/consents/{type}") {
        val type = call.parameters["type"]!!.let { t -> ConsentType.entries.firstOrNull { it.name == t } }
        requireNotNull(type) { "unknown consent type" }
        val body = call.receive<ConsentChangeDto>()
        store.addConsentEvent(ConsentEventDto(type.name, body.granted, clock.now().toString()))
        call.respond(current(store))
    }
    post("/v1/consent-events") {
        val events = call.receive<List<ConsentEventDto>>()
        events.forEach { e ->
            require(ConsentType.entries.any { it.name == e.type }) { "unknown consent type" }
            Instant.parse(e.changedAt)
            store.addConsentEvent(e)
        }
        call.respond(HttpStatusCode.NoContent)
    }
    put("/v1/settings/me/offers") {
        val days = call.receive<OfferPauseDto>().pauseDays
        require(days in PAUSE_OPTIONS) { "pauseDays must be one of $PAUSE_OPTIONS" }
        store.offersPausedUntil = if (days == 0) null else clock.now().plus(Duration.ofDays(days.toLong()))
        call.respond(current(store))
    }
}

fun current(store: SettingsStore) = SettingsDto(
    consents = store.consentEvents().groupBy { it.type }.map { (_, v) -> v.maxBy { Instant.parse(it.changedAt) } }.sortedBy { it.type },
    offersPausedUntil = store.offersPausedUntil?.toString(),
    retentionDays = RetentionPolicy.DEFAULT_DAYS,
)
