package com.neulbom.drive.domain.cognitive

/**
 * 시행 기반 과제 상태 기계. UI 프레임워크와 무관한 순수 로직이며 시간은 호출자가 단조 시각(ms)으로 넘긴다.
 *
 * 흐름: [current] 확인 → isi 대기 → [stimulusShown] → [respond] 또는 [windowElapsed] → 다음 시행.
 * [abort] 이후에는 모든 입력을 무시한다(입력 차단, T02).
 */
class TaskEngine(
    val plan: List<TrialSpec>,
    val responseWindowMs: Long,
    val anticipationMs: Long = 150,
) {
    private val lock = Any()
    private val results = mutableListOf<TrialResult>()
    private var cursor = 0
    private var onsetMs: Long? = null
    private var latencyMs: Long = 0
    private var scheduledMs: Long = 0
    private var aborted = false

    val current: TrialSpec?
        get() = synchronized(lock) { if (aborted) null else plan.getOrNull(cursor) }

    val isComplete: Boolean get() = synchronized(lock) { !aborted && cursor >= plan.size }
    val isAborted: Boolean get() = synchronized(lock) { aborted }
    val isAwaitingResponse: Boolean get() = synchronized(lock) { !aborted && onsetMs != null }
    val progress: Pair<Int, Int> get() = synchronized(lock) { cursor to plan.size }

    fun results(): List<TrialResult> = synchronized(lock) { results.toList() }

    /** @param requestedAtMs 화면 표시를 요청한 시각, [shownAtMs] 실제 프레임에 그려진 시각. */
    fun stimulusShown(shownAtMs: Long, requestedAtMs: Long = shownAtMs) = synchronized(lock) {
        if (aborted || cursor >= plan.size || onsetMs != null) return@synchronized
        onsetMs = shownAtMs
        scheduledMs = requestedAtMs
        latencyMs = (shownAtMs - requestedAtMs).coerceAtLeast(0)
    }

    /** @return 기록된 결과. 입력이 차단·무시되면 null. */
    fun respond(key: ResponseKey, atMs: Long): TrialResult? = synchronized(lock) {
        val onset = onsetMs ?: return@synchronized null
        if (aborted) return@synchronized null
        val spec = plan[cursor]
        val rt = atMs - onset
        val outcome = when {
            rt > responseWindowMs -> if (spec.expected == null) TrialOutcome.CORRECT_REJECTION else TrialOutcome.OMISSION
            rt < anticipationMs -> TrialOutcome.ANTICIPATION
            spec.expected == null -> TrialOutcome.COMMISSION
            key == spec.expected -> TrialOutcome.CORRECT
            else -> TrialOutcome.WRONG
        }
        val responded = outcome != TrialOutcome.OMISSION && outcome != TrialOutcome.CORRECT_REJECTION
        record(spec, key.takeIf { responded }, rt.takeIf { responded }, outcome, atMs.takeIf { responded })
    }

    /** 응답 제한 시간 경과: Go/일반 시행은 누락, No-Go는 올바른 억제. */
    fun windowElapsed(atMs: Long): TrialResult? = synchronized(lock) {
        val onset = onsetMs ?: return@synchronized null
        if (aborted || atMs - onset < responseWindowMs) return@synchronized null
        val spec = plan[cursor]
        record(spec, null, null, if (spec.expected == null) TrialOutcome.CORRECT_REJECTION else TrialOutcome.OMISSION, null)
    }

    fun abort() = synchronized(lock) {
        aborted = true
        onsetMs = null
    }

    private fun record(spec: TrialSpec, key: ResponseKey?, rt: Long?, outcome: TrialOutcome, responseAt: Long?): TrialResult {
        val result = TrialResult(spec, key, rt, outcome, latencyMs, scheduledMs, onsetMs, responseAt)
        results += result
        cursor++
        onsetMs = null
        return result
    }
}
