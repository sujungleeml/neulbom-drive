package com.neulbom.drive.server.checks

import com.neulbom.drive.domain.cognitive.ProtocolRegistry
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.report.ChecksReportBuilder
import com.neulbom.drive.server.overview.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Instant

/** 주의·반응 점검 축(B) API. 주행 축과 값을 섞지 않는다. */
fun Route.checksRoutes(store: CheckStore) {
    post("/v1/assessments") {
        val dto = call.receive<AssessmentUploadDto>()
        require(call.request.headers["Idempotency-Key"] == dto.session.assessmentId) { "Idempotency-Key must equal assessmentId" }
        dto.toRecord() // 형식 검증(알 수 없는 과제·결과 값이면 400)
        validateUpload(dto)
        val created = store.putIfAbsent(dto)
        val stored = store.get(dto.session.assessmentId)!!
        // 같은 assessmentId로 다른 내용이 오면 덮어쓰지 않고 충돌로 알린다.
        if (!created && stored != dto) return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "assessmentId already stored with different content"))
        call.respond(if (created) HttpStatusCode.Created else HttpStatusCode.OK, AssessmentUploadResultDto(stored.session.assessmentId, stored.trials.size))
    }
    get("/v1/reports/me/checks") { call.respond(checksReport(store, withPoints = true)) }
}

/**
 * 무결성 검사(400): 시각 순서, 아는 프로토콜 버전, 시행 버전이 세션의 과제 버전과 같은지.
 * 버전이 맞지 않는 기록은 나중에 다른 규칙으로 계산되므로 받지 않는다.
 */
internal fun validateUpload(dto: AssessmentUploadDto) {
    val s = dto.session
    val started = Instant.parse(s.startedAt)
    s.endedAt?.let { require(!Instant.parse(it).isBefore(started)) { "endedAt must not be before startedAt" } }
    s.protocolVersions.forEach { (task, version) ->
        requireNotNull(ProtocolRegistry.find(TaskId.valueOf(task), version)) { "unknown protocol version $task v$version" }
    }
    dto.trials.forEach { t ->
        require(s.protocolVersions[t.taskId] == t.taskVersion) { "trial version ${t.taskId} v${t.taskVersion} does not match session" }
    }
}

/** 점검 기록 리포트(웹·공유 화면 공용). 점검별 기록(points)은 본인 화면에서만 넣는다. */
fun checksReport(store: CheckStore, withPoints: Boolean = false): ChecksReportDto {
    val records = store.all().map { it.toRecord() }
    val dto = ChecksReportDto.from(ChecksReportBuilder.build(records))
    if (!withPoints) return dto
    return dto.copy(
        tasks = dto.tasks.map { t ->
            t.copy(points = ChecksReportBuilder.series(TaskId.valueOf(t.taskId), records).map { it.toDto() })
        },
    )
}
