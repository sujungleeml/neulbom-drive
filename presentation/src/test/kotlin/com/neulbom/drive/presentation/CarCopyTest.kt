package com.neulbom.drive.presentation

import com.neulbom.drive.domain.cognitive.assessment.SaveState
import kotlin.test.Test
import kotlin.test.assertEquals

/** 명세 v0.6 02 §6: 완료 상태 문구는 정확히 이 세 문장. */
class CarCopyTest {
    @Test fun completionCopyIsExact() {
        assertEquals("오늘 점검이 저장되었어요.", UiText.completion(SaveState.SERVER_SAVED))
        assertEquals("오늘 점검을 이 차에 보관했어요. 연결되면 보내 드려요.", UiText.completion(SaveState.LOCAL_PENDING_SYNC))
        assertEquals("기록을 저장하지 못했어요. 다시 확인해 주세요.", UiText.completion(SaveState.SAVE_FAILED))
    }
}

class OfferCopyTest {
    @Test fun offerTitleIsExact() {
        assertEquals("약 4분 점검을 해 보실래요?", UiText.OFFER_TITLE)
    }
}
