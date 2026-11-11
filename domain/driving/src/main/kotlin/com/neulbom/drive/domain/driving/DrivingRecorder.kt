package com.neulbom.drive.domain.driving

import com.neulbom.drive.core.id.IdGenerator
import com.neulbom.drive.core.model.Attribution
import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.core.model.DrivingSession
import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.core.model.SignalId
import com.neulbom.drive.core.model.SignalSample
import com.neulbom.drive.core.model.SignalSupport
import com.neulbom.drive.core.model.SupportStatus
import com.neulbom.drive.core.time.Clock
import com.neulbom.drive.domain.consent.ConsentRepository
import com.neulbom.drive.domain.consent.ConsentType
import java.time.Instant
import java.time.ZoneId

/** 완료된 주행 세션을 영구 저장하는 곳(차량 앱: Room 대기열). */
fun interface DrivingSink {
    suspend fun saveSession(session: DrivingSession, samples: List<SignalSample>)
}

/**
 * 주행 세션 기록기(명세 v0.6 04 §2, 08 §2).
 *  - 주행 습관 기록 동의가 있을 때만 세션을 시작하고, 매 표본·종료 시점에 다시 확인한다(철회 시 세션 폐기).
 *  - SUPPORTED 신호만 기록한다(미검증·미지원 신호 표본은 버림). VEHICLE_STATE가 SUPPORTED가 아니면 세션을 열지 않는다.
 *  - PARKED_SAFE/POWERING_OFF에서 종료·저장. 종료 없이 다음 주행이 시작되면 마지막 표본 시각으로 닫고 비정상 종료로 표시한다.
 */
class DrivingRecorder(
    private val consents: ConsentRepository,
    private val support: () -> List<SignalSupport>,
    private val sink: DrivingSink,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val zone: ZoneId,
    private val attribution: () -> Attribution,
) {
    private class Active(
        val driveId: String,
        val startedAt: Instant,
        val startMono: Long,
        val origin: DataOrigin,
        val signals: Set<SignalId>,
        val rates: Map<SignalId, Double>,
        val samples: MutableList<SignalSample> = mutableListOf(),
        var lastMono: Long,
    )

    private var active: Active? = null

    private val consented get() = consents.state.value.isGranted(ConsentType.DRIVING_RECORD)

    suspend fun onSafetyChanged(state: SafetyState, origin: DataOrigin) {
        when (state) {
            SafetyState.DRIVING -> {
                active?.let { close(it, normally = false) }
                // 주행 상태 신호 자체가 검증되지 않았으면 세션 경계를 알 수 없으므로 기록하지 않는다.
                active = if (consented && SignalId.VEHICLE_STATE in supportedSignals()) start(origin) else null
            }
            SafetyState.PARKED_SAFE, SafetyState.POWERING_OFF -> active?.let { close(it, normally = true) }
            SafetyState.PARKED_UNVERIFIED -> Unit // 정차·신호 불명은 세션을 이어 간다
        }
    }

    fun onSample(signal: SignalId, value: Double) {
        val a = active ?: return
        if (!consented) {
            active = null // 철회: 진행 중 세션을 버린다
            return
        }
        if (signal !in a.signals) return
        val now = clock.monotonicMs()
        a.samples += SignalSample(signal, now - a.startMono, value)
        a.lastMono = now
    }

    /** 시연용 모의 주행 한 건을 같은 동의·신호 게이트로 저장한다. */
    suspend fun importMockTrip(session: DrivingSession, samples: List<SignalSample>) {
        if (!consented) return
        val supported = supportedSignals()
        val signals = session.signals intersect supported.keys
        sink.saveSession(
            session.copy(signals = signals, sampleRateHz = session.sampleRateHz.filterKeys { it in signals }, attribution = attribution()),
            samples.filter { it.signal in signals },
        )
    }

    private fun supportedSignals(): Map<SignalId, Double?> =
        support().filter { it.status == SupportStatus.SUPPORTED }.associate { it.signal to it.sampleRateHz }

    private fun start(origin: DataOrigin): Active {
        val supported = supportedSignals()
        val mono = clock.monotonicMs()
        return Active(
            driveId = ids.newId(),
            startedAt = clock.now(),
            startMono = mono,
            origin = origin,
            signals = supported.keys,
            rates = supported.mapNotNull { (k, v) -> v?.let { k to it } }.toMap(),
            lastMono = mono,
        )
    }

    private suspend fun close(a: Active, normally: Boolean) {
        active = null
        if (!consented) return
        val endMono = if (normally) clock.monotonicMs() else a.lastMono
        val session = DrivingSession(
            driveId = a.driveId,
            startedAt = a.startedAt,
            endedAt = a.startedAt.plusMillis(endMono - a.startMono),
            zone = zone,
            origin = a.origin,
            signals = a.signals,
            sampleRateHz = a.rates,
            attribution = attribution(),
            terminatedNormally = normally,
        )
        sink.saveSession(session, a.samples.toList())
    }
}
