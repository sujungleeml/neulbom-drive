package com.neulbom.drive.domain.cognitive

/** 두 개의 큰 응답 버튼(왼쪽/오른쪽). 차량 화면에서 오조작을 줄이기 위해 응답은 항상 2지선다. */
enum class ResponseKey { LEFT, RIGHT, TAP }

enum class SwitchRule(val label: String, val leftLabel: String, val rightLabel: String) {
    PARITY("홀짝", "홀수", "짝수"),
    MAGNITUDE("크기", "5보다 작다", "5보다 크다"),
}

sealed interface Stimulus {
    /** 선택 반응 과제: 화살표 방향과 같은 쪽 버튼을 누른다. */
    data class Arrow(val side: ResponseKey) : Stimulus

    /** 규칙 전환 과제: 숫자(1~9, 5 제외)를 현재 규칙으로 판단한다. */
    data class Digit(val value: Int, val rule: SwitchRule) : Stimulus

    /** Go/No-Go: Go 자극이면 누르고, No-Go 자극이면 누르지 않는다. */
    data class GoNoGo(val isGo: Boolean) : Stimulus
}

enum class Block { PRACTICE, MAIN, PURE_PARITY, PURE_MAGNITUDE, MIXED }

data class TrialSpec(
    val index: Int,
    val block: Block,
    val stimulus: Stimulus,
    /** 정답 응답. null이면 "누르지 않기"가 정답(No-Go). */
    val expected: ResponseKey?,
    /** 이전 시행 종료 후 자극 제시까지 대기 시간. */
    val isiMs: Long,
    /** 규칙 전환 과제 혼합 블록에서 직전 시행과 규칙이 다른지(null: 해당 없음). */
    val isSwitch: Boolean? = null,
) {
    val isPractice: Boolean get() = block == Block.PRACTICE
}

enum class TrialOutcome {
    CORRECT,
    WRONG,
    OMISSION,
    ANTICIPATION,

    /** No-Go에서 누르지 않음(정답). */
    CORRECT_REJECTION,

    /** No-Go에서 누름(오반응). */
    COMMISSION,
}

data class TrialResult(
    val spec: TrialSpec,
    val response: ResponseKey?,
    /** 실제 표시 시각 기준 반응시간(ms). */
    val rtMs: Long?,
    val outcome: TrialOutcome,
    /** 자극 제시 요청 → 실제 화면 표시까지 지연(장치 지연 기록). */
    val displayLatencyMs: Long,
    /** 단조 시각(ms): 표시를 요청한 시각, 실제 표시 시각, 응답 시각. */
    val scheduledOnsetMs: Long,
    val actualOnsetMs: Long?,
    val responseMs: Long?,
)
