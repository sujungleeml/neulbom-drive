package com.neulbom.drive.ui.car

import androidx.compose.ui.unit.sp
import com.neulbom.drive.ui.layout.pageLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.theme.LocalNeulbomPalette

/** 주행 중: 조작 요소 없이 짧은 글만. 화면 터치는 흡수한다(새 팝업·점검·시각적 주의 요구 금지). */
@Composable
fun DrivingScreen() {
    val p = LocalNeulbomPalette.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        val l = pageLayout("driving")
        CenterPage(layout = l) {
            Text(UiText.DRIVING_LOCK_TITLE, style = MaterialTheme.typography.headlineLarge.copy(fontSize = l.titleFontSize.sp, lineHeight = (l.titleFontSize * 1.21f).sp), color = p.primary)
            Text(UiText.DRIVING_LOCK_BODY, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = p.textSecondary)
        }
    }
}
