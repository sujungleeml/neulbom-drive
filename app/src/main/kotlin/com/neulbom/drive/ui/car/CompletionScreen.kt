package com.neulbom.drive.ui.car

import com.neulbom.drive.ui.layout.layoutPainter
import com.neulbom.drive.ui.layout.pageLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.neulbom.drive.R
import com.neulbom.drive.domain.cognitive.assessment.SaveState
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.components.Heading
import com.neulbom.drive.ui.components.PrimaryButton
import com.neulbom.drive.ui.theme.LocalNeulbomPalette
import com.neulbom.drive.ui.theme.isNight

/** CAR-09: 저장 상태 문구와 "닫기"만. 점수·반응시간·추세는 차 안에서 보여 주지 않는다. */
@Composable
fun CompletionScreen(saveState: SaveState, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val p = LocalNeulbomPalette.current
    val l = pageLayout("completion")
    CenterPage(layout = l) {
        // 완료 일러스트(docs/design/assets/v3). 장식이라 접근성 설명은 두지 않는다.
        Image(
            painter = layoutPainter(l.image, if (p.isNight) R.drawable.completion_dark else R.drawable.completion_light),
            contentDescription = null,
            modifier = Modifier.size(l.imageSize.dp),
        )
        Heading(UiText.COMPLETION_TITLE)
        Text(
            UiText.completion(saveState),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = if (saveState == SaveState.SAVE_FAILED) p.caution else p.text,
        )
        PrimaryButton(UiText.CLOSE, onClick = onClose, modifier = Modifier.widthIn(min = l.ctaMinWidth.dp), minHeight = l.ctaHeight.dp)
    }
}
