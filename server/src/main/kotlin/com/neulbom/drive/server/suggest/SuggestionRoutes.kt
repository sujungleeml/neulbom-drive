package com.neulbom.drive.server.suggest

import com.neulbom.drive.domain.consent.ConsentType
import com.neulbom.drive.server.settings.SettingsStore
import com.neulbom.drive.server.settings.current
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class SuggestionItemDto(val id: String, val title: String, val together: String, val action: String)

@Serializable
data class SuggestionScenarioDto(val id: String, val label: String)

/**
 * state: DEMO(시연 결과) | NOT_ENOUGH_DATA | NO_MATCH | CONSENT_REQUIRED | NOT_VALIDATED(실제 기록에는 아직 제안을 만들지 않음)
 */
@Serializable
data class SuggestionsDto(
    val state: String,
    val synthetic: Boolean,
    val rulesetId: String?,
    val rulesValidated: Boolean,
    val notice: String,
    val scenario: String? = null,
    val primary: SuggestionItemDto? = null,
    val others: List<SuggestionItemDto> = emptyList(),
    val activityEvidence: List<String> = emptyList(),
    val activityBasis: String? = null,
    val drivingEvidence: List<String> = emptyList(),
    val drivingBasis: String? = null,
    val scenarios: List<SuggestionScenarioDto> = emptyList(),
)

private fun item(id: String) = SuggestionCopy.MESSAGES.getValue(id).let { SuggestionItemDto(id, it.title, it.together, it.action) }

/** 엔진 결과를 화면용으로 바꾼다(시연과 테스트 공용). */
fun suggestionsDto(r: SuggestionEngine.Result, scenario: String?, ruleset: SuggestionEngine.Ruleset, synthetic: Boolean): SuggestionsDto {
    val enough = r.activity.baselineReady && r.activity.rtPct != null && r.driving.comparable
    val state = when {
        !enough -> "NOT_ENOUGH_DATA"
        r.matched.isEmpty() -> "NO_MATCH"
        else -> "DEMO"
    }
    return SuggestionsDto(
        state = state,
        synthetic = synthetic,
        rulesetId = ruleset.id,
        rulesValidated = ruleset.validated,
        notice = when (state) { "NOT_ENOUGH_DATA" -> SuggestionCopy.NOT_ENOUGH; "NO_MATCH" -> SuggestionCopy.NO_MATCH; else -> DEMO_NOTICE },
        scenario = scenario,
        primary = r.primary?.let(::item),
        others = r.matched.drop(1).map(::item),
        activityEvidence = if (enough) SuggestionCopy.activityEvidence(r.activity) else emptyList(),
        activityBasis = if (enough) SuggestionCopy.ACTIVITY_BASIS else null,
        drivingEvidence = if (enough) SuggestionCopy.drivingEvidence(r.driving) else emptyList(),
        drivingBasis = if (enough) SuggestionCopy.drivingBasis(r.driving) else null,
    )
}

const val DEMO_NOTICE = "합성 데이터 기반 기능 시연이에요. 기준값은 아직 검증되지 않았고, 두 기록이 함께 달라졌다고 해서 서로 원인이라는 뜻은 아니에요."
const val NOT_VALIDATED_NOTICE = "실제 기록에는 아직 제안을 만들지 않아요. 기준값을 검증한 뒤에 열 예정이에요."
const val CONSENT_NOTICE = "주행 습관 기록과 점검 기록 동의가 모두 켜져 있을 때만 두 기록을 함께 살펴볼 수 있어요."

private fun granted(settings: SettingsStore, type: ConsentType) = current(settings).consents.firstOrNull { it.type == type.name }?.granted == true

/**
 * - `/v1/reports/me/suggestions`: 실제 사용자 경로. 동의를 먼저 확인하고(없으면 기록을 읽지 않는다), 운영 규칙이 없으므로 제안을 만들지 않는다.
 * - `/v1/demo/suggestions?scenario=R01`: 시연 경로. [demo]가 켜진 서버에서만 열리고, 합성 기록만 쓴다(저장소를 읽지 않는다).
 */
fun Route.suggestionRoutes(settings: SettingsStore, demo: Boolean) {
    get("/v1/reports/me/suggestions") {
        val ok = granted(settings, ConsentType.DRIVING_RECORD) && granted(settings, ConsentType.COGNITIVE_CHECK)
        call.respond(
            if (!ok) SuggestionsDto("CONSENT_REQUIRED", false, null, false, CONSENT_NOTICE)
            else SuggestionsDto("NOT_VALIDATED", false, null, false, NOT_VALIDATED_NOTICE),
        )
    }
    get("/v1/demo/suggestions") {
        if (!demo) return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "demo off"))
        val id = call.request.queryParameters["scenario"] ?: "R01"
        val s = DemoSuggestionFixtures.get(id) ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "unknown scenario"))
        val r = SuggestionEngine.evaluate(SuggestionEngine.activityChange(s.sessions), SuggestionEngine.drivingChange(s.previous, s.current))
        call.respond(suggestionsDto(r, s.id, SuggestionEngine.DEMO_RULESET, synthetic = true).copy(
            scenarios = DemoSuggestionFixtures.ALL.map { SuggestionScenarioDto(it.id, it.label) },
        ))
    }
}
