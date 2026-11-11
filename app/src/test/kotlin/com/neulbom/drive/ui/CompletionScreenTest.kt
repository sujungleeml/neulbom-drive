package com.neulbom.drive.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import com.neulbom.drive.domain.cognitive.assessment.SaveState
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.car.CompletionScreen
import com.neulbom.drive.ui.theme.NeulbomTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

/** CAR-09: 완료 화면은 저장 상태 문구와 "닫기"만 — 숫자·반응시간·백분율 없음(명세 v0.6 02 §6). */
@RunWith(RobolectricTestRunner::class)
class CompletionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun allText(): String = compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
        .joinToString(" ")

    @Test
    fun showsNoNumbers() {
        var current by mutableStateOf(SaveState.SERVER_SAVED)
        compose.setContent { NeulbomTheme(night = false) { CompletionScreen(current, onClose = {}) } }
        SaveState.entries.forEach { state ->
            compose.runOnIdle { current = state }
            compose.waitForIdle()
            val text = allText()
            assertTrue(text.isNotBlank())
            assertTrue(text.none { it.isDigit() }, "숫자 포함: $text")
            assertTrue("ms" !in text && "%" !in text, "단위 포함: $text")
        }
    }

    @Test
    fun localPendingShowsKeepMessage() {
        compose.setContent { NeulbomTheme(night = false) { CompletionScreen(SaveState.LOCAL_PENDING_SYNC, onClose = {}) } }
        compose.onNodeWithText("오늘 점검을 이 차에 보관했어요. 연결되면 보내 드려요.").assertExists()
        compose.onNodeWithText(UiText.CLOSE).assertExists()
    }
}
