package com.neulbom.drive.server.overview

import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.report.ChecksReportBuilder
import com.neulbom.drive.domain.cognitive.report.TaskPoint
import com.neulbom.drive.server.checks.CheckStore
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class CheckWeekDto(val weekStart: String, val completed: Int, val stopped: Int, val mock: Boolean)

/** 과제 하나의 점검별 기록. 비교 키가 다른 점끼리는 화면에서 선을 잇지 않는다. */
@Serializable
data class TaskPointDto(
    val startedAt: String,
    val compareKey: String,
    val medianRtMs: Long?,
    val accuracy: Double?,
    val hiddenReason: String?,
)

@Serializable
data class TaskLaneDto(
    val taskId: String,
    val state: String,
    val comparableCount: Int,
    val excludedCount: Int,
    val limitReason: String?,
    val text: String,
    val points: List<TaskPointDto>,
)

/** 비교 키 = 과제 버전·자극 세트·기기. 화면은 이 문자열이 같은 점끼리만 잇는다. */
fun TaskPoint.toDto() = TaskPointDto(startedAt.toString(), "v${taskVersion}·s${stimulusSetVersion}·${deviceProfile}", medianRtMs, accuracy, hiddenReason)

/** 주의·반응 점검 레인. 이 레인은 점검 기록만 담는다. */
@Serializable
data class ChecksLaneDto(
    val weeks: List<CheckWeekDto>,
    val completed: Int,
    val stopped: Int,
    val tasks: List<TaskLaneDto>,
    val notes: List<String>,
)

object ChecksLaneCopy {
    const val CUT_LINE = "점검 방식이나 기기가 바뀐 곳에서는 점선으로 나누고 선을 잇지 않았어요."
}

/** @param weekStarts 오래된 주부터, 각 주의 월요일. 과제 문구·상태는 점검 리포트와 같다(전체 기록 기준). */
fun checksLane(store: CheckStore, weekStarts: List<LocalDate>, zone: ZoneId): ChecksLaneDto {
    val uploads = store.all()
    fun localDay(at: String) = Instant.parse(at).atZone(zone).toLocalDate()
    val from = weekStarts.first()
    val to = weekStarts.last().plusDays(7)
    fun inRange(at: String) = localDay(at).let { !it.isBefore(from) && it.isBefore(to) }
    val weeks = weekStarts.map { start ->
        val inWeek = uploads.filter { localDay(it.session.startedAt).let { d -> !d.isBefore(start) && d.isBefore(start.plusDays(7)) } }
        CheckWeekDto(
            weekStart = start.toString(),
            completed = inWeek.count { it.session.status == "COMPLETED" },
            stopped = inWeek.count { it.session.status == "INTERRUPTED" || it.session.status == "CANCELLED" },
            mock = inWeek.any { it.session.vehicleSource == "MOCK" },
        )
    }
    val records = uploads.map { it.toRecord() }
    val report = ChecksReportBuilder.build(records)
    val tasks = TaskId.entries.map { id ->
        val trend = report.tasks.first { it.taskId == id }
        TaskLaneDto(
            taskId = id.name,
            state = trend.state.name,
            comparableCount = trend.comparableCount,
            excludedCount = trend.excludedCount,
            limitReason = trend.limitReason?.name,
            text = trend.text,
            points = ChecksReportBuilder.series(id, records).filter { inRange(it.startedAt.toString()) }.map { it.toDto() },
        )
    }
    return ChecksLaneDto(weeks, weeks.sumOf { it.completed }, weeks.sumOf { it.stopped }, tasks, listOf(ChecksLaneCopy.CUT_LINE) + report.notices)
}
