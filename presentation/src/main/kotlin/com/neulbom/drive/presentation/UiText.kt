package com.neulbom.drive.presentation

import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.assessment.SaveState
import com.neulbom.drive.domain.consent.ConsentType

/**
 * 사용자에게 보이는 문구 모음. 원칙(PRD 1·5·11절):
 *  - 진단·위험도·운전 적합성·면허 관련 표현을 쓰지 않는다.
 *  - 변화 이유를 과장하지 않고, 여러 일상적 이유가 있을 수 있음을 함께 알린다.
 *  - 짧고 큰 글씨에 맞는 문장.
 * [DISCLAIMER]만 "진단이 아님"을 밝히기 위해 예외적으로 관련 단어를 포함한다(금칙어 테스트 제외 대상).
 */
object UiText {
    const val APP_NAME = "늘봄 Drive"

    /** 미션(명세 v0.6 D16). */
    const val MISSION = "나이가 들어도, 운전의 즐거움은 계속되도록."

    const val DISCLAIMER =
        "이 앱은 의료 진단 도구가 아닙니다. 결과는 본인의 평소 기록과 비교한 참고 정보이며, " +
            "건강이 걱정되시면 가까운 보건소나 전문가와 상담해 주세요."

    const val SYNTHETIC_BADGE = "합성 데이터"
    const val MOCK_VEHICLE_BADGE = "모의 차량"

    fun safety(state: SafetyState): String = when (state) {
        SafetyState.PARKED_SAFE -> "안전하게 주차됨"
        SafetyState.DRIVING -> "주행 중"
        SafetyState.PARKED_UNVERIFIED -> "주차 확인 중"
        SafetyState.POWERING_OFF -> "전원 끄는 중"
    }




    const val DRIVING_LOCK_TITLE = "안전 운전 중"
    const val DRIVING_LOCK_BODY = "운전에만 집중하세요.\n주차하면 다시 보여 드릴게요."

    // CAR-02 운전자 확인
    const val PROFILE_TITLE = "지금 운전하시는 분이 맞나요?"
    const val PROFILE_MOCK_NAME = "운전자 1"
    const val PROFILE_YES = "네, 맞아요"
    const val PROFILE_NO = "아니에요"
    const val PROFILE_NOT_ME_NOTE = "다른 분이 운전하시는 동안에는 점검을 제안하지 않아요."

    // 대기 화면
    const val IDLE_TITLE_PARKED = "좋아하는 길을, 더 오래 달릴 수 있도록"
    const val IDLE_TITLE_WAIT = "주차가 확인되면 점검할 수 있어요"
    const val IDLE_INTERVAL_NOTE = "부담 없이 시작하고, 나만의 기록을 천천히 쌓아가세요."
    const val IDLE_RESULTS_TITLE = "휴대폰이나 컴퓨터에서 나의 기록을 확인할 수 있어요"
        const val IDLE_RESULTS_BUTTON = "기록 보기"
    const val IDLE_DRIVING_OFF = "주행 습관 기록이 꺼져 있어요."
    const val IDLE_DRIVING_TURN_ON = "켜러 가기"
    const val IDLE_DRIVING_CLOSE = "닫기"
    /** 제안 정책 고지(IDLE에서 OFFER로 옮김). */
    const val OFFER_INTERVAL_NOTE = "늘봄 점검은 정해진 간격으로만 제안해요."

    // 음성 안내(선택)
    const val VOICE_TITLE = "음성 안내"
    const val VOICE_DESCRIPTION = "주차 중에만 화면 문장을 읽어 드려요."

    // CAR-03 제안
    const val OFFER_TITLE = "약 3분 점검을 해 보실래요?"
    const val OFFER_BODY = "짧은 활동 3가지를 해요. 원하면 중간에 멈출 수 있어요."
    const val OFFER_FIRST_NOTE = "처음이라 연습부터 천천히 시작해요."
    const val OFFER_START = "지금 시작"
    const val OFFER_LATER = "나중에"
    const val OFFER_NOT_TODAY = "오늘은 건너뛰기"

