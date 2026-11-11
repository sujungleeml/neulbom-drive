package com.neulbom.drive.data.sync

import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.domain.consent.ConsentType
import com.neulbom.drive.domain.consent.InMemoryConsentRepository
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsSyncTest {
    private var settingsJson = ""
    private val pushed = CopyOnWriteArrayList<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/v1/settings/me") { ex ->
            val bytes = settingsJson.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }
        }
        createContext("/v1/consent-events") { ex ->
            pushed += ex.requestBody.readBytes().decodeToString()
            ex.sendResponseHeaders(204, -1); ex.close()
        }
        start()
    }
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @AfterTest fun stop() = server.stop(0)

    @Test fun appliesNewerRemoteAndPushesNewerLocal() = runTest {
        val clock = FakeClock(Instant.parse("2026-11-01T01:00:00Z"))
        val consents = InMemoryConsentRepository(clock)
        consents.update(ConsentType.DRIVING_RECORD, true) // 01:00
        consents.update(ConsentType.COGNITIVE_CHECK, true) // 01:00
        settingsJson = """{"consents":[
            {"type":"COGNITIVE_CHECK","granted":false,"changedAt":"2026-11-01T02:00:00Z"},
            {"type":"DRIVING_RECORD","granted":false,"changedAt":"2026-11-01T00:30:00Z"}],
            "offersPausedUntil":"2026-11-08T00:00:00Z","retentionDays":180}"""
        clock.advanceMs(2 * 3_600_000L) // 03:00
        val remote = SettingsSync(base, consents).sync()!!
        assertFalse(consents.state.value.isGranted(ConsentType.COGNITIVE_CHECK)) // 웹에서 나중에 끔 → 적용
        assertTrue(consents.state.value.isGranted(ConsentType.DRIVING_RECORD)) // 차량이 더 늦음 → 유지
        assertEquals(Instant.parse("2026-11-08T00:00:00Z"), remote.offersPausedUntil)
        assertTrue(pushed.single().contains("\"type\":\"DRIVING_RECORD\",\"granted\":true"))
    }

    @Test fun unreachableServerChangesNothing() = runTest {
        val consents = InMemoryConsentRepository(FakeClock())
        consents.update(ConsentType.COGNITIVE_CHECK, true)
        assertNull(SettingsSync("http://127.0.0.1:1", consents).sync())
        assertTrue(consents.state.value.isGranted(ConsentType.COGNITIVE_CHECK))
    }

    @Test fun blankBaseUrlSkips() = runTest {
        assertNull(SettingsSync("", InMemoryConsentRepository(FakeClock())).sync())
    }
}
