package com.neulbom.drive.ui.car

import com.neulbom.drive.ui.layout.layoutPainter
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.Image
import com.neulbom.drive.ui.layout.LocalScreenLayouts
import com.neulbom.drive.ui.layout.IdleLayout
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neulbom.drive.R
import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.core.model.SupportStatus
import com.neulbom.drive.di.AppContainer
import com.neulbom.drive.domain.cognitive.GateDecision
import com.neulbom.drive.domain.consent.ConsentType
import com.neulbom.drive.domain.scheduler.ScheduleState
import com.neulbom.drive.presentation.ProfileAnswer
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.components.Body
import com.neulbom.drive.ui.components.Caption
import com.neulbom.drive.ui.components.Heading
import com.neulbom.drive.ui.components.InfoCard
import com.neulbom.drive.ui.components.PrimaryButton
import com.neulbom.drive.ui.components.SecondaryButton
import com.neulbom.drive.ui.components.ToggleRow
import com.neulbom.drive.ui.theme.Dimens
import com.neulbom.drive.ui.theme.LocalNeulbomPalette
import com.neulbom.drive.ui.theme.isNight

/**
 * 대기 화면: 지금 할 수 있는 일 한 가지(점검 시작)를 크게, 기록은 웹에서 본다는 안내를 작게.
 * 차 안에서는 점검 결과·추세를 보여 주지 않는다(ADR-01).
 */
@Composable
fun IdleScreen(container: AppContainer) {
    val vehicleState by container.vehicle.adapter.observeState().collectAsStateWithLifecycle()
    val consents by container.consents.state.collectAsStateWithLifecycle()
    val foreground by container.appInForeground.collectAsStateWithLifecycle()
    val syncStatus by container.controller.syncStatus.collectAsStateWithLifecycle()
    val flow by container.carFlow.state.collectAsStateWithLifecycle()
    val schedule by container.carFlow.scheduleState.collectAsStateWithLifecycle()
    val denial by container.carFlow.lastDenial.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val devTools by container.devToolsVisible.collectAsStateWithLifecycle()
    val notMe = flow.profile == ProfileAnswer.NOT_ME
    val scope = rememberCoroutineScope()
    val decision = container.gate.evaluateStart(
        state = vehicleState,
        capabilities = container.vehicle.adapter.capabilityFlags(),
        userInitiated = true,
        appInForeground = foreground,
        cognitiveConsent = consents.isGranted(ConsentType.COGNITIVE_CHECK),
    )
    val parked = vehicleState.safety == SafetyState.PARKED_SAFE
    val supported = container.vehicle.adapter.signalSupport().count { it.status == SupportStatus.SUPPORTED }

    IdleContent(
        parked = parked,
        checkEnabled = decision.isAllowed && !notMe,
        notice = when {
            notMe -> UiText.PROFILE_NOT_ME_NOTE
            decision is GateDecision.Denied -> UiText.CHECK_UNAVAILABLE_PREFIX + decision.reason.message
            denial != null -> UiText.CHECK_UNAVAILABLE_PREFIX + denial!!.message
            else -> null
        },
        scheduleText = scheduleLine(schedule),
        drivingText = if (consents.isGranted(ConsentType.DRIVING_RECORD)) "주행 습관을 기록하고 있어요. 이 차량에서 받는 정보 ${supported}가지."
        else "주행 습관 기록이 꺼져 있어요.",
        syncStatus = syncStatus,
        isMock = container.vehicle.simulator != null && devTools,
        demoTools = if (devTools && container.demoToolsEnabled && container.vehicle.demoTrips != null) {
            DemoTools(container.controller::generateDemoHistory, container.controller::upload, container.carFlow::resetOffersForDemo, container.controller::resetDemo)
        } else {
            null
        },
        onCheck = container.carFlow::openGuide,
        drivingRecordOn = consents.isGranted(ConsentType.DRIVING_RECORD),
        onDrivingRecordChange = { granted -> scope.launch { container.consents.update(ConsentType.DRIVING_RECORD, granted) } },
        retentionDays = settings.retentionDays,
        voiceOn = settings.voiceGuide,
        onVoiceChange = { on -> container.settings.update { it.copy(voiceGuide = on) } },
    )
}

/** 주행 습관 기록 동의(대기 화면 '기록 보기'에서 바로 켜기). */
class DrivingConsent(val on: Boolean, val onChange: (Boolean) -> Unit, val retentionDays: Int)

/** mock 플래버의 시연 도구 버튼들. */
class DemoTools(val makeHistory: () -> Unit, val sendNow: () -> Unit, val replayOffer: () -> Unit, val resetAll: () -> Unit)

