package com.neulbom.drive.ui.car

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.components.ChipTone
import com.neulbom.drive.ui.components.Heading
import com.neulbom.drive.ui.components.PrimaryButton
import com.neulbom.drive.ui.components.StatusChip

/** CAR-08: 4주 정기 점검 뒤 짧은 문항 자리. 문항은 미확정(open-questions) — 임시 화면. */
@Composable
fun CheckInPlaceholderScreen(onNext: () -> Unit) {
    CenterPage {
        Heading(UiText.CHECK_IN_TITLE)
        StatusChip(UiText.CHECK_IN_PLACEHOLDER, ChipTone.CAUTION)
        PrimaryButton(UiText.CHECK_IN_NEXT, onClick = onNext, modifier = Modifier.widthIn(min = 280.dp))
    }
}
