package com.neulbom.drive.ui.car

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.neulbom.drive.domain.cognitive.Block
import com.neulbom.drive.domain.cognitive.ResponseKey
import com.neulbom.drive.domain.cognitive.Stimulus
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.TrialSpec
import com.neulbom.drive.ui.theme.NeulbomTheme
import com.neulbom.drive.voice.Speaker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 음성은 과제 설명 단계에서만 — 자극이 나오는 동안에는 멈춰 있어야 한다(측정에 소리가 끼어들지 않게). */
@RunWith(RobolectricTestRunner::class)
class TaskVoiceTest {
    @get:Rule
    val compose = createComposeRule()

    private class FakeSpeaker : Speaker {
        val log = mutableListOf<String>()
        override fun speak(text: String) { log += "speak" }
        override fun stop() { log += "stop" }
    }

    @Test
    fun introIsReadAndSpeechStopsWhenTrialsBegin() {
        val speaker = FakeSpeaker()
        val spec = TrialSpec(0, Block.PRACTICE, Stimulus.Arrow(ResponseKey.LEFT), ResponseKey.LEFT, 800)
        var phase by mutableStateOf<Phase>(Phase.Intro(Block.PRACTICE))
        compose.setContent {
            NeulbomTheme(night = false) { TaskFrame("1/3", TaskId.VISUAL_ATTENTION, phase, spec, {}, {}, {}, speaker) }
        }
        compose.waitForIdle()
        assertEquals(listOf("speak"), speaker.log)
        compose.runOnIdle { phase = Phase.Showing(spec) }
        compose.waitForIdle()
        assertEquals("stop", speaker.log.last())
        assertTrue(speaker.log.count { it == "speak" } == 1) // 자극 중에는 새로 말하지 않음
    }
}
