package com.neulbom.drive.domain.cognitive.protocol

import com.neulbom.drive.domain.cognitive.ProvisionalProtocols
import com.neulbom.drive.domain.cognitive.ResponseCapture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 반응 시각은 손가락이 닿는 순간(touch-down). 측정 방식이 바뀌었으므로 버전 2부터(1과 비교하지 않음). */
class ResponseCaptureTest {
    @Test
    fun responsesAreCapturedAtTouchDownFromVersion2() {
        ProvisionalProtocols.ALL.forEach {
            assertEquals(ResponseCapture.TOUCH_DOWN, it.responseCapture, it.taskId.name)
            assertTrue(it.taskVersion >= 2, it.taskId.name)
        }
    }
}
