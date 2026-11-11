package com.neulbom.drive.presentation

import com.neulbom.drive.domain.cognitive.DenialReason
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.assessment.SaveState

/** 화면을 읽을 때 필요한 추가 정보. */
data class VoiceContext(
    val saveState: SaveState? = null,
    val nextTask: TaskId? = null,
    val cancelled: Boolean = false,
    val stopReason: DenialReason? = null,
)

/**
 * 음성 안내 대본(선택, 기본 꺼짐). 화면에 있는 문장을 그대로 읽는다 — 새 정보를 말로만 주지 않는다.
 * 주행 중과 과제 진행 중(자극 제시)에는 아무것도 읽지 않는다: 운전 주의를 끌지 않고, 소리가 반응 측정에 끼어들지 않게.
 */
object VoiceScript {
    fun forScreen(screen: CarScreen, ctx: VoiceContext): String? = when (screen) {
        CarScreen.DRIVING, CarScreen.TASK, CarScreen.SAVING, CarScreen.IDLE, CarScreen.ONBOARDING -> null
        CarScreen.PROFILE -> "${UiText.PROFILE_TITLE} ${UiText.PROFILE_MOCK_NAME}."
        CarScreen.OFFER -> "${UiText.OFFER_TITLE} ${UiText.OFFER_BODY} ${UiText.OFFER_START}, ${UiText.OFFER_LATER}, ${UiText.OFFER_NOT_TODAY} 중에서 골라 주세요."
        CarScreen.GUIDE -> "${UiText.GUIDE_TITLE}. " + UiText.GUIDE_POINTS.joinToString(" ") + " 준비되면 ${UiText.GUIDE_START}를 눌러 주세요."
        CarScreen.BETWEEN -> ctx.nextTask?.let { "잘하셨어요. 다음은 ${UiText.taskLabel(it)}이에요. 준비되면 다음 활동 시작을 눌러 주세요." }
        CarScreen.CHECK_IN -> "${UiText.CHECK_IN_TITLE}."
        CarScreen.COMPLETION -> ctx.saveState?.let { "${UiText.COMPLETION_TITLE}. ${UiText.completion(it)}" }
        CarScreen.INTERRUPTED -> if (ctx.cancelled) {
            "${UiText.CANCELLED_TITLE}. ${UiText.INTERRUPTED_BODY}"
        } else {
            listOfNotNull(UiText.INTERRUPTED_TITLE + ".", ctx.stopReason?.message, UiText.INTERRUPTED_BODY).joinToString(" ")
        }
    }
}
