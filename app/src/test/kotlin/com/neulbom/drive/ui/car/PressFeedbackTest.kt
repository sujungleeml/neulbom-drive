package com.neulbom.drive.ui.car

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.neulbom.drive.domain.cognitive.Block
import com.neulbom.drive.domain.cognitive.Stimulus
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.TrialSpec
import com.neulbom.drive.ui.theme.NeulbomTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** 누름 피드백(햅틱)은 반응 기록 **뒤에** 일어나야 한다 — 측정 경로를 늦추지 않도록. */
@RunWith(RobolectricTestRunner::class)
class PressFeedbackTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pressFeedbackDoesNotDelayResponse() {
        val events = mutableListOf<String>()
        val haptic = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { events += "haptic" }
        }
        val spec = TrialSpec(0, Block.MAIN, Stimulus.GoNoGo(true), null, 600)
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                NeulbomTheme(night = false) {
                    TaskFrame("2/3", TaskId.GO_NO_GO, Phase.Showing(spec), spec, {}, {}, { k -> events += "respond:$k" })
                }
            }
        }
        compose.onNodeWithText("누르기").performClick()
        compose.waitForIdle()
        assertEquals(listOf("respond:TAP", "haptic"), events)
    }
}

@RunWith(RobolectricTestRunner::class)
// 기본 좁은 화면에서는 weight 분할이 1px씩 어긋난다 → 실제 Pleos 앱 영역에서 잰다.
@org.robolectric.annotation.Config(qualifiers = "w1680dp-h1210dp-land-mdpi")
class OfferChoicesTest {
    @get:Rule
    val compose = createComposeRule()

    /** 거절 선택지를 작게 만들어 시작 쪽으로 떠밀지 않는다. */
    @Test
    fun allThreeChoicesHaveTheSameSize() {
        compose.setContent { NeulbomTheme(night = false) { OfferScreen(isFirst = false) {} } }
        val sizes = listOf(com.neulbom.drive.presentation.UiText.OFFER_START, com.neulbom.drive.presentation.UiText.OFFER_LATER, com.neulbom.drive.presentation.UiText.OFFER_NOT_TODAY)
            .map { compose.onNodeWithText(it).fetchSemanticsNode().let { n -> n.size } }
        assertEquals(1, sizes.distinct().size, sizes.toString())
    }
}
