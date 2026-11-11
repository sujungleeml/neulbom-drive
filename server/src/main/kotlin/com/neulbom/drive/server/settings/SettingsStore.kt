package com.neulbom.drive.server.settings

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
data class ConsentEventDto(val type: String, val granted: Boolean, val changedAt: String)

@Serializable
data class SettingsDto(val consents: List<ConsentEventDto>, val offersPausedUntil: String?, val retentionDays: Int)

@Serializable
data class ConsentChangeDto(val granted: Boolean)

@Serializable
data class OfferPauseDto(val pauseDays: Int)

/** 동의 변경 이벤트(감사 이력)와 제안 일시 중지. 시연용 단일 사용자, 메모리. */
interface SettingsStore {
    fun addConsentEvent(e: ConsentEventDto)
    fun consentEvents(): List<ConsentEventDto>
    var offersPausedUntil: Instant?
}

class InMemorySettingsStore : SettingsStore {
    private val events = CopyOnWriteArrayList<ConsentEventDto>()
    @Volatile override var offersPausedUntil: Instant? = null
    override fun addConsentEvent(e: ConsentEventDto) { events += e }
    override fun consentEvents(): List<ConsentEventDto> = events.toList()
}
