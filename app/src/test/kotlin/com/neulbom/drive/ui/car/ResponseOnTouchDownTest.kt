package com.neulbom.drive.ui.car

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.neulbom.drive.domain.cognitive.Block
import com.neulbom.drive.domain.cognitive.ResponseKey
import com.neulbom.drive.domain.cognitive.Stimulus
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.TrialSpec
import com.neulbom.drive.ui.theme.NeulbomTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** 반응은 손가락이 닿는 순간 기록된다(뗄 때가 아님). 스크린리더의 두 번 탭도 한 번만 기록. */
@RunWith(RobolectricTestRunner::class)
class ResponseOnTouchDownTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<ResponseKey>()

    private fun show(enabled: Boolean = true) {
        val spec = TrialSpec(0, Block.MAIN, Stimulus.Arrow(ResponseKey.LEFT), ResponseKey.LEFT, 800)
        compose.setContent {
            NeulbomTheme(night = false) {
                TaskFrame("1/3", TaskId.VISUAL_ATTENTION, if (enabled) Phase.Showing(spec) else Phase.Fixation, spec, {}, {}, { events += it })
            }
        }
    }

    @Test
    fun responseIsRecordedOnTouchDownBeforeRelease() {
        show()
        compose.onNodeWithText("왼쪽").performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(listOf(ResponseKey.LEFT), events) // 아직 손을 떼지 않았다
        compose.onNodeWithText("왼쪽").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf(ResponseKey.LEFT), events) // 뗄 때 다시 기록하지 않는다
    }

    @Test
    fun accessibilityClickRecordsOnce() {
        show()
        compose.onNodeWithText("오른쪽").performClick()
        compose.waitForIdle()
        assertEquals(listOf(ResponseKey.RIGHT), events)
    }

    @Test
    fun disabledButtonsIgnoreTouches() {
        show(enabled = false)
        compose.onNodeWithText("왼쪽").performTouchInput { down(center); up() }
        compose.waitForIdle()
        assertEquals(emptyList(), events)
    }
}
