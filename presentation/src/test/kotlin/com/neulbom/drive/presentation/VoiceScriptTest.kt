package com.neulbom.drive.presentation

import com.neulbom.drive.domain.cognitive.DenialReason
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.assessment.SaveState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceScriptTest {
    @Test
    fun silentWhileDrivingAndDuringTrials() {
        assertNull(VoiceScript.forScreen(CarScreen.DRIVING, VoiceContext()))
        assertNull(VoiceScript.forScreen(CarScreen.TASK, VoiceContext())) // 과제 설명은 TaskFrame이 단계별로 따로, 자극 중에는 말하지 않음
        assertNull(VoiceScript.forScreen(CarScreen.SAVING, VoiceContext()))
    }

    @Test
    fun completionSaysOnlyTheSaveStateSentence() {
        assertEquals(
            UiText.COMPLETION_TITLE + ". " + UiText.completion(SaveState.LOCAL_PENDING_SYNC),
            VoiceScript.forScreen(CarScreen.COMPLETION, VoiceContext(saveState = SaveState.LOCAL_PENDING_SYNC)),
        )
    }

    @Test
    fun offerReadsAllThreeChoices() {
        val t = VoiceScript.forScreen(CarScreen.OFFER, VoiceContext())!!
        listOf(UiText.OFFER_TITLE, UiText.OFFER_START, UiText.OFFER_LATER, UiText.OFFER_NOT_TODAY).forEach { assertTrue(it in t, it) }
    }

    @Test
    fun betweenAndInterruptedAreSpecific() {
        assertTrue(UiText.taskLabel(TaskId.GO_NO_GO) in VoiceScript.forScreen(CarScreen.BETWEEN, VoiceContext(nextTask = TaskId.GO_NO_GO))!!)
        val stopped = VoiceScript.forScreen(CarScreen.INTERRUPTED, VoiceContext(stopReason = DenialReason.DRIVING))!!
        assertTrue(UiText.INTERRUPTED_TITLE in stopped && DenialReason.DRIVING.message in stopped)
        assertTrue(UiText.CANCELLED_TITLE in VoiceScript.forScreen(CarScreen.INTERRUPTED, VoiceContext(cancelled = true))!!)
    }

    @Test
    fun everySpokenLineIsFreeOfForbiddenWords() {
        val forbidden = listOf("치매", "진단", "위험", "정상", "저하", "판정", "적합", "면허", "점수", "등급", "인증")
        val ctxs = listOf(
            VoiceContext(), VoiceContext(saveState = SaveState.SERVER_SAVED), VoiceContext(saveState = SaveState.SAVE_FAILED),
            VoiceContext(nextTask = TaskId.TASK_SWITCHING), VoiceContext(stopReason = DenialReason.POWERING_OFF), VoiceContext(cancelled = true),
        )
        CarScreen.entries.forEach { s ->
            ctxs.forEach { c ->
                VoiceScript.forScreen(s, c)?.let { t -> forbidden.forEach { w -> assertTrue(w !in t, "$s: '$w' in $t") } }
            }
        }
        assertNotNull(VoiceScript.forScreen(CarScreen.GUIDE, VoiceContext()))
    }
}
