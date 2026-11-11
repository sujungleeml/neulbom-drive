package com.neulbom.drive.ui.car

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neulbom.drive.ui.theme.LocalNeulbomPalette
import com.neulbom.drive.ui.theme.NeulbomColors
import com.neulbom.drive.ui.theme.NeulbomTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals

/**
 * Go/No-Go 자극 변경 전후 비교(측정 규격 검토용). 이전 v2(● ✕ 160sp 글리프, 같은 글자색)와
 * 현재 v3(512dp 캔버스 벡터 동그라미, 빨간 가위표)를 같은 1680×1210dp 캔버스에 나란히 그린다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1680dp-h1210dp-land-mdpi")
class GoNoGoStimulusPreviewTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun renderGoNoGoComparisonDayAndNight() {
        val out = File("build/car-screens").apply { mkdirs() }
        var night by mutableStateOf(false)
        compose.setContent {
            NeulbomTheme(night = night) {
                Box(Modifier.fillMaxSize().background(LocalNeulbomPalette.current.background)) { Comparison() }
            }
        }
        for (n in listOf(false, true)) {
            compose.runOnIdle { night = n }
            compose.waitForIdle()
            assertEquals(1, compose.onAllNodesWithContentDescription("멈춤 신호").fetchSemanticsNodes().size)
            val view = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { view.draw(Canvas(bmp)) }
            File(out, "18-gonogo-compare-${if (n) "night" else "day"}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

@Composable
private fun Comparison() {
    Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.height(1040.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                Text("●", fontSize = 160.sp, fontWeight = FontWeight.Bold, color = NeulbomColors.Text)
                Text("✕", fontSize = 160.sp, fontWeight = FontWeight.Bold, color = NeulbomColors.Text)
            }
            Text("이전 v2 · 글리프 160sp", style = MaterialTheme.typography.labelMedium, color = NeulbomColors.TextSecondary)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.height(1040.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                GoNoGoStimulus(isGo = true)
                GoNoGoStimulus(isGo = false)
            }
            Text("현재 v3 · 벡터 512dp, 가위표 빨강", style = MaterialTheme.typography.labelMedium, color = NeulbomColors.TextSecondary)
        }
    }
}
