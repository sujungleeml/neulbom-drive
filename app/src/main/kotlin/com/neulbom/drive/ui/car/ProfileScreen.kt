package com.neulbom.drive.ui.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neulbom.drive.presentation.ProfileAnswer
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.components.Caption
import com.neulbom.drive.ui.components.ChipTone
import com.neulbom.drive.ui.components.Heading
import com.neulbom.drive.ui.components.InfoCard
import com.neulbom.drive.ui.components.PrimaryButton
import com.neulbom.drive.ui.components.SecondaryButton
import com.neulbom.drive.ui.components.StatusChip
import com.neulbom.drive.ui.theme.Dimens

/**
 * CAR-02 운전자 확인. 차량 운전자 프로필 API가 확인되지 않아(UNVERIFIED) mock 프로필 한 명으로 묻는다.
 * TODO(PLEOS-PROFILE): 실제 차량 프로필 연동 시 이름과 확인 방식을 교체.
 */
@Composable
fun ProfileScreen(isMock: Boolean, onAnswer: (ProfileAnswer) -> Unit) {
    CenterPage {
        Heading(UiText.PROFILE_TITLE)
        InfoCard {
            Heading(UiText.PROFILE_MOCK_NAME)
            if (isMock) StatusChip(UiText.MOCK_VEHICLE_BADGE, ChipTone.CAUTION)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(UiText.PROFILE_NO, onClick = { onAnswer(ProfileAnswer.NOT_ME) }, modifier = Modifier.weight(1f), minHeight = Dimens.BigTouch)
            PrimaryButton(UiText.PROFILE_YES, onClick = { onAnswer(ProfileAnswer.CONFIRMED) }, modifier = Modifier.weight(1f), minHeight = Dimens.BigTouch)
        }
        Caption(UiText.PROFILE_NOT_ME_NOTE)
    }
}
