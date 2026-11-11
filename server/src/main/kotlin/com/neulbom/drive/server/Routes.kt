package com.neulbom.drive.server

import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.server.checks.CheckStore
import com.neulbom.drive.server.checks.InMemoryCheckStore
import com.neulbom.drive.server.checks.checksRoutes
import com.neulbom.drive.server.driving.DrivingStore
import com.neulbom.drive.server.driving.InMemoryDrivingStore
import com.neulbom.drive.server.driving.drivingRoutes
import com.neulbom.drive.server.settings.InMemorySettingsStore
import com.neulbom.drive.server.settings.SettingsStore
import com.neulbom.drive.server.settings.dataRoutes
import com.neulbom.drive.server.settings.settingsRoutes
import com.neulbom.drive.server.share.InMemoryShareStore
import com.neulbom.drive.server.share.ShareStore
import com.neulbom.drive.server.share.shareRoutes
import com.neulbom.drive.server.journey.journeyRoutes
import com.neulbom.drive.server.overview.overviewRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** 리포트 기간 기본 시간대(시연: 한국). */
val REPORT_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/** 서버 모듈: 두 축(주행 습관·주의 반응 점검)의 API를 따로 올리고 정적 웹을 제공한다. */
fun Application.neulbomModule(
    store: DrivingStore = InMemoryDrivingStore(),
    clock: Clock,
    checks: CheckStore = InMemoryCheckStore(),
    settings: SettingsStore = InMemorySettingsStore(),
    shares: ShareStore = InMemoryShareStore(),
) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "bad request"))) }
        exception<DateTimeParseException> { call, _ -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to "bad period")) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to "bad request")) }
    }
    routing {
        drivingRoutes(store, clock)
        checksRoutes(checks)
        settingsRoutes(settings, clock)
        dataRoutes(store, checks, settings, clock, onDelete = shares::deleteAll)
        shareRoutes(shares, store, checks, clock)
        journeyRoutes(store, checks, clock)
        overviewRoutes(store, checks, clock)
        staticResources("/", "web")
    }
}