/** 대기 화면 그리기(상태만 받는다). */
@Composable
fun IdleContent(
    parked: Boolean,
    checkEnabled: Boolean,
    notice: String?,
    scheduleText: String,
    drivingText: String,
    syncStatus: String,
    isMock: Boolean,
    demoTools: DemoTools?,
    onCheck: () -> Unit,
    voiceOn: Boolean = false,
    onVoiceChange: (Boolean) -> Unit = {},
    drivingRecordOn: Boolean = true,
    onDrivingRecordChange: (Boolean) -> Unit = {},
    retentionDays: Int = 180,
) {
    var showResultsInfo by remember { mutableStateOf(false) }
    var showDemoTools by remember { mutableStateOf(false) }
    // 배치 값은 Visual Layout Studio가 저장한 assets/layout/idle.json(주간·야간 따로). 영역은 NeulbomRoot가 정한 실제 가용 영역.
    val l = LocalScreenLayouts.current.idle(LocalNeulbomPalette.current.isNight)
    Row(Modifier.fillMaxSize().padding(start = l.safeStart.dp, end = l.safeEnd.dp, top = l.safeTop.dp + Dimens.Gutter, bottom = l.safeBottom.dp)) {
        if (l.brand.visible) BrandPanel(l.brand, Modifier.weight(l.brand.widthFraction).fillMaxHeight())
        Box(Modifier.weight(1f - (if (l.brand.visible) l.brand.widthFraction else 0f)).fillMaxHeight()) {
            IdleMain(l.main, parked, checkEnabled, notice, scheduleText, drivingText, syncStatus, isMock, demoTools, onCheck, voiceOn, onVoiceChange, showResultsInfo, { showResultsInfo = it }, showDemoTools, { showDemoTools = it },
                DrivingConsent(drivingRecordOn, onDrivingRecordChange, retentionDays))
        }
    }
}

/**
 * 브랜드 패널: 브랜드 그래픽(660×1050 기준) 위에 미션 문장을 **별도 레이어**로 얹는다.
 * 그림은 비율을 지킨 채 영역 안에서 잘리고(기준점·확대는 배치 값), 문장은 그림이 어떻게 잘리든 늘 보인다. 장식이라 접근성 설명은 두지 않는다.
 */
@Composable
private fun BrandPanel(b: IdleLayout.Brand, modifier: Modifier) {
    val p = LocalNeulbomPalette.current
    val align = BiasAlignment(b.imageAlignX * 2 - 1, b.imageAlignY * 2 - 1)
    Box(
        modifier.clip(RoundedCornerShape(b.cornerRadius.dp)).background(p.woodLight),
        contentAlignment = Alignment.BottomStart,
    ) {
        Image(
            painter = layoutPainter(b.image, if (p.isNight) R.drawable.idle_dark else R.drawable.idle_light),
            contentDescription = null,
            contentScale = if (b.imageFit == "fit") ContentScale.Fit else ContentScale.Crop,
            alignment = align,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = b.imageScale
                scaleY = b.imageScale
                transformOrigin = TransformOrigin(b.imageAlignX, b.imageAlignY)
            },
        )
        if (b.missionVisible) {
            val style = MaterialTheme.typography.titleLarge.copy(fontSize = b.missionFontSize.sp, lineHeight = (b.missionFontSize * 1.28f).sp)
            // 좁은 패널에서 어절이 끊기지 않도록 쉼표 뒤에서 줄을 바꾼다.
            Text(
                UiText.MISSION.replace(", ", ",\n"),
                style = style,
                color = p.primary,
                modifier = Modifier
                    .padding(start = b.missionPaddingX.dp, end = b.missionPaddingX.dp, bottom = b.missionPaddingBottom.dp)
                    .then(if (b.missionBackdrop) Modifier.clip(RoundedCornerShape(16.dp)).background(p.woodLight.copy(alpha = 0.82f)).padding(horizontal = 20.dp, vertical = 14.dp) else Modifier),
            )
        }
    }
}

@Composable
private fun IdleMain(
    m: IdleLayout.Main,
    parked: Boolean,
    checkEnabled: Boolean,
    notice: String?,
    scheduleText: String,
    drivingText: String,
    syncStatus: String,
    isMock: Boolean,
    demoTools: DemoTools?,
    onCheck: () -> Unit,
    voiceOn: Boolean,
    onVoiceChange: (Boolean) -> Unit,
    showResultsInfo: Boolean,
    setShowResultsInfo: (Boolean) -> Unit,
    showDemoTools: Boolean,
    setShowDemoTools: (Boolean) -> Unit,
    driving: DrivingConsent,
) {
    val actions: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(m.ctaGap.dp), modifier = Modifier.fillMaxWidth().padding(bottom = m.actionsBottomOffset.dp)) {
            PrimaryButton(UiText.CHECK_NOW, onClick = onCheck, enabled = checkEnabled, modifier = Modifier.weight(m.primaryWeight), minHeight = m.ctaHeight.dp)
            SecondaryButton(
                UiText.IDLE_RESULTS_BUTTON,
                onClick = { setShowResultsInfo(!showResultsInfo) },
                modifier = Modifier.weight(m.secondaryWeight),
                minHeight = m.ctaHeight.dp,
            )
        }
    }
    val atBottom = m.actionsPlacement == "bottom"
    // 행동 버튼은 늘 가용 영역 안에 있다: 내용은 남는 높이 안에서 스크롤하고, 버튼은 그 아래에 고정된다.
    Column(
        modifier = Modifier.fillMaxSize().padding(start = m.paddingStart.dp, end = m.paddingEnd.dp, top = m.paddingTop.dp),
        verticalArrangement = Arrangement.spacedBy(m.gap.dp),
    ) {
        Text(
            if (parked) UiText.IDLE_TITLE_PARKED else UiText.IDLE_TITLE_WAIT,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = m.titleFontSize.sp, lineHeight = (m.titleFontSize * 1.23f).sp),
            modifier = Modifier.semantics { heading() },
        )
        Column(
            modifier = Modifier.weight(1f, fill = atBottom).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IdleBody(notice, scheduleText, drivingText, syncStatus, isMock, demoTools, voiceOn, onVoiceChange, showResultsInfo, showDemoTools, setShowDemoTools, driving)
        }
        actions()
    }
}

