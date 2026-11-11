package com.neulbom.drive.vehicle.api

/**
 * 운전자 식별. 식별 실패 시 개인 기준선 업데이트를 하지 않는다(PRD 4절).
 * pseudonymousId는 기기 내 무작위 ID이며 실명·차대번호·계정 ID가 아니다.
 */
sealed interface DriverIdentity {
    data class Verified(val pseudonymousId: String) : DriverIdentity
    data object Unverified : DriverIdentity
}

fun interface DriverIdentityAdapter {
    fun currentDriver(): DriverIdentity
}

/**
 * 내비게이션 연계(선택). 외부 내비 앱의 터치 이벤트를 읽을 수 있다고 가정하지 않는다(PRD 6절).
 * 앱 자체 목적지 UI에서 고른 항목을 넘겨주는 단방향 연계만 상정한다.
 */
interface NaviAdapter {
    val isAvailable: Boolean

    /** @param favoriteSlot 앱 내 즐겨찾기 슬롯 번호(목적지 원문은 이 앱이 보관하지 않음). */
    fun handOffFavorite(favoriteSlot: Int): HandOffResult
}

enum class HandOffResult { SENT, NOT_AVAILABLE }

/** 음성(STT/TTS) 연계(선택). 음성 원본은 저장하지 않는다. */
interface SpeechAdapter {
    val sttAvailable: Boolean
    val ttsAvailable: Boolean
}

/** 권한·API가 확인되기 전 기본 구현: 항상 사용 불가. */
object UnavailableNaviAdapter : NaviAdapter {
    override val isAvailable = false
    override fun handOffFavorite(favoriteSlot: Int) = HandOffResult.NOT_AVAILABLE
}

object UnavailableSpeechAdapter : SpeechAdapter {
    override val sttAvailable = false
    override val ttsAvailable = false
}
