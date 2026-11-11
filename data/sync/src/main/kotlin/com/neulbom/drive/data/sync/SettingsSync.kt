package com.neulbom.drive.data.sync

import com.neulbom.drive.domain.consent.CONSENT_POLICY_VERSION
import com.neulbom.drive.domain.consent.ConsentMerge
import com.neulbom.drive.domain.consent.ConsentRecord
import com.neulbom.drive.domain.consent.ConsentRepository
import com.neulbom.drive.domain.consent.ConsentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/** 서버(웹 설정)에서 받은 값. */
data class RemoteSettings(val consents: List<ConsentRecord>, val offersPausedUntil: Instant?)

/**
 * 웹 설정 ↔ 차량 동기화(명세 v0.6 W-04). 서버 값을 받아 더 늦게 바뀐 동의만 차량에 적용하고,
 * 차량에서 더 늦게 바뀐 동의는 서버로 올린다([ConsentMerge]). 연결이 안 되면 아무것도 바꾸지 않는다.
 */
class SettingsSync(
    private val baseUrl: String,
    private val consents: ConsentRepository,
    private val getter: HttpGetter = UrlConnectionGetter,
    transport: HttpTransport = UrlConnectionTransport,
    sleeper: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    private val poster = RetryingPoster(transport, sleeper)

    suspend fun sync(): RemoteSettings? {
        if (baseUrl.isBlank()) return null
        val body = withContext(Dispatchers.IO) { getter.get("$baseUrl/v1/settings/me") } ?: return null
        val remote = runCatching { parse(body) }.getOrNull() ?: return null
        val local = consents.state.value.records
        ConsentMerge.toApply(local, remote.consents).forEach { consents.update(it.type, it.granted) }
        val push = ConsentMerge.toPush(consents.state.value.records, remote.consents)
        if (push.isNotEmpty()) {
            poster.send("$baseUrl/v1/consent-events", push.joinToString(",", "[", "]") {
                "{\"type\":${Json.q(it.type.name)},\"granted\":${it.granted},\"changedAt\":${Json.q(it.changedAt.toString())}}"
            }, emptyMap())
        }
        return remote
    }

    private fun parse(body: String): RemoteSettings {
        val o = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
        val list = o["consents"]!!.jsonArray.mapNotNull { e ->
            val c = e.jsonObject
            val type = runCatching { ConsentType.valueOf(c["type"]!!.jsonPrimitive.content) }.getOrNull() ?: return@mapNotNull null
            ConsentRecord(type, c["granted"]!!.jsonPrimitive.content.toBoolean(), CONSENT_POLICY_VERSION, Instant.parse(c["changedAt"]!!.jsonPrimitive.content))
        }
        val paused = o["offersPausedUntil"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.let(Instant::parse)
        return RemoteSettings(list, paused)
    }
}