    // CAR-04 안내
    const val GUIDE_TITLE = "시작하기 전에"
    val GUIDE_POINTS = listOf(
        "약 3분 정도 걸려요.",
        "화면을 보고 큰 버튼을 누르면 돼요.",
        "차가 움직이거나 시동을 끄면 바로 멈춰요.",
    )
    const val GUIDE_PRACTICE_NOTE = "각 활동은 시작 전에 연습해 볼 수 있어요."
    const val GUIDE_START = "시작하기"
    const val BACK = "처음으로"
    const val CLOSE = "닫기"
    const val QUIT = "그만하기"

    // CAR-08 4주 문항(자리표시자)
    const val CHECK_IN_TITLE = "몇 가지만 여쭤볼게요"
    const val CHECK_IN_PLACEHOLDER = "문항 준비 중 · 임시"
    const val CHECK_IN_NEXT = "다음"

    // CAR-09 완료
    const val COMPLETION_TITLE = "오늘 점검을 마쳤어요"

    // CAR-10 중단
    const val INTERRUPTED_TITLE = "안전을 위해 점검을 멈췄어요"
    const val CANCELLED_TITLE = "점검을 멈췄어요"
    // 멈춘 점검도 기기에는 INTERRUPTED/CANCELLED로 남으므로 "기록하지 않음"이라고 하지 않는다.
    const val INTERRUPTED_BODY = "이번 점검은 나의 변화를 살펴보는 데 쓰지 않아요.\n다음에 편할 때 다시 해 보세요."

    const val CHECK_NOW = "지금 점검하기"
    const val CHECK_UNAVAILABLE_PREFIX = "지금은 점검할 수 없어요: "

    fun consentTitle(type: ConsentType): String = when (type) {
        ConsentType.DRIVING_RECORD -> "주행 습관 기록"
        ConsentType.COGNITIVE_CHECK -> "주차 중 주의·반응 점검"
    }

    /** 수집 항목·목적·공유 범위(명세 v0.5 CAR-01). */
    fun consentDetail(type: ConsentType, retentionDays: Int): String = when (type) {
        ConsentType.DRIVING_RECORD ->
            "수집: 차량이 허용하는 범위의 주행 시간, 속도, 감속 정보(차량마다 다름)\n" +
                "수집하지 않음: 위치 좌표, 주행 경로, 목적지 기록\n" +
                "목적: 내 운전 습관을 사실대로 살펴보기\n" +
                "보관: ${retentionDays}일"
        ConsentType.COGNITIVE_CHECK ->
            "수집: 주차 중 직접 시작한 점검의 시행별 반응 기록\n" +
                "목적: 나의 이전 기록과 비교해 보기\n" +
                "보관: ${retentionDays}일"
    }

    /** 거부해도 가능한 기능 안내. */
    fun withoutConsent(type: ConsentType): String = when (type) {
        ConsentType.DRIVING_RECORD -> "동의하지 않아도 점검은 할 수 있어요. 주행 기록만 남지 않아요."
        ConsentType.COGNITIVE_CHECK -> "동의하지 않으면 점검 제안과 점검이 나타나지 않아요."
    }

    fun taskLabel(task: TaskId): String = when (task) {
        TaskId.VISUAL_ATTENTION -> "화살표 보고 누르기"
        TaskId.GO_NO_GO -> "누를 때와 멈출 때"
        TaskId.TASK_SWITCHING -> "규칙 바꾸기"
    }

    /** 완료 화면 문구(명세 v0.6 02 §6). 점수·추세를 넣지 않는다. */
    fun completion(state: SaveState): String = when (state) {
        SaveState.SERVER_SAVED -> "오늘 점검이 저장되었어요."
        SaveState.LOCAL_PENDING_SYNC -> "오늘 점검을 이 차에 보관했어요. 연결되면 보내 드려요."
        SaveState.SAVE_FAILED -> "기록을 저장하지 못했어요. 다시 확인해 주세요."
    }
}
