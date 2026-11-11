package com.neulbom.drive.data.local

import com.neulbom.drive.data.sync.AssessmentQueue
import com.neulbom.drive.data.sync.AssessmentUpload
import com.neulbom.drive.data.sync.AssessmentUploadSession
import com.neulbom.drive.data.sync.AssessmentUploadTrial
import java.time.Instant

/**
 * 끝난 점검을 서버로 보내기 위한 대기열. 원자료는 이미 Room에 있으므로 읽어서 넘기기만 한다.
 * @param vehicleSource 이 빌드의 차량 출처(mock 플래버는 "MOCK")
 */
class RoomAssessmentQueue(private val db: NeulbomDatabase, private val vehicleSource: String) : AssessmentQueue {
    private val dao = db.assessmentDao()

    override suspend fun pending(): List<AssessmentUpload> = dao.pendingSync().map { s ->
        AssessmentUpload(
            AssessmentUploadSession(
                assessmentId = s.assessmentId,
                startedAt = Instant.ofEpochMilli(s.startedAt),
                endedAt = s.endedAt?.let(Instant::ofEpochMilli),
                status = s.status,
                stopReason = s.stopReason,
                isFirstAssessment = s.isFirstAssessment,
                protocolVersions = s.protocolVersions.split(';').filter { it.isNotBlank() }.associate { p -> p.split(':').let { it[0] to it[1].toInt() } },
                appVersion = s.appVersion,
                deviceProfile = s.deviceProfile,
                vehicleSource = vehicleSource,
            ),
            dao.trials(s.assessmentId).map { t ->
                AssessmentUploadTrial(
                    t.taskId, t.taskVersion, t.stimulusSetVersion, t.trialIndex, t.block, t.trialType, t.scheduledOnsetMs,
                    t.actualOnsetMs, t.responseMs, t.reactionTimeMs, t.responseCode, t.outcome, t.isPractice, t.displayLatencyMs,
                )
            },
        )
    }

    override suspend fun markSynced(assessmentId: String) {
        dao.markServerSaved(assessmentId)
    }
}
