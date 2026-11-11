package com.neulbom.drive.server.db

import com.neulbom.drive.server.checks.AssessmentSessionDto
import com.neulbom.drive.server.checks.AssessmentUploadDto
import com.neulbom.drive.server.checks.CheckStore
import com.neulbom.drive.server.checks.TrialDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/** assessment_sessions + assessment_trials. 세션과 시행은 한 트랜잭션으로 저장한다(일부만 남지 않게). */
class SqliteCheckStore(private val db: SqliteDb, private val userId: String = SqliteDrivingStore.DEFAULT_USER) : CheckStore {

    override fun putIfAbsent(upload: AssessmentUploadDto): Boolean = db.tx {
        val s = upload.session
        if (query("SELECT 1 FROM assessment_sessions WHERE assessment_id = ?", s.assessmentId) { true }.isNotEmpty()) return@tx false
        update(
            "INSERT INTO assessment_sessions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            userId, s.assessmentId, s.startedAt, s.endedAt, s.status, s.stopReason, if (s.isFirstAssessment) 1 else 0,
            Json.encodeToString(s.protocolVersions), s.appVersion, s.deviceProfile, s.vehicleSource,
        )
        upload.trials.forEachIndexed { seq, t ->
            update(
                "INSERT INTO assessment_trials VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                s.assessmentId, seq, t.taskId, t.taskVersion, t.stimulusSetVersion, t.trialIndex, t.block, t.trialType,
                t.scheduledOnsetMs, t.actualOnsetMs, t.responseMs, t.reactionTimeMs, t.responseCode, t.outcome,
                if (t.isPractice) 1 else 0, t.displayLatencyMs,
            )
        }
        true
    }

    override fun get(assessmentId: String) = sessions("WHERE user_id = ? AND assessment_id = ?", userId, assessmentId).firstOrNull()

    override fun all() = sessions("WHERE user_id = ? ORDER BY started_at", userId)

    override fun deleteAll() {
        db.tx { update("DELETE FROM assessment_sessions WHERE user_id = ?", userId) }
    }

    override fun deleteStartedBefore(cutoff: Instant): Int =
        db.tx { update("DELETE FROM assessment_sessions WHERE user_id = ? AND started_at < ?", userId, cutoff.toString()) }

    private fun sessions(where: String, vararg args: Any?): List<AssessmentUploadDto> = db.query("SELECT * FROM assessment_sessions $where", *args) { rs ->
        AssessmentSessionDto(
            assessmentId = rs.getString("assessment_id"),
            startedAt = rs.getString("started_at"),
            endedAt = rs.getString("ended_at"),
            status = rs.getString("status"),
            stopReason = rs.getString("stop_reason"),
            isFirstAssessment = rs.getInt("is_first_assessment") == 1,
            protocolVersions = Json.decodeFromString<Map<String, Int>>(rs.getString("protocol_versions")),
            appVersion = rs.getString("app_version"),
            deviceProfile = rs.getString("device_profile"),
            vehicleSource = rs.getString("vehicle_source"),
        )
    }.map { AssessmentUploadDto(it, trials(it.assessmentId)) }

    private fun trials(assessmentId: String): List<TrialDto> = db.query("SELECT * FROM assessment_trials WHERE assessment_id = ? ORDER BY seq", assessmentId) { rs ->
        TrialDto(
            taskId = rs.getString("task_id"),
            taskVersion = rs.getInt("task_version"),
            stimulusSetVersion = rs.getInt("stimulus_set_version"),
            trialIndex = rs.getInt("trial_index"),
            block = rs.getString("block"),
            trialType = rs.getString("trial_type"),
            scheduledOnsetMs = rs.getLong("scheduled_onset_ms"),
            actualOnsetMs = rs.nullableLong("actual_onset_ms"),
            responseMs = rs.nullableLong("response_ms"),
            reactionTimeMs = rs.nullableLong("reaction_time_ms"),
            responseCode = rs.getString("response_code"),
            outcome = rs.getString("outcome"),
            isPractice = rs.getInt("is_practice") == 1,
            displayLatencyMs = rs.getLong("display_latency_ms"),
        )
    }
}
