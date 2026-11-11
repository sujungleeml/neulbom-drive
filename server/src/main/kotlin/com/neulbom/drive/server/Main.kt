package com.neulbom.drive.server

import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.time.SystemClock
import com.neulbom.drive.domain.driving.SessionFeatureCalculator
import com.neulbom.drive.vehicle.mock.MockDrivingHistory
import com.neulbom.drive.vehicle.mock.MockVehicleAdapter
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import com.neulbom.drive.server.driving.DrivingStore
import com.neulbom.drive.server.driving.InMemoryDrivingStore
import com.neulbom.drive.server.driving.SignalSupportDto
import com.neulbom.drive.server.checks.DemoChecks
import com.neulbom.drive.server.checks.InMemoryCheckStore
import com.neulbom.drive.server.checks.CheckStore
import com.neulbom.drive.server.db.SqliteCheckStore
import com.neulbom.drive.server.db.SqliteDb
import com.neulbom.drive.server.db.SqliteDrivingStore
import com.neulbom.drive.server.db.SqliteSettingsStore
import com.neulbom.drive.server.db.SqliteShareStore
import com.neulbom.drive.server.settings.InMemorySettingsStore
import com.neulbom.drive.server.settings.SettingsStore
import com.neulbom.drive.server.settings.purgeExpired
import com.neulbom.drive.server.share.InMemoryShareStore
import com.neulbom.drive.server.share.ShareStore
import java.time.Duration
import java.util.Timer
import kotlin.concurrent.timerTask
import java.time.LocalDate

/**
 * 로컬 시연 서버: `./gradlew -Pneulbom.jvmOnly=true :server:run` → http://localhost:8080
 * `NEULBOM_DB_PATH=파일경로`이면 SQLite에 영속 저장(없으면 메모리).
 * `NEULBOM_DEMO_SEED=true`이면 (비어 있을 때) 8주치 **모의** 주행 기록과 모의 점검 6회를 미리 넣는다(모두 MOCK 표시).
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    // NEULBOM_DB_PATH가 있으면 SQLite 파일에 영속 저장, 없으면 지금처럼 메모리(재시작하면 사라짐).
    val db = System.getenv("NEULBOM_DB_PATH")?.takeIf { it.isNotBlank() }?.let(::SqliteDb)
    val store: DrivingStore = db?.let { SqliteDrivingStore(it) } ?: InMemoryDrivingStore()
    val checks: CheckStore = db?.let { SqliteCheckStore(it) } ?: InMemoryCheckStore()
    val settings: SettingsStore = db?.let { SqliteSettingsStore(it) } ?: InMemorySettingsStore()
    val shares: ShareStore = db?.let { SqliteShareStore(it) } ?: InMemoryShareStore()
    if (System.getenv("NEULBOM_DEMO_SEED") == "true") {
        // 영속 저장일 때는 비어 있을 때만 넣는다(재시작마다 겹쳐 쌓이지 않게).
        if (store.all().isEmpty()) seedDemo(store, LocalDate.now(REPORT_ZONE))
        if (checks.all().isEmpty()) DemoChecks.generate(LocalDate.now(REPORT_ZONE), REPORT_ZONE).forEach { checks.putIfAbsent(it) }
    }
    // 보존 기간 정리: 시작할 때 한 번, 그 뒤 하루에 한 번.
    Timer("retention", true).scheduleAtFixedRate(timerTask { purgeExpired(store, checks, SystemClock.now()) }, 0L, Duration.ofDays(1).toMillis())
    embeddedServer(Netty, port = port, host = "0.0.0.0") { neulbomModule(store, SystemClock, checks, settings, shares) }.start(wait = true)
}

fun seedDemo(store: DrivingStore, today: LocalDate, seed: Long = 20261101L) {
    val signals = MockVehicleAdapter.DEFAULT_MOCK_SIGNALS
    MockDrivingHistory.generate(today, weeks = 8, seed = seed, zone = REPORT_ZONE, signals = signals).forEach { trip ->
        store.upsert(trip.session, SessionFeatureCalculator.compute(trip.session, trip.samples))
    }
    store.putSignalSupport(
        SignalId.entries.map { s ->
            SignalSupportDto(s.name, if (s in signals) "SUPPORTED" else "UNSUPPORTED", "MOCK", if (s in signals && s != SignalId.VEHICLE_STATE) 10.0 else null)
        },
    )
}
