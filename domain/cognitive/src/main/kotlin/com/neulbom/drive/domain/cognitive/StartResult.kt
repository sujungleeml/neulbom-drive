package com.neulbom.drive.domain.cognitive

sealed interface StartResult {
    data class Started(val assessmentId: String) : StartResult
    data class Denied(val reason: DenialReason) : StartResult
}
