package com.neulbom.drive.ui.car

import com.neulbom.drive.ui.layout.pageLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neulbom.drive.domain.scheduler.OfferResponse
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.components.Body
import com.neulbom.drive.ui.components.Caption
import com.neulbom.drive.ui.components.Heading
import com.neulbom.drive.ui.components.PrimaryButton
import com.neulbom.drive.ui.components.SecondaryButton
import com.neulbom.drive.ui.theme.Dimens

/** CAR-03: 주차 확인 직후의 제안. 세 가지 응답을 같은 크기로 보여 준다(거절을 숨기거나 작게 만들지 않는다). */
@Composable
fun OfferScreen(isFirst: Boolean, onRespond: (OfferResponse) -> Unit) {
    // 세 버튼 문구가 한 줄에 들어가도록 기본보다 넓게.
    val l = pageLayout("offer")
    CenterPage(layout = l) {
        Heading(UiText.OFFER_TITLE)
        Body(UiText.OFFER_BODY)
        if (isFirst) Caption(UiText.OFFER_FIRST_NOTE)
        Caption(UiText.OFFER_INTERVAL_NOTE)
        // 세 버튼은 같은 너비·같은 높이. 시작만 색으로 구분하고 크기로 떠밀지 않는다.
        Row(horizontalArrangement = Arrangement.spacedBy(l.ctaGap.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(UiText.OFFER_START, onClick = { onRespond(OfferResponse.ACCEPTED) }, modifier = Modifier.weight(1f), minHeight = l.ctaHeight.dp)
            SecondaryButton(UiText.OFFER_LATER, onClick = { onRespond(OfferResponse.LATER) }, modifier = Modifier.weight(1f), minHeight = l.ctaHeight.dp)
            SecondaryButton(UiText.OFFER_NOT_TODAY, onClick = { onRespond(OfferResponse.NOT_TODAY) }, modifier = Modifier.weight(1f), minHeight = l.ctaHeight.dp)
        }
    }
}
