package com.neulbom.drive.ui.car

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.neulbom.drive.domain.cognitive.Block
import com.neulbom.drive.domain.cognitive.DenialReason
import com.neulbom.drive.domain.cognitive.ResponseKey
import com.neulbom.drive.domain.cognitive.Stimulus
import com.neulbom.drive.domain.cognitive.SwitchRule
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.TrialSpec
import com.neulbom.drive.domain.cognitive.assessment.RunnerState
import com.neulbom.drive.domain.cognitive.assessment.SaveState
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.theme.LocalNeulbomPalette
import com.neulbom.drive.ui.theme.NeulbomTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertTrue

/**
 * 차량 화면 갤러리: 에뮬레이터 없이 CAR 화면을 Pleos Connect 앱 영역(1680×1210dp, mdpi) 주간·야간 PNG로 남긴다.
 * CI가 `app/build/car-screens/`를 아티팩트로 올린다. 각 장면은 핵심 문구가 실제로 그려졌는지도 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1680dp-h1210dp-land-mdpi")
class CarScreensGalleryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Scene(val name: String, val mustShow: String, val content: @Composable () -> Unit)

    private fun spec(stimulus: Stimulus, block: Block = Block.MAIN) = TrialSpec(3, block, stimulus, null, 1000)

    private val scenes = listOf(
        Scene("02-profile", UiText.PROFILE_TITLE) { ProfileScreen(isMock = true) {} },
        Scene("03-idle-parked", UiText.CHECK_NOW) {
            IdleContent(true, true, null, "운전의 즐거움을 이어가는 작은 습관, 잠깐의 점검으로 시작해 보세요.",
                "주행 습관을 기록하고 있어요. 이 차량에서 받는 정보 3가지.", "주행 기록 1건을 보냈어요.", false, null, {})
        },
        Scene("03-idle-waiting", UiText.IDLE_TITLE_WAIT) {
            IdleContent(false, false, UiText.CHECK_UNAVAILABLE_PREFIX + DenialReason.STATE_UNKNOWN.message, "다음 정기 점검 때 알려 드릴게요.",
                "주행 습관 기록이 꺼져 있어요.", "", false, null, {})
        },
        Scene("04-offer", UiText.OFFER_TITLE) { OfferScreen(isFirst = true) {} },
        Scene("05-guide", UiText.GUIDE_TITLE) { GuideScreen({}, {}) },
        Scene("06-task-intro", "연습") {
            TaskFrame("1/3 ${UiText.taskLabel(TaskId.VISUAL_ATTENTION)} · 연습 안내", TaskId.VISUAL_ATTENTION, Phase.Intro(Block.PRACTICE), null, {}, {}, {})
        },
        Scene("07-task-arrow", "오른쪽") {
            val s = spec(Stimulus.Arrow(ResponseKey.LEFT))
            TaskFrame("1/3 ${UiText.taskLabel(TaskId.VISUAL_ATTENTION)} · 8 / 27", TaskId.VISUAL_ATTENTION, Phase.Showing(s), s, {}, {}, {})
        },
        Scene("08-task-gonogo-intro", "누르지 않기") {
            TaskFrame("2/3 ${UiText.taskLabel(TaskId.GO_NO_GO)} · 연습 안내", TaskId.GO_NO_GO, Phase.Intro(Block.PRACTICE), null, {}, {}, {})
        },
        Scene("08-task-gonogo", "누르기") {
            val s = spec(Stimulus.GoNoGo(isGo = true))
            TaskFrame("2/3 ${UiText.taskLabel(TaskId.GO_NO_GO)} · 12 / 44", TaskId.GO_NO_GO, Phase.Showing(s), s, {}, {}, {})
        },
        Scene("09-task-switch", "홀짝 규칙") {
            val s = spec(Stimulus.Digit(7, SwitchRule.PARITY), Block.MIXED)
            TaskFrame("3/3 ${UiText.taskLabel(TaskId.TASK_SWITCHING)} · 25 / 36", TaskId.TASK_SWITCHING, Phase.Showing(s), s, {}, {}, {})
        },
        Scene("10-task-feedback", "잘하셨어요") {
            val s = spec(Stimulus.Arrow(ResponseKey.RIGHT), Block.PRACTICE)
            TaskFrame("1/3 ${UiText.taskLabel(TaskId.VISUAL_ATTENTION)} · 2 / 27", TaskId.VISUAL_ATTENTION, Phase.Feedback("잘하셨어요", true), s, {}, {}, {})
        },
        Scene("11-between", "다음 활동 시작") { BetweenScreen(RunnerState.Between("a", 1, TaskId.GO_NO_GO), {}, {}) },
        Scene("12-saving", "기록을 보관하는 중이에요") { SavingScreen() },
        Scene("13-check-in", UiText.CHECK_IN_PLACEHOLDER) { CheckInPlaceholderScreen {} },
        Scene("14-completion-local", UiText.completion(SaveState.LOCAL_PENDING_SYNC)) { CompletionScreen(SaveState.LOCAL_PENDING_SYNC) {} },
        Scene("14-completion-server", UiText.completion(SaveState.SERVER_SAVED)) { CompletionScreen(SaveState.SERVER_SAVED) {} },
        Scene("14-completion-failed", UiText.completion(SaveState.SAVE_FAILED)) { CompletionScreen(SaveState.SAVE_FAILED) {} },
        Scene("15-interrupted", UiText.INTERRUPTED_TITLE) { InterruptedScreen(false, DenialReason.DRIVING) {} },
        Scene("15-cancelled", UiText.CANCELLED_TITLE) { InterruptedScreen(true, null) {} },
        Scene("16-driving", UiText.DRIVING_LOCK_TITLE) { DrivingScreen() },
    )

    @Test
    fun renderAllCarScreensDayAndNight() {
        val out = File("build/car-screens").apply { mkdirs() }
        var night by mutableStateOf(false)
        var scene by mutableStateOf(scenes.first())
        compose.setContent {
            NeulbomTheme(night = night) {
                Box(Modifier.fillMaxSize().background(LocalNeulbomPalette.current.background)) { scene.content() }
            }
        }
        for (n in listOf(false, true)) {
            for (s in scenes) {
                compose.runOnIdle { night = n; scene = s }
                compose.waitForIdle()
                assertTrue(
                    compose.onAllNodesWithText(s.mustShow, substring = true).fetchSemanticsNodes().isNotEmpty(),
                    "${s.name}: '${s.mustShow}' 없음",
                )
                // captureToImage()는 Robolectric에서 PixelCopy를 기다리다 멈춘다 → 루트 뷰를 직접 그린다(NATIVE 그래픽).
                val view = compose.activity.window.decorView
                val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                compose.runOnUiThread { view.draw(Canvas(bmp)) }
                File(out, "${s.name}-${if (n) "night" else "day"}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        assertTrue(out.listFiles()!!.count { it.extension == "png" } >= scenes.size * 2)
    }
}
