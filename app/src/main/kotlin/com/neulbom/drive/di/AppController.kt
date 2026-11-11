package com.neulbom.drive.di

import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.domain.consent.RetentionPolicy
import com.neulbom.drive.domain.cognitive.assessment.RunnerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 앱 수준 흐름 조정: 보존기간 정리, 주행 세션 기록·업로드.
 * mock 플래버는 차량이 실제 표본을 보내지 않으므로 주행이 끝날 때 **모의** 주행 한 건을 같은 게이트로 넣는다.
 */
class AppController(private val c: AppContainer) {
    private val syncFlow = MutableStateFlow("")
    val syncStatus: StateFlow<String> = syncFlow.asStateFlow()

    fun start() {
        c.carFlow.start()
        c.appScope.launch {
            c.dataManager.purgeExpired(RetentionPolicy(c.settings.settings.value.retentionDays), c.clock.now())
            c.uploader.uploadSignalSupport(c.vehicle.adapter.signalSupport())
            syncSettings()
            upload()
            uploadChecks()
        }
        c.appScope.launch {
            // 점검이 끝나면(완료·중단·취소) 바로 보낸다. 실패하면 다음 주차·시작 때 다시.
            c.taskRunner.state.collect { if (it is RunnerState.Finished) uploadChecks() }
        }
        c.appScope.launch {
            var wasDriving = false
            c.vehicle.adapter.observeState().map { it.safety }.distinctUntilChanged().collect { safety ->
                val demo = c.vehicle.demoTrips
                if (demo == null) {
                    c.drivingRecorder.onSafetyChanged(safety, DataOrigin.REAL)
                } else if (wasDriving && (safety == SafetyState.PARKED_SAFE || safety == SafetyState.POWERING_OFF)) {
                    val (session, samples) = demo.tripEndingNow(c.clock.now(), c.zone)
                    c.drivingRecorder.importMockTrip(session, samples)
                }
                if (safety == SafetyState.PARKED_SAFE || safety == SafetyState.POWERING_OFF) {
                    // 네트워크는 따로 실행한다 — 이 수집기가 다음 주행 전환을 늦게 받지 않도록.
                    c.appScope.launch { syncSettings() }
                    upload()
                    uploadChecks()
                }
                wasDriving = safety == SafetyState.DRIVING
            }
        }
    }

    /** mock 시연 도구: 처음 설치한 상태로 되돌린다(서버 기록은 그대로). */
    fun resetDemo() {
        if (c.vehicle.demoTrips == null) return
        c.appScope.launch {
            DemoReset(
                consents = c.consents,
                stopFlow = {
                    c.taskRunner.cancelByUser()
                    // 취소 기록이 다 써질 때까지 기다린 뒤 지운다(늦게 써진 기록이 남지 않도록).
                    c.taskRunner.state.first { it is RunnerState.Idle || it is RunnerState.Finished }
                    c.taskRunner.reset()
                    c.carFlow.resetForDemo()
                },
                deleteLocal = { c.dataManager.deleteAllRecords() },
                resetSettings = { c.settings.update { it.copy(onboardingDone = false, offersPausedUntilMs = null) } },
            ).run()
            syncFlow.value = ""
        }
    }

    /** 개발 데모: 오늘까지 4주치 모의 주행 기록을 만들어 보낸다. */
    fun generateDemoHistory() {
        val demo = c.vehicle.demoTrips ?: return
        c.appScope.launch {
            syncFlow.value = "모의 주행 기록을 만드는 중이에요."
            demo.history(LocalDate.now(c.zone), weeks = 4, zone = c.zone).forEach { (session, samples) ->
                c.drivingRecorder.importMockTrip(session, samples)
            }
            upload()
        }
    }

    /** 웹에서 바꾼 동의·제안 일시 중지를 받아 오고, 차량에서 바꾼 동의는 올린다. 연결이 안 되면 그대로 둔다. */
    suspend fun syncSettings() {
        val remote = c.settingsSync.sync() ?: return
        c.settings.update { it.copy(offersPausedUntilMs = remote.offersPausedUntil?.toEpochMilli()) }
    }

    fun uploadChecks() {
        c.appScope.launch {
            c.carFlow.markServerSaved(c.assessmentUploader.uploadPending().syncedIds)
        }
    }

    fun upload() {
        c.appScope.launch {
            val r = c.uploader.uploadPending()
            syncFlow.value = when {
                r.sent == 0 && r.failed == 0 -> syncFlow.value
                r.failed == 0 -> "주행 기록 ${r.sent}건을 보냈어요."
                else -> "주행 기록 ${r.sent}건을 보냈고 ${r.failed}건은 연결되면 다시 보내요."
            }
        }
    }
}
