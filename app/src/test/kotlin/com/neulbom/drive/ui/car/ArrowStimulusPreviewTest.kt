package com.neulbom.drive.ui.car

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neulbom.drive.R
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
import kotlin.test.assertTrue

/**
 * 화살표 자극 비교 프리뷰. 이전 글리프(v2, ← → 160sp)와 v3 벡터(512dp, 더 작은 후보 포함)를
 * 같은 1680×1210dp 캔버스에 나란히 그린다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1680dp-h1210dp-land-mdpi")
class ArrowStimulusPreviewTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun renderArrowComparisonDayAndNight() {
        val out = File("build/car-screens").apply { mkdirs() }
        var night by mutableStateOf(false)
        compose.setContent {
            NeulbomTheme(night = night) {
                Box(Modifier.fillMaxSize().background(LocalNeulbomPalette.current.background)) { ArrowComparison() }
            }
        }
        for (n in listOf(false, true)) {
            compose.runOnIdle { night = n }
            compose.waitForIdle()
            val view = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { view.draw(Canvas(bmp)) }
            File(out, "17-arrow-compare-${if (n) "night" else "day"}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertTrue(File(out, "17-arrow-compare-day.png").exists())
    }
}

@Composable
private fun ArrowComparison() {
    Row(
        Modifier.fillMaxSize().padding(48.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Candidate("이전 v2 · 글리프 160sp") {
            Text("←", fontSize = 160.sp, fontWeight = FontWeight.Bold, color = NeulbomColors.Text)
            Text("→", fontSize = 160.sp, fontWeight = FontWeight.Bold, color = NeulbomColors.Text)
        }
        Candidate("벡터 160dp") { VectorPair(160.dp) }
        Candidate("벡터 256dp") { VectorPair(256.dp) }
        Candidate("현재 v3 · 벡터 512dp") {
            // 실제 과제 화면과 같은 컴포저블.
            ArrowStimulus(left = true)
            ArrowStimulus(left = false)
        }
    }
}

@Composable
private fun Candidate(label: String, arrows: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.height(1040.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) { arrows() }
        Text(label, style = MaterialTheme.typography.labelMedium, color = NeulbomColors.TextSecondary)
    }
}

@Composable
private fun VectorPair(size: Dp) {
    val tint = ColorFilter.tint(NeulbomColors.Text)
    Image(painterResource(R.drawable.stimulus_arrow), contentDescription = "왼쪽 화살표", colorFilter = tint, modifier = Modifier.size(size))
    Image(
        painterResource(R.drawable.stimulus_arrow), contentDescription = "오른쪽 화살표", colorFilter = tint,
        modifier = Modifier.size(size).graphicsLayer { scaleX = -1f },
    )
}
