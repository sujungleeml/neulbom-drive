package com.neulbom.drive

import com.neulbom.drive.ui.layout.LocalLayoutStudioUrl
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neulbom.drive.ui.layout.LayoutStore
import com.neulbom.drive.ui.layout.LocalScreenLayouts
import com.neulbom.drive.ui.NeulbomRoot
import com.neulbom.drive.ui.theme.NeulbomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NeulbomApp).container
        // 화면 배치 값(assets/layout). 디버그 mock 빌드에서만 Visual Layout Studio에 연결해 저장 즉시 반영한다.
        val layoutStore = LayoutStore(applicationContext, if (BuildConfig.DEMO_TOOLS_ENABLED) BuildConfig.LAYOUT_STUDIO_URL else "")
        setContent {
            val layouts by layoutStore.layouts.collectAsStateWithLifecycle()
            LaunchedEffect(layoutStore) { layoutStore.pollStudio() }
            CompositionLocalProvider(LocalScreenLayouts provides layouts, LocalLayoutStudioUrl provides layoutStore.studioUrl) {
                NeulbomTheme {
                    NeulbomRoot(container)
                }
            }
        }
    }
}
