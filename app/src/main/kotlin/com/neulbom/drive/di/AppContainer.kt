package com.neulbom.drive.di

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.neulbom.drive.BuildConfig
import com.neulbom.drive.core.id.RandomUuidGenerator
import com.neulbom.drive.core.time.SystemClock
import com.neulbom.drive.data.local.LocalDataManager
import com.neulbom.drive.data.local.NeulbomDatabase
import com.neulbom.drive.data.local.RoomTrialLogSink
import com.neulbom.drive.data.local.RoomConsentRepository
import com.neulbom.drive.data.local.RoomDrivingQueue
import com.neulbom.drive.data.sync.DrivingUploader
import com.neulbom.drive.data.sync.AssessmentUploader
import com.neulbom.drive.data.sync.SettingsSync
import com.neulbom.drive.data.local.RoomAssessmentQueue
import com.neulbom.drive.core.model.Attribution
import com.neulbom.drive.domain.driving.DrivingRecorder
import com.neulbom.drive.vehicle.api.DriverIdentity
import com.neulbom.drive.domain.cognitive.ProvisionalProtocols
import com.neulbom.drive.domain.cognitive.assessment.AssessmentRunner
import com.neulbom.drive.domain.cognitive.ParkingSafetyGate
import com.neulbom.drive.settings.SettingsStore
import com.neulbom.drive.vehicle.VehicleModule
import com.neulbom.drive.voice.VoiceGuide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 수동 DI 컨테이너. 앱 전체에서 하나만 존재한다. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val clock = SystemClock
    val ids = RandomUuidGenerator
    val appVersion: String = BuildConfig.VERSION_NAME
    val demoToolsEnabled: Boolean = BuildConfig.DEMO_TOOLS_ENABLED

    val settings = SettingsStore(context)
    val vehicle = VehicleModule.create(clock, appScope)
    val gate = ParkingSafetyGate()
    val voice = VoiceGuide(context)

    private val devToolsFlow = MutableStateFlow(false)

    /** 시연·개발 도구(모의 차량 상태 전환, 시연 도구) 표시. 기본은 숨김이라 리뷰·사용자 화면에 섞이지 않는다. */
    val devToolsVisible: StateFlow<Boolean> = devToolsFlow.asStateFlow()

    /** 상단 "늘봄 Drive"를 길게 누르면 켜고 끈다. 모의 차량(mock 빌드)에서만 동작한다. */
    fun toggleDevTools() {
        if (vehicle.simulator != null) devToolsFlow.value = !devToolsFlow.value
    }

    private val foregroundFlow = MutableStateFlow(false)
    val appInForeground: StateFlow<Boolean> = foregroundFlow.asStateFlow()

    val db = NeulbomDatabase.create(context)
    val consents = RoomConsentRepository(db.consentDao(), clock, appScope)

    // 주행 습관 축(명세 v0.6 08): 동의·신호 게이트를 거친 세션만 대기열 → 서버
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
    val drivingQueue = RoomDrivingQueue(db)
    val drivingRecorder = DrivingRecorder(
        consents = consents,
        support = { vehicle.adapter.signalSupport() },
        sink = drivingQueue,
        clock = clock,
        ids = ids,
        zone = zone,
        attribution = { if (vehicle.driverIdentity.currentDriver() is DriverIdentity.Verified) Attribution.VERIFIED else Attribution.UNVERIFIED },
    )
    val uploader = DrivingUploader(BuildConfig.SERVER_BASE_URL, drivingQueue)
    val dataManager = LocalDataManager(db, drivingQueue)

    // 주의·반응 점검 축(명세 v0.6 03): 세 과제, 시행 원자료 즉시 저장
    val taskRunner = AssessmentRunner(
        gate = gate,
        vehicle = vehicle.adapter,
        consents = consents,
        sink = RoomTrialLogSink(db, ids),
        protocols = ProvisionalProtocols.ALL,
        appForeground = appInForeground,
        clock = clock,
        ids = ids,
        scope = appScope,
        appVersion = appVersion,
        deviceProfile = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.SDK_INT}",
    )

    /** 끝난 점검 원자료를 서버로(ACK 후 SERVER_SAVED). 차량 출처는 빌드 플래버로 정한다. */
    val assessmentUploader = AssessmentUploader(
        BuildConfig.SERVER_BASE_URL,
        RoomAssessmentQueue(db, if (vehicle.simulator != null) "MOCK" else "PLEOS"),
    )

    /** 웹 설정(동의·제안 일시 중지)과 맞추기. 늦게 바뀐 동의가 이긴다. */
    val settingsSync = SettingsSync(BuildConfig.SERVER_BASE_URL, consents)

    val carFlow = CarFlowController(this)
    val controller = AppController(this)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foregroundFlow.value = true
            }

            override fun onStop(owner: LifecycleOwner) {
                // 앱이 백그라운드로 가면 진행 중 과제는 Runner가 즉시 중지한다.
                foregroundFlow.value = false
                voice.stop()
            }
        })
    }
}