@Composable
private fun IdleBody(
    notice: String?,
    scheduleText: String,
    drivingText: String,
    syncStatus: String,
    isMock: Boolean,
    demoTools: DemoTools?,
    voiceOn: Boolean,
    onVoiceChange: (Boolean) -> Unit,
    showResultsInfo: Boolean,
    showDemoTools: Boolean,
    setShowDemoTools: (Boolean) -> Unit,
    driving: DrivingConsent,
) {
    var showDrivingConsent by remember { mutableStateOf(false) }
    run {
        if (notice != null) Caption(notice)
        // 첫 화면에는 점검 카드 하나만. 음성 안내는 카드 없이 한 줄로, 주행 습관은 "기록 보기" 안으로.
        InfoCard {
            Body(scheduleText)
            Caption(UiText.IDLE_INTERVAL_NOTE)
        }
        ToggleRow(
            title = UiText.VOICE_TITLE,
            checked = voiceOn,
            onChange = onVoiceChange,
            description = UiText.VOICE_DESCRIPTION,
            subtle = true,
        )
        if (showResultsInfo) {
            InfoCard {
                Heading(UiText.IDLE_RESULTS_TITLE)
                if (driving.on) {
                    Caption(drivingText)
                } else {
                    // 꺼져 있으면 '켜러 가기' → 별도 창에서 동의(대기 화면에는 펼치지 않는다). 동의 전에는 수집하지 않고, 점검은 그대로 할 수 있다.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Caption(UiText.IDLE_DRIVING_OFF, modifier = Modifier.weight(1f))
                        SecondaryButton(UiText.IDLE_DRIVING_TURN_ON, onClick = { showDrivingConsent = true })
                    }
                }
                if (showDrivingConsent) DrivingConsentDialog(driving, onDismiss = { showDrivingConsent = false })
                if (syncStatus.isNotEmpty()) Caption(syncStatus)
                // TODO(WEB-URL): 운영 웹 주소가 정해지면 교체. 지금은 개발 PC 주소만 안내한다.
                if (isMock) Caption("시연: 개발 PC 브라우저에서 http://localhost:8080")
            }
        }
        // 시연 도구는 mock 빌드 전용이고, 사용자 화면과 섞이지 않게 기본으로 접어 둔다.
        if (demoTools != null) {
            SecondaryButton(if (showDemoTools) "시연 도구 닫기" else "시연 도구 열기", onClick = { setShowDemoTools(!showDemoTools) })
        }
        if (demoTools != null && showDemoTools) {
            InfoCard {
                Body("시연 도구 (모의 데이터)")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton("모의 주행 4주 만들기", onClick = demoTools.makeHistory, modifier = Modifier.weight(1f))
                    SecondaryButton("지금 보내기", onClick = demoTools.sendNow, modifier = Modifier.weight(1f))
                    SecondaryButton("제안 다시 보기", onClick = demoTools.replayOffer, modifier = Modifier.weight(1f))
                }
                SecondaryButton("시연 처음부터 (기기 안 기록 지움)", onClick = demoTools.resetAll, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** 주행 습관 기록 동의 창: 온보딩과 같은 설명(수집·미수집·목적·보관)과 스위치. */
@Composable
private fun DrivingConsentDialog(driving: DrivingConsent, onDismiss: () -> Unit) {
    val p = LocalNeulbomPalette.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(0.7f).clip(RoundedCornerShape(Dimens.CardRadius)).background(p.surface).padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Heading(UiText.consentTitle(ConsentType.DRIVING_RECORD))
            ToggleRow(title = if (driving.on) "켜짐" else "꺼짐", checked = driving.on, onChange = driving.onChange)
            Caption(UiText.consentDetail(ConsentType.DRIVING_RECORD, driving.retentionDays))
            Caption(UiText.withoutConsent(ConsentType.DRIVING_RECORD))
            PrimaryButton(UiText.IDLE_DRIVING_CLOSE, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun scheduleLine(state: ScheduleState): String = when (state) {
    ScheduleState.BASELINE_COLLECTING -> "운전의 즐거움을 이어가는 작은 습관, 잠깐의 점검으로 시작해 보세요."
    ScheduleState.ROUTINE_DUE -> "정기 점검을 할 때가 되었어요."
    ScheduleState.SUSPENDED, ScheduleState.DEFERRED -> "오늘은 점검을 제안하지 않아요. 원하시면 직접 시작할 수 있어요."
    else -> "다음 정기 점검 때 알려 드릴게요."
}
