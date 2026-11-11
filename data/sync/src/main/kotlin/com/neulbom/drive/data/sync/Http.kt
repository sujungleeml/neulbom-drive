package com.neulbom.drive.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** HTTP 전송 한 번. 응답 코드만 돌려준다(네트워크 오류는 -1). */
fun interface HttpTransport {
    fun post(url: String, body: String, headers: Map<String, String>): Int
}

object UrlConnectionTransport : HttpTransport {
    override fun post(url: String, body: String, headers: Map<String, String>): Int = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 5_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toByteArray()) }
        conn.responseCode.also { conn.disconnect() }
    } catch (e: java.io.IOException) {
        -1
    }
}

/** GET 한 번. 실패하면 null(네트워크 오류·2xx 아님). */
fun interface HttpGetter {
    fun get(url: String): String?
}

object UrlConnectionGetter : HttpGetter {
    override fun get(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes().decodeToString() } else null
        } finally {
            conn.disconnect()
        }
    } catch (e: java.io.IOException) {
        null
    }
}

/**
 * 재시도 규칙(명세 v0.6 04 §5): 2xx 성공, 4xx는 재시도하지 않음, 5xx·네트워크 오류는 1초→2초 간격으로 최대 3회.
 */
class RetryingPoster(
    private val transport: HttpTransport,
    private val sleeper: suspend (Long) -> Unit,
) {
    suspend fun send(url: String, body: String, headers: Map<String, String>): Boolean {
        var wait = 1_000L
        repeat(MAX_ATTEMPTS) { attempt ->
            val code = withContext(Dispatchers.IO) { transport.post(url, body, headers) }
            when {
                code in 200..299 -> return true
                code in 400..499 -> return false
                attempt < MAX_ATTEMPTS - 1 -> { sleeper(wait); wait *= 2 }
            }
        }
        return false
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

/** 손으로 쓰는 JSON 조각(이 모듈은 직렬화 라이브러리를 쓰지 않는다). */
internal object Json {
    fun q(s: String?): String = if (s == null) "null" else "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    fun n(v: Number?): String = v?.toString() ?: "null"
}
