package com.neulbom.drive.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neulbom.drive.core.model.SafetyState
import com.neulbom.drive.di.AppContainer
import com.neulbom.drive.domain.cognitive.assessment.AssessmentStatus
import com.neulbom.drive.domain.cognitive.assessment.RunnerState
import com.neulbom.drive.domain.cognitive.assessment.SaveState
import com.neulbom.drive.presentation.CarNavigator
import com.neulbom.drive.presentation.CarScreen
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.presentation.VoiceContext
import com.neulbom.drive.presentation.VoiceScript
import com.neulbom.drive.ui.car.BetweenScreen
import com.neulbom.drive.ui.car.CheckInPlaceholderScreen
import com.neulbom.drive.ui.car.CompletionScreen
import com.neulbom.drive.ui.car.DrivingScreen
import com.neulbom.drive.ui.car.GuideScreen
import com.neulbom.drive.ui.car.IdleScreen
import com.neulbom.drive.ui.car.InterruptedScreen
import com.neulbom.drive.ui.car.OfferScreen
import com.neulbom.drive.ui.car.OnboardingScreen
import com.neulbom.drive.ui.car.ProfileScreen
import com.neulbom.drive.ui.car.SavingScreen
import com.neulbom.drive.ui.car.TaskScreen
import com.neulbom.drive.ui.components.ChipTone
import com.neulbom.drive.ui.components.StatusChip
import com.neulbom.drive.ui.theme.LocalNeulbomPalette

/**
 * 차량 앱 뿌리 화면. 어떤 화면을 보일지는 [CarNavigator]가 상태만 보고 정한다
 * (주행 중이면 무조건 주행 화면). 화면을 "쌓지" 않으므로 돌아가는 길은 언제나 대기 화면 하나다.
 */
