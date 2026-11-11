package com.neulbom.drive.ui.car

import androidx.compose.ui.unit.dp
import com.neulbom.drive.ui.layout.pageLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.components.Body
import com.neulbom.drive.ui.components.Caption
import com.neulbom.drive.ui.components.InfoCard
import com.neulbom.drive.ui.components.PrimaryButton
import com.neulbom.drive.ui.theme.Dimens

/** CAR-04: 시작 전 안내. 시작은 사용자가 누를 때만. */
@Composable
fun GuideScreen(onStart: () -> Unit, onBack: () -> Unit) {
    val l = pageLayout("guide")
    CarPage(
        title = UiText.GUIDE_TITLE,
        onBack = onBack,
        layout = l,
        actions = { PrimaryButton(UiText.GUIDE_START, onClick = onStart, modifier = Modifier.fillMaxWidth(), minHeight = l.ctaHeight.dp) },
    ) {
        InfoCard { UiText.GUIDE_POINTS.forEach { Body("• $it") } }
        Caption(UiText.GUIDE_PRACTICE_NOTE)
    }
}
