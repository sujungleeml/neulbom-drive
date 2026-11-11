package com.neulbom.drive.ui.layout

import android.content.Context
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * 화면 배치 값 저장소. 앱에 들어 있는 `assets/layout/idle.json`을 읽고,
 * 개발용(디버그 + mock 빌드)일 때만 Visual Layout Studio 서버를 주기적으로 읽어 바로 바꾼다(Hot Reload).
 * 운영 빌드에서는 studioUrl이 비어 있어 네트워크를 쓰지 않는다.
 */
class LayoutStore(private val context: Context, val studioUrl: String) {
    private val state = MutableStateFlow(load())
    val layouts: StateFlow<ScreenLayouts> = state

    /** Hot Reload가 켜져 있는지(화면에 '개발용 배치 연결' 표시를 할 때 쓴다). */
    val liveReload: Boolean get() = studioUrl.isNotEmpty()

    private fun load(): ScreenLayouts = ScreenLayouts.parseAll(
        ScreenLayouts.SCREENS.mapNotNull { screen ->
            try {
                context.assets.open("layout/$screen.json").bufferedReader().use { screen to it.readText() }
            } catch (e: Exception) {
                Log.w(TAG, "배치 파일 layout/$screen.json을 읽지 못해 코드 기본값을 씁니다", e)
                null
            }
        }.toMap(),
    )

    /** 편집기 서버를 주기적으로 읽는다. 서버가 꺼져 있으면 조용히 기다린다. */
    suspend fun pollStudio(intervalMs: Long = 1000) {
        if (!liveReload) return
        while (coroutineContext.isActive) {
            fetch()?.let { if (it.revision != state.value.revision) state.value = it }
            delay(intervalMs)
        }
    }

    private suspend fun fetch(): ScreenLayouts? = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$studioUrl/api/layouts?client=app").openConnection() as HttpURLConnection
            conn.connectTimeout = 800
            conn.readTimeout = 800
            try {
                if (conn.responseCode != 200) return@withContext null
                val all = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                ScreenLayouts.parseAll(all.keys().asSequence().associateWith { all.getJSONObject(it).toString() })
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "LayoutStore"
    }
}

/** 지금 쓰는 배치 값. 미리 보기·테스트에서는 코드 기본값. */
val LocalScreenLayouts = staticCompositionLocalOf { ScreenLayouts() }
