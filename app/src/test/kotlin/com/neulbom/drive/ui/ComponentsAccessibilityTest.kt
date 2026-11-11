package com.neulbom.drive.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.unit.dp
import com.neulbom.drive.ui.components.ChipTone
import com.neulbom.drive.ui.components.PrimaryButton
import com.neulbom.drive.ui.components.SecondaryButton
import com.neulbom.drive.ui.components.StatusChip
import com.neulbom.drive.ui.components.ToggleRow
import com.neulbom.drive.ui.theme.NeulbomColors
import com.neulbom.drive.ui.theme.NeulbomTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertNotEquals

/** T08 접근성: 큰 터치 영역, 상태를 읽을 수 있는 토글. G17 디자인 토큰. */
@RunWith(RobolectricTestRunner::class)
class ComponentsAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun primaryButtonHasLargeTouchTarget() {
        compose.setContent { NeulbomTheme(night = false) { PrimaryButton("점검 시작하기", onClick = {}, enabled = false) } }
        compose.onNodeWithText("점검 시작하기").assertHeightIsAtLeast(72.dp).assertIsNotEnabled()
    }

    @Test
    fun secondaryButtonHasLargeTouchTargetAtNight() {
        compose.setContent { NeulbomTheme(night = true) { SecondaryButton("나중에", onClick = {}) } }
        compose.onNodeWithText("나중에").assertHeightIsAtLeast(72.dp)
    }

    @Test
    fun statusChipShowsItsText() {
        compose.setContent { NeulbomTheme(night = false) { StatusChip("주차 확인됨", ChipTone.OK) } }
        compose.onNodeWithText("주차 확인됨").assertExists()
    }

    @Test
    fun toggleRowExposesStateDescription() {
        compose.setContent { NeulbomTheme(night = false) { ToggleRow("음성 안내", checked = false, onChange = {}) } }
        compose.onNodeWithText("음성 안내", useUnmergedTree = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "꺼짐"))
    }

    @Test
    fun nightPaletteDiffersFromDay() {
        assertNotEquals(NeulbomColors.day.background, NeulbomColors.night.background)
        assertNotEquals(NeulbomColors.day.ctaBackground, NeulbomColors.night.ctaBackground)
    }
}
