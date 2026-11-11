package com.neulbom.drive.settings

import android.content.Context
import com.neulbom.drive.core.model.DataOrigin
import com.neulbom.drive.domain.consent.RetentionPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val onboardingDone: Boolean = false,
    val promptsEnabled: Boolean = true,
    val retentionDays: Int = RetentionPolicy.DEFAULT_DAYS,
    val voiceGuide: Boolean = false,
    /** 분석·기록 화면이 보여줄 데이터 출처. 모의 데이터는 MOCK. */
    val analysisOrigin: DataOrigin = DataOrigin.REAL,
    val demoProfile: String? = null,
    /** 웹 설정의 "점검 제안 일시 중지" 끝 시각(epoch ms). null이면 중지 안 함. */
    val offersPausedUntilMs: Long? = null,
)

/** 간단한 앱 설정(SharedPreferences). 개인 기록은 저장하지 않는다. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("neulbom_settings", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = flow.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(flow.value)
        prefs.edit()
            .putBoolean(K_ONBOARDING, next.onboardingDone)
            .putBoolean(K_PROMPTS, next.promptsEnabled)
            .putInt(K_RETENTION, next.retentionDays)
            .putBoolean(K_VOICE, next.voiceGuide)
            .putString(K_ORIGIN, next.analysisOrigin.name)
            .putString(K_PROFILE, next.demoProfile)
            .putLong(K_PAUSED, next.offersPausedUntilMs ?: -1L)
            .apply()
        flow.value = next
    }

    private fun load() = AppSettings(
        onboardingDone = prefs.getBoolean(K_ONBOARDING, false),
        promptsEnabled = prefs.getBoolean(K_PROMPTS, true),
        retentionDays = prefs.getInt(K_RETENTION, RetentionPolicy.DEFAULT_DAYS).takeIf { it in RetentionPolicy.OPTIONS } ?: RetentionPolicy.DEFAULT_DAYS,
        voiceGuide = prefs.getBoolean(K_VOICE, false),
        analysisOrigin = runCatching { DataOrigin.valueOf(prefs.getString(K_ORIGIN, null) ?: "REAL") }.getOrDefault(DataOrigin.REAL),
        demoProfile = prefs.getString(K_PROFILE, null),
        offersPausedUntilMs = prefs.getLong(K_PAUSED, -1L).takeIf { it >= 0 },
    )

    private companion object {
        const val K_ONBOARDING = "onboarding_done"
        const val K_PROMPTS = "prompts_enabled"
        const val K_RETENTION = "retention_days"
        const val K_VOICE = "voice_guide"
        const val K_ORIGIN = "analysis_origin"
        const val K_PROFILE = "demo_profile"
        const val K_PAUSED = "offers_paused_until"
    }
}
