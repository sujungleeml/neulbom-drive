package com.neulbom.drive.server.settings

import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.server.checks.AssessmentUploadDto
import com.neulbom.drive.server.checks.CheckStore
import com.neulbom.drive.server.driving.DrivingStore
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class ExportedDriveDto(
    val driveId: String,
    val startedAt: String,
    val endedAt: String,
    val origin: String,
    val durationS: Double,
    val distanceKm: Double?,
    val nightShare: Double,
    val harshDecelCount: Int?,
)

/** 내 기록 내려받기. 두 축은 서로 다른 목록으로만 담는다(합친 값 없음). */
@Serializable
data class ExportDto(
    val exportedAt: String,
    val drivingSessions: List<ExportedDriveDto>,
    val assessments: List<AssessmentUploadDto>,
    val consentEvents: List<ConsentEventDto>,
)

/**
 * 내보내기·삭제(명세 v0.6 04 §6). 삭제는 주행·점검 기록과 공유 링크를 지우고 동의 감사 이력은 남긴다.
 * @param onDelete 다른 저장소(공유 링크 등)도 함께 지우기 위한 연결점
 */
fun Route.dataRoutes(driving: DrivingStore, checks: CheckStore, settings: SettingsStore, clock: Clock, onDelete: () -> Unit = {}) {
    get("/v1/me/export") {
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"neulbom-my-data.json\"")
        call.respond(
            ExportDto(
                exportedAt = clock.now().toString(),
                drivingSessions = driving.all().map { (s, f) ->
                    ExportedDriveDto(s.driveId, s.startedAt.toString(), s.endedAt.toString(), s.origin.name, f.durationS, f.distanceKm, f.nightShare, f.harshDecelCount)
                },
                assessments = checks.all(),
                consentEvents = settings.consentEvents(),
            ),
        )
    }
    delete("/v1/me/data") {
        driving.deleteAll()
        checks.deleteAll()
        onDelete()
        call.respond(HttpStatusCode.NoContent)
    }
}
