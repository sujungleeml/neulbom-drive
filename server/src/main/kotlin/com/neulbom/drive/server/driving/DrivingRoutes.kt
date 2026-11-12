package com.neulbom.drive.server.driving

import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.domain.driving.DrivingCopy
import com.neulbom.drive.domain.driving.DrivingReportBuilder
import com.neulbom.drive.domain.driving.PeriodSummarizer
import com.neulbom.drive.domain.driving.SampleCodec
import com.neulbom.drive.domain.driving.SessionFeatureCalculator
import com.neulbom.drive.domain.driving.SessionFeatures
import com.neulbom.drive.domain.driving.SessionQuality
import com.neulbom.drive.server.REPORT_ZONE
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.YearMonth

/** 주행 습관 축(A) API. 점검 축과 값을 섞지 않는다. */
fun Route.drivingRoutes(store: DrivingStore, clock: Clock) {
    post("/v1/driving-sessions") {
        val dto = call.receive<SessionUploadDto>()
        val key = call.request.headers["Idempotency-Key"]
        require(key == dto.session.driveId) { "Idempotency-Key must equal driveId" }
        val session = dto.session.toDomain()
        require(!session.endedAt.isBefore(session.startedAt)) { "endedAt must not be before startedAt" }
        // 같은 driveId로 다시 보내면 기존 결과를 돌려준다. 내용이 다르면 덮어쓰지 않고 충돌로 알린다.
        store.get(key)?.let { (s, f) ->
            return@post if (s == session) call.respond(HttpStatusCode.OK, f.toResult()) else call.respond(HttpStatusCode.Conflict, mapOf("error" to "driveId already stored with different content"))
        }
        val features = SessionFeatureCalculator.compute(session, SampleCodec.decode(dto.samplesEncoded))
        store.upsert(session, features)
        call.respond(HttpStatusCode.Created, features.toResult())
    }
    get("/v1/reports/me/driving") {
        val period = call.request.queryParameters["period"]?.let { YearMonth.parse(it) } ?: YearMonth.from(clock.now().atZone(REPORT_ZONE))
        call.respond(drivingReport(store, period).copy(quality = drivingQuality(store, period)))
    }
    post("/v1/signal-support") {
        store.putSignalSupport(call.receive())
        call.respond(HttpStatusCode.NoContent)
    }
    get("/v1/signal-support") { call.respond(store.signalSupport()) }
}

/** 한 달 주행 리포트(웹·공유 화면 공용). */
fun drivingReport(store: DrivingStore, period: YearMonth): DrivingReportDto {
    val all = store.all()
    val report = DrivingReportBuilder.build(
        PeriodSummarizer.summarize(period, all),
        PeriodSummarizer.summarize(period.minusMonths(1), all),
    )
    return DrivingReportDto(
        period = report.period.toString(),
        state = report.state.name,
        origin = report.origin.name,
        algorithmVersion = report.algorithmVersion,
        lines = report.lines.map {
            MetricLineDto(it.key, it.value, it.unit, it.direction?.name, it.extra, DrivingCopy.render(it.templateId, it), it.previous, DrivingCopy.change(it))
        },
        notices = report.notices.map { DrivingCopy.render(it) },
    )
}

/** 그 달(세션 시간대 기준) 기록 상태. 리포트에 넣는 규칙은 PeriodSummarizer와 같다. */
fun drivingQuality(store: DrivingStore, period: YearMonth): DrivingQualityDto {
    val inMonth = store.all().filter { (s, _) -> YearMonth.from(s.startedAt.atZone(s.zone)) == period }
    val included = inMonth.filter { (_, f) -> PeriodSummarizer.isIncluded(f) }
    return DrivingQualityDto(
        drives = inMonth.size,
        included = included.size,
        limited = included.count { it.second.quality == SessionQuality.LIMITED },
        excluded = inMonth.size - included.size,
    )
}

private fun SessionFeatures.toResult() = UploadResultDto(driveId, quality.name, valid, algorithmVersion)
