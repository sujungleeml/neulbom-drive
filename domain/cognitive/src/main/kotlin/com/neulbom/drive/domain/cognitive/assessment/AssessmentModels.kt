package com.neulbom.drive.domain.cognitive.assessment

import com.neulbom.drive.domain.cognitive.DenialReason
import com.neulbom.drive.domain.cognitive.TaskEngine
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.TaskProtocol
import com.neulbom.drive.domain.cognitive.TrialResult
import java.time.Instant

enum class AssessmentStatus { IN_PROGRESS, COMPLETED, INTERRUPTED, CANCELLED }

/** 완료 화면에 보여 줄 저장 상태(명세 v0.6 02 §6). 점수는 없다. */
enum class SaveState { SERVER_SAVED, LOCAL_PENDING_SYNC, SAVE_FAILED }

data class AssessmentSession(
    val assessmentId: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val status: AssessmentStatus,
    val stopReason: DenialReason?,
    val isFirstAssessment: Boolean,
    val protocolVersions: Map<TaskId, Int>,
    val appVersion: String,
    val deviceProfile: String,
)

/** 시행 원자료를 즉시 영구 저장하는 곳(차량 앱: Room). */
interface TrialLogSink {
    suspend fun open(session: AssessmentSession)
    suspend fun append(assessmentId: String, taskId: TaskId, result: TrialResult)
    suspend fun close(session: AssessmentSession): SaveState
}

sealed interface RunnerState {
    data object Idle : RunnerState
    data class Running(val assessmentId: String, val taskIndex: Int, val taskId: TaskId, val protocol: TaskProtocol, val engine: TaskEngine) : RunnerState

    /** 과제 사이 안내 화면. 이 상태에서도 안전 감시는 계속된다. */
    data class Between(val assessmentId: String, val nextTaskIndex: Int, val nextTaskId: TaskId) : RunnerState

    /** 마지막 기록을 저장하는 중. */
    data class Saving(val assessmentId: String) : RunnerState
    data class Finished(val session: AssessmentSession, val saveState: SaveState) : RunnerState
}
