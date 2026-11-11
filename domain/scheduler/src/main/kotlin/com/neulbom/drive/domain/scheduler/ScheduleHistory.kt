package com.neulbom.drive.domain.scheduler

import com.neulbom.drive.core.model.SafetyState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 점검 제안에 대한 사용자 응답. */
enum class OfferResponse { ACCEPTED, LATER, NOT_TODAY }

/** 제안 한 건. response가 null이면 아직 답하지 않았다(화면을 벗어났거나 주행을 시작함). */
data class OfferRecord(val offeredAt: Instant, val response: OfferResponse?)

/**
 * 저장된 기록(완료한 점검 시각, 제안 기록)으로 [ScheduleInput]을 만든다.
 * 점검 **결과**는 입력으로 받지 않는다 — 시각만 쓴다.
 */
object ScheduleHistory {
    fun input(
        now: Instant,
        safety: SafetyState,
        consentGranted: Boolean,
        onboardingDone: Boolean,
        completedAt: List<Instant>,
        offers: List<OfferRecord>,
        zone: ZoneId,
        /** 웹 설정의 "점검 제안 일시 중지" 끝 시각. */
        pausedUntil: Instant? = null,
        config: SchedulerConfig = SchedulerConfig(),
    ): ScheduleInput {
        val lastValid = completedAt.maxOrNull()
        val lastOffer = offers.maxByOrNull { it.offeredAt }
        val notToday = lastOffer
            ?.takeIf { it.response == OfferResponse.NOT_TODAY }
            ?.let { it.offeredAt.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant() }
        val suspendedUntil = listOfNotNull(notToday, pausedUntil).maxOrNull()
        return ScheduleInput(
            now = now,
            safety = safety,
            consentGranted = consentGranted,
            onboardingDone = onboardingDone,
            suspendedUntil = suspendedUntil,
            validAssessmentCount = completedAt.size,
            lastValidAssessmentAt = lastValid,
            offersInCurrentCycle = offersInCurrentCycle(now, lastValid, offers, config),
            lastOfferAt = lastOffer?.offeredAt,
            lastOfferDeclined = lastOffer != null && lastOffer.response != OfferResponse.ACCEPTED,
        )
    }

    /**
     * 현재 주기의 제안 수. 주기는 마지막 유효 점검 뒤에 시작한다.
     * 한 주기에 제안이 [SchedulerConfig.maxOffersPerCycle]회 찼고 그 마지막 제안에서 [SchedulerConfig.routineIntervalDays]일이
     * 지났으면, 그 제안 뒤를 새 주기로 본다(R-1: 유효 점검이 없으면 제안이 영구히 멈추던 문제). 점검 결과는 보지 않는다.
     */
    internal fun offersInCurrentCycle(now: Instant, lastValid: Instant?, offers: List<OfferRecord>, config: SchedulerConfig): Int {
        val sorted = offers.map { it.offeredAt }.sorted()
        var cycle = sorted.filter { lastValid == null || it.isAfter(lastValid) }
        while (cycle.size >= config.maxOffersPerCycle) {
            val limitAt = cycle[config.maxOffersPerCycle - 1]
            if (now.isBefore(limitAt.plus(Duration.ofDays(config.routineIntervalDays)))) break
            cycle = cycle.filter { it.isAfter(limitAt) }
        }
        return cycle.size
    }
}
