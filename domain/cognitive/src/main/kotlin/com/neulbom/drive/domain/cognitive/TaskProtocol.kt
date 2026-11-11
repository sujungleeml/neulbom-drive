package com.neulbom.drive.domain.cognitive

/** 점검 과제(명세 v0.6 03 §1). 매회 이 순서로 세 과제를 모두 시행한다. */
enum class TaskId { VISUAL_ATTENTION, GO_NO_GO, TASK_SWITCHING }

enum class ProtocolStatus { PROVISIONAL, CONFIRMED }

/**
 * 반응 시각을 언제로 보느냐(측정 조건). 버전 1은 손을 뗄 때(RELEASE)였고,
 * 버전 2부터 손가락이 화면에 닿는 순간(TOUCH_DOWN) — 누르고 있던 시간이 반응시간에 섞이지 않는다.
 */
enum class ResponseCapture { RELEASE, TOUCH_DOWN }

/**
 * 과제 프로토콜(명세 03 §2 템플릿 중 엔진이 쓰는 값). 확정 전 값은 [ProtocolStatus.PROVISIONAL]로 표시하고
 * 시행 로그마다 [taskVersion]·[stimulusSetVersion]을 남긴다. 화면의 대비·크기·배치·타이밍도 측정 조건이므로 임의로 바꾸지 않는다.
 */
data class TaskProtocol(
    val taskId: TaskId,
    val taskVersion: Int,
    val stimulusSetId: String,
    val stimulusSetVersion: Int,
    val status: ProtocolStatus,
    val practiceTrials: Int,
    val trialCount: Int,
    /** 자극 표시 시간(ms). null이면 응답할 때까지 표시. */
    val displayDurationMs: Long?,
    val isiMinMs: Long,
    val isiMaxMs: Long,
    val responseWindowMs: Long,
    val anticipationMs: Long,
    /** Go/No-Go의 Go 비율. 다른 과제는 null. */
    val goRatio: Double?,
    val minimumValidTrials: Int,
    /** 반복 점검 목표 시간(명세 B절 55/55/60초 가설). */
    val targetDurationSec: Int,
    val responseCapture: ResponseCapture = ResponseCapture.TOUCH_DOWN,
)

/** **임시(PROVISIONAL)** 프로토콜 — 검사 명세서 확정 전 시연용. 임상 근거가 있는 값이 아니다. */
object ProvisionalProtocols {
    /**
     * 버전 3: 화살표를 글리프(← → 160sp)에서 굵은 벡터 512dp(약 3배)로 바꿈(자극 세트 arrows-lr v2).
     * 시각 디자인 규격으로 잠정 승인된 값이며 측정 동등성은 검증 전 — v2 기록과 같은 기준선으로 보지 않는다.
     */
    val VISUAL_ATTENTION = TaskProtocol(
        taskId = TaskId.VISUAL_ATTENTION, taskVersion = 3, stimulusSetId = "arrows-lr", stimulusSetVersion = 2,
        status = ProtocolStatus.PROVISIONAL, practiceTrials = 3, trialCount = 24, displayDurationMs = null,
        isiMinMs = 800, isiMaxMs = 1600, responseWindowMs = 2000, anticipationMs = 150, goRatio = null,
        minimumValidTrials = 16, targetDurationSec = 55,
    )
    /**
     * 버전 4(2026-11-05, PO 결정 A안): 고령 운전자에게 너무 급하다는 시연 피드백으로 속도를 살짝 늦춤 —
     * 표시 500→600ms, 자극 간격 600~1000→800~1200ms, 응답 제한 800→900ms. 자극·시행 수·Go 비율(75%)은 v3와 같다.
     * 타이밍이 바뀌었으므로 v3 기록과 같은 기준선으로 보지 않는다(비교 키가 갈림). 본 과제 약 52초 → 약 64초.
     *
     * 버전 3: 동그라미·가위표를 글리프(● ✕ 160sp, 같은 색)에서 512dp 캔버스의 벡터로 바꾸고, 가위표를 빨간색으로(자극 세트 circle-cross v2).
     * 시각적 접근성 보정으로 잠정 승인된 값이며 측정 동등성은 검증 전 — v2 기록과 같은 기준선으로 보지 않는다.
     * 표시 시간·응답 제한·시행 수·Go 비율은 v2와 같다.
     */
    val GO_NO_GO = TaskProtocol(
        taskId = TaskId.GO_NO_GO, taskVersion = 4, stimulusSetId = "circle-cross", stimulusSetVersion = 2,
        status = ProtocolStatus.PROVISIONAL, practiceTrials = 4, trialCount = 40, displayDurationMs = 600,
        isiMinMs = 800, isiMaxMs = 1200, responseWindowMs = 900, anticipationMs = 150, goRatio = 0.75,
        minimumValidTrials = 30, targetDurationSec = 65,
    )

    /**
     * Go/No-Go 버전 1~3의 타이밍(표시 500ms, 간격 600~1000ms, 응답 제한 800ms). 지난 기록을 그 규칙으로 다시 계산할 때 쓴다.
     * 버전 3은 자극 세트 circle-cross v2(512dp 벡터), 1·2는 v1 글리프.
     */
    val GO_NO_GO_V3 = GO_NO_GO.copy(
        taskVersion = 3, displayDurationMs = 500, isiMinMs = 600, isiMaxMs = 1000, responseWindowMs = 800, targetDurationSec = 55,
    )
    val TASK_SWITCHING = TaskProtocol(
        taskId = TaskId.TASK_SWITCHING, taskVersion = 2, stimulusSetId = "digits-parity-magnitude", stimulusSetVersion = 1,
        status = ProtocolStatus.PROVISIONAL, practiceTrials = 4, trialCount = 32, displayDurationMs = null,
        isiMinMs = 700, isiMaxMs = 1200, responseWindowMs = 2500, anticipationMs = 150, goRatio = null,
        minimumValidTrials = 20, targetDurationSec = 60,
    )
    val ALL = listOf(VISUAL_ATTENTION, GO_NO_GO, TASK_SWITCHING)
}

/**
 * 지금까지 쓰인 모든 프로토콜 버전. 기록은 **그 기록이 쓰인 버전의 규칙**으로 다시 계산한다.
 * 버전 1: 손을 뗄 때 기록(RELEASE). 버전 2: 닿는 순간(TOUCH_DOWN).
 * 버전 3: 시각 주의 512dp 벡터 화살표, Go/No-Go 512dp 벡터 동그라미·빨간 가위표.
 * 버전 4: Go/No-Go 타이밍 완화(표시 600ms, 간격 800~1200ms, 응답 제한 900ms).
 */
object ProtocolRegistry {
    val ALL: List<TaskProtocol> = buildList {
        // 지난 버전은 그 버전의 타이밍에서 만든다(Go/No-Go는 v4에서 타이밍이 바뀌어 v3 값을 기준으로).
        ProvisionalProtocols.ALL.forEach { current ->
            val base = if (current.taskId == TaskId.GO_NO_GO) ProvisionalProtocols.GO_NO_GO_V3 else current
            add(base.copy(taskVersion = 1, stimulusSetVersion = 1, responseCapture = ResponseCapture.RELEASE))
            add(base.copy(taskVersion = 2, stimulusSetVersion = 1))
        }
        add(ProvisionalProtocols.GO_NO_GO_V3)
        addAll(ProvisionalProtocols.ALL)
    }.distinctBy { it.taskId to it.taskVersion }

    fun find(taskId: TaskId, taskVersion: Int): TaskProtocol? = ALL.firstOrNull { it.taskId == taskId && it.taskVersion == taskVersion }
}
