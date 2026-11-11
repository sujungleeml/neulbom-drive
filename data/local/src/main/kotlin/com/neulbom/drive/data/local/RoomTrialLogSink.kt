package com.neulbom.drive.data.local

import com.neulbom.drive.core.id.IdGenerator
import com.neulbom.drive.domain.cognitive.Block
import com.neulbom.drive.domain.cognitive.ProvisionalProtocols
import com.neulbom.drive.domain.cognitive.Stimulus
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.TrialResult
import com.neulbom.drive.domain.cognitive.assessment.AssessmentSession
import com.neulbom.drive.domain.cognitive.assessment.SaveState
import com.neulbom.drive.domain.cognitive.assessment.TrialLogSink

/**
 * 시행 원자료를 즉시 Room에 쓰는 기록기(ADR-05). 끝나면 `LOCAL_PENDING_SYNC` — 서버 ACK 뒤 RoomAssessmentQueue가 `SERVER_SAVED`로 바꾼다.
 * 저장에 실패하면 `SAVE_FAILED`를 돌려준다 — "보관했습니다"는 실제 저장이 끝난 뒤에만(명세 04 §5).
 */
class RoomTrialLogSink(private val db: NeulbomDatabase, private val ids: IdGenerator) : TrialLogSink {
    private val dao = db.assessmentDao()

    override suspend fun open(session: AssessmentSession) {
        dao.insertSession(
            AssessmentSessionEntity(
                assessmentId = session.assessmentId,
                startedAt = session.startedAt.toEpochMilli(),
                endedAt = null,
                status = session.status.name,
                stopReason = null,
                isFirstAssessment = session.isFirstAssessment,
                protocolVersions = session.protocolVersions.entries.joinToString(";") { "${it.key.name}:${it.value}" },
                appVersion = session.appVersion,
                deviceProfile = session.deviceProfile,
                syncState = "IN_PROGRESS",
            ),
        )
    }

    override suspend fun append(assessmentId: String, taskId: TaskId, result: TrialResult) {
        val protocol = ProvisionalProtocols.ALL.first { it.taskId == taskId }
        dao.insertTrial(
            AssessmentTrialEntity(
                trialId = ids.newId(),
                assessmentId = assessmentId,
                taskId = taskId.name,
                taskVersion = protocol.taskVersion,
                stimulusSetVersion = protocol.stimulusSetVersion,
                trialIndex = result.spec.index,
                block = result.spec.block.name,
                trialType = when (val s = result.spec.stimulus) {
                    is Stimulus.Arrow -> "ARROW_${s.side.name}"
                    is Stimulus.GoNoGo -> if (s.isGo) "GO" else "NOGO"
                    is Stimulus.Digit -> "DIGIT_${s.value}_${s.rule.name}" + (result.spec.isSwitch?.let { if (it) "_SWITCH" else "_REPEAT" } ?: "")
                },
                scheduledOnsetMs = result.scheduledOnsetMs,
                actualOnsetMs = result.actualOnsetMs,
                responseMs = result.responseMs,
                reactionTimeMs = result.rtMs,
                responseCode = result.response?.name,
                outcome = result.outcome.name,
                isPractice = result.spec.block == Block.PRACTICE,
                displayLatencyMs = result.displayLatencyMs,
            ),
        )
    }

    override suspend fun close(session: AssessmentSession): SaveState = try {
        val updated = dao.finish(session.assessmentId, session.status.name, session.endedAt?.toEpochMilli(), session.stopReason?.name, "LOCAL_PENDING_SYNC")
        if (updated == 1) SaveState.LOCAL_PENDING_SYNC else SaveState.SAVE_FAILED
    } catch (e: Exception) {
        SaveState.SAVE_FAILED
    }
}
