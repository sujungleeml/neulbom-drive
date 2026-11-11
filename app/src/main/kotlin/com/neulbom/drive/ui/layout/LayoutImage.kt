package com.neulbom.drive.ui.layout

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Visual Layout Studio 주소(개발용 Hot Reload). 비어 있으면 앱에 들어 있는 파일만 쓴다. */
val LocalLayoutStudioUrl = staticCompositionLocalOf { "" }

private val cache = LruCache<String, ImageBitmap>(8)

/**
 * 배치 값의 이미지 경로(assets/layout 기준)를 그린다. 경로가 비었거나 읽지 못하면 앱 기본 그림(fallback).
 * 개발용 빌드에서는 편집기에 막 올린 그림을 먼저 편집기 서버에서 읽고, 없으면 앱에 들어 있는 assets에서 읽는다.
 */
@Composable
fun layoutPainter(path: String, @DrawableRes fallback: Int): Painter {
    val fallbackPainter = painterResource(fallback)
    if (path.isEmpty()) return fallbackPainter
    val context = LocalContext.current
    val studio = LocalLayoutStudioUrl.current
    val bitmap by produceState(cache.get(path), path, studio) { value = cache.get(path) ?: load(context, studio, path)?.also { cache.put(path, it) } }
    return bitmap?.let { BitmapPainter(it) } ?: fallbackPainter
}

private suspend fun load(context: Context, studio: String, path: String): ImageBitmap? = withContext(Dispatchers.IO) {
    val bytes = (if (studio.isNotEmpty()) fetch("$studio/files/$path") else null)
        ?: runCatching { context.assets.open("layout/$path").use { it.readBytes() } }.getOrNull()
    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
}

private fun fetch(url: String): ByteArray? = try {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 1500
    c.readTimeout = 5000
    try { if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null } finally { c.disconnect() }
} catch (e: Exception) {
    null
}
