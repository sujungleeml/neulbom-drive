package com.neulbom.drive.server

import com.neulbom.drive.core.time.FakeClock
import com.neulbom.drive.server.checks.InMemoryCheckStore
import com.neulbom.drive.server.driving.InMemoryDrivingStore
import com.neulbom.drive.server.settings.InMemorySettingsStore
import com.neulbom.drive.server.share.InMemoryShareStore
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareLinksTest {
    private val clock = FakeClock(Instant.parse("2026-11-05T03:00:00Z"))
    private val shares = InMemoryShareStore()
    private val checks = InMemoryCheckStore()

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { neulbomModule(InMemoryDrivingStore(), clock, checks, InMemorySettingsStore(), shares) }
        block()
    }

    private suspend fun ApplicationTestBuilder.create(body: String) = client.post("/v1/share-links") {
        contentType(ContentType.Application.Json); setBody(body)
    }
    private suspend fun ApplicationTestBuilder.createOk(scopes: String = "\"DRIVING_SUMMARY\""): JsonObject =
        Json.parseToJsonElement(create("""{"scopes":[$scopes],"days":7}""").bodyAsText()).jsonObject

    @Test fun noScopeIsRejected() = app {
        assertEquals(HttpStatusCode.BadRequest, create("""{"scopes":[],"days":7}""").status)
        assertEquals(HttpStatusCode.BadRequest, create("""{"scopes":["EVERYTHING"],"days":7}""").status)
        assertEquals(HttpStatusCode.BadRequest, create("""{"scopes":["DRIVING_SUMMARY"],"days":90}""").status)
    }

    @Test fun unselectedScopeIsAbsent() = app {
        val link = createOk()
        assertEquals("2026-11-12T03:00:00Z", link["expiresAt"]!!.jsonPrimitive.content)
        val view = Json.parseToJsonElement(client.get("/v1/shared/${link["token"]!!.jsonPrimitive.content}").bodyAsText()).jsonObject
        assertTrue("driving" in view)
        assertFalse("checks" in view) // 선택하지 않은 범위는 키 자체가 없다
        val both = createOk("\"DRIVING_SUMMARY\",\"CHECK_RECORDS\"")
        val v2 = Json.parseToJsonElement(client.get("/v1/shared/${both["token"]!!.jsonPrimitive.content}").bodyAsText()).jsonObject
        assertTrue("driving" in v2 && "checks" in v2)
    }

    @Test fun expiredLinkIsGone() = app {
        val token = createOk()["token"]!!.jsonPrimitive.content
        clock.advanceMs(7L * 86_400_000 + 1)
        assertEquals(HttpStatusCode.Gone, client.get("/v1/shared/$token").status)
    }

    @Test fun revokedLinkIsGone() = app {
        val link = createOk()
        assertEquals(HttpStatusCode.NoContent, client.delete("/v1/share-links/${link["id"]!!.jsonPrimitive.content}").status)
        assertEquals(HttpStatusCode.Gone, client.get("/v1/shared/${link["token"]!!.jsonPrimitive.content}").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/shared/not-a-token").status)
        val list = Json.parseToJsonElement(client.get("/v1/share-links").bodyAsText()).jsonArray
        assertEquals("REVOKED", list.single().jsonObject["state"]!!.jsonPrimitive.content)
    }

    @Test fun viewsAreLogged() = app {
        val token = createOk()["token"]!!.jsonPrimitive.content
        client.get("/v1/shared/$token"); client.get("/v1/shared/$token")
        val list = Json.parseToJsonElement(client.get("/v1/share-links").bodyAsText()).jsonArray
        assertEquals("2", list.single().jsonObject["views"]!!.jsonPrimitive.content)
        assertFalse("token" in list.single().jsonObject) // 목록에는 토큰을 다시 보여 주지 않는다
    }

    @Test fun tokenIsLongAndUrlSafe() = app {
        val tokens = (1..20).map { createOk()["token"]!!.jsonPrimitive.content }
        assertEquals(20, tokens.toSet().size)
        tokens.forEach { assertTrue(it.length >= 22 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' }, it) }
    }

    @Test fun deletingMyDataRevokesLinks() = app {
        val token = createOk()["token"]!!.jsonPrimitive.content
        client.delete("/v1/me/data")
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/shared/$token").status)
    }

    @Test fun sharePageIsServed() = app {
        val html = client.get("/s/anything").bodyAsText()
        assertTrue("share.js" in html)
    }
}
