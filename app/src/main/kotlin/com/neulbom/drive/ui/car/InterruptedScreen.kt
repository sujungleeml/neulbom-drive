package com.neulbom.drive.ui.car

import com.neulbom.drive.ui.layout.pageLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neulbom.drive.domain.cognitive.DenialReason
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.components.Heading
import com.neulbom.drive.ui.components.PrimaryButton
import com.neulbom.drive.ui.theme.LocalNeulbomPalette

/** CAR-10: 안전 때문에 멈췄거나 직접 그만둔 경우. 이번 기록은 비교에 쓰지 않는다. */
@Composable
fun InterruptedScreen(cancelledByUser: Boolean, reason: DenialReason?, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val p = LocalNeulbomPalette.current
    val l = pageLayout("interrupted")
    CenterPage(layout = l) {
        Heading(if (cancelledByUser) UiText.CANCELLED_TITLE else UiText.INTERRUPTED_TITLE)
        val lines = listOfNotNull(reason?.message?.takeIf { !cancelledByUser }, UiText.INTERRUPTED_BODY)
        Text(lines.joinToString("\n"), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = p.text)
        PrimaryButton(UiText.CLOSE, onClick = onClose, modifier = Modifier.widthIn(min = l.ctaMinWidth.dp), minHeight = l.ctaHeight.dp)
    }
}