@Composable
fun NeulbomRoot(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val vehicleState by container.vehicle.adapter.observeState().collectAsStateWithLifecycle()
    val flow by container.carFlow.state.collectAsStateWithLifecycle()
    val runner by container.taskRunner.state.collectAsStateWithLifecycle()
    val serverSaved by container.carFlow.serverSaved.collectAsStateWithLifecycle()
    val safety = vehicleState.safety
    val screen = CarNavigator.screen(safety, settings.onboardingDone, flow, runner)
    val p = LocalNeulbomPalette.current

    // 음성 안내(선택): 화면이 바뀔 때 그 화면의 문장을 읽는다. 주행·과제 중에는 VoiceScript가 null → 멈춤.
    val finished = runner as? RunnerState.Finished
    val shownSave = finished?.let {
        if (it.saveState == SaveState.LOCAL_PENDING_SYNC && it.session.assessmentId in serverSaved) SaveState.SERVER_SAVED else it.saveState
    }
    val voiceText = VoiceScript.forScreen(
        screen,
        VoiceContext(
            saveState = shownSave,
            nextTask = (runner as? RunnerState.Between)?.nextTaskId,
            cancelled = finished?.session?.status == AssessmentStatus.CANCELLED,
            stopReason = finished?.session?.stopReason,
        ),
    )
    LaunchedEffect(screen, voiceText, settings.voiceGuide) {
        if (settings.voiceGuide && voiceText != null) container.voice.speak(voiceText)
        else if (screen != CarScreen.TASK) container.voice.stop() // 과제 화면의 설명 읽기는 TaskFrame이 맡는다
    }

    // 실제 가용 영역: Pleos는 하단 공조 바(140px)가 앱 창 위를 덮지만 inset으로 알려 주지 않는다.
    // 시스템이 알려 주는 화면 높이(screenHeightDp, 시스템 바 제외)를 넘지 않게 그린다. 일반 기기에서는 창 높이와 같다.
    val usableHeight = LocalConfiguration.current.screenHeightDp.dp
    Box(Modifier.fillMaxSize().background(p.background)) {
    Column(modifier = Modifier.fillMaxWidth().heightIn(max = usableHeight).fillMaxHeight().safeDrawingPadding()) {
        StatusBar(container, safety)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Crossfade(targetState = screen, label = "car-screen") { s ->
                when (s) {
                    CarScreen.DRIVING -> DrivingScreen()
                    CarScreen.ONBOARDING -> OnboardingScreen(container) {}
                    CarScreen.PROFILE -> ProfileScreen(isMock = container.vehicle.simulator != null, onAnswer = container.carFlow::answerProfile)
                    CarScreen.IDLE -> IdleScreen(container)
                    CarScreen.OFFER -> OfferScreen(
                        isFirst = flow.offerIsFirst,
                        onRespond = container.carFlow::respondToOffer,
                    )
                    CarScreen.GUIDE -> GuideScreen(onStart = container.carFlow::startCheck, onBack = container.carFlow::closeGuide)
                    CarScreen.TASK -> (runner as? RunnerState.Running)?.let { TaskScreen(container, it, onQuit = container.taskRunner::cancelByUser) }
                    CarScreen.BETWEEN -> (runner as? RunnerState.Between)?.let {
                        BetweenScreen(it, onNext = container.taskRunner::beginNextTask, onQuit = container.taskRunner::cancelByUser)
                    }
                    CarScreen.SAVING -> SavingScreen()
                    CarScreen.CHECK_IN -> CheckInPlaceholderScreen(onNext = container.carFlow::finishCheckIn)
                    CarScreen.COMPLETION -> (runner as? RunnerState.Finished)?.let {
                        // 서버 ACK를 받으면 "저장되었습니다"로 바뀐다(받기 전에는 보관 문구).
                        val shown = if (it.saveState == SaveState.LOCAL_PENDING_SYNC && it.session.assessmentId in serverSaved) SaveState.SERVER_SAVED else it.saveState
                        CompletionScreen(shown, onClose = container.carFlow::closeResult)
                    }
                    CarScreen.INTERRUPTED -> (runner as? RunnerState.Finished)?.let {
                        InterruptedScreen(
                            cancelledByUser = it.session.status == AssessmentStatus.CANCELLED,
                            reason = it.session.stopReason,
                            onClose = container.carFlow::closeResult,
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun StatusBar(container: AppContainer, safety: SafetyState) {
    val p = LocalNeulbomPalette.current
    val tone = when (safety) {
        SafetyState.PARKED_SAFE -> ChipTone.OK
        SafetyState.DRIVING -> ChipTone.NEUTRAL
        SafetyState.PARKED_UNVERIFIED, SafetyState.POWERING_OFF -> ChipTone.CAUTION
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface)
            .padding(horizontal = 32.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Pleos 시스템 바가 이미 위에 있으므로 앱 헤더는 얇고 조용하게.
        // 개발자 진입점: 앱 이름을 길게 누르면 시연·개발 도구를 켜고 끈다(mock 빌드 전용, 기본 숨김).
        Text(
            UiText.APP_NAME,
            style = MaterialTheme.typography.labelMedium,
            color = p.textSecondary,
            modifier = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { container.toggleDevTools() }) },
        )
        // 검증된 주차일 때는 조용히 숨기고, 확인 중·주행 중처럼 점검이 막힌 이유만 보여 준다.
        Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            if (safety != SafetyState.PARKED_SAFE) StatusChip(UiText.safety(safety), tone)
        }
        Spacer(Modifier.weight(1f))
        val devTools by container.devToolsVisible.collectAsStateWithLifecycle()
        if (devTools) container.vehicle.simulator?.let { sim ->
            StatusChip(container.vehicle.sourceLabel, ChipTone.CAUTION)
            // mock 전용 상태 전환(시연 편의). 실제 차량 빌드(pleos)에는 없다.
            val quick = listOf("PARKED_VERIFIED" to "P", "DRIVING" to "주행", "SIGNAL_LOST" to "불명")
            val currentId by sim.current.collectAsStateWithLifecycle()
            quick.forEach { (id, label) ->
                val on = currentId == id
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) p.ctaForeground else p.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) p.ctaBackground else p.woodLight)
                        .clickable { sim.select(id) }
                        .heightIn(min = 48.dp)
                        .widthIn(min = 64.dp)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }
    }
}
