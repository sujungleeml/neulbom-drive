package com.neulbom.drive.ui.car

import com.neulbom.drive.ui.layout.pageLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import com.neulbom.drive.R
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neulbom.drive.di.AppContainer
import com.neulbom.drive.domain.cognitive.Block
import com.neulbom.drive.domain.cognitive.ResponseKey
import com.neulbom.drive.domain.cognitive.Stimulus
import com.neulbom.drive.domain.cognitive.SwitchRule
import com.neulbom.drive.domain.cognitive.TaskId
import com.neulbom.drive.domain.cognitive.TrialOutcome
import com.neulbom.drive.domain.cognitive.TrialResult
import com.neulbom.drive.domain.cognitive.TrialSpec
import com.neulbom.drive.domain.cognitive.assessment.RunnerState
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.components.Body
import com.neulbom.drive.ui.components.Caption
import com.neulbom.drive.ui.components.Heading
import com.neulbom.drive.ui.components.PrimaryButton
import com.neulbom.drive.ui.components.SecondaryButton
import com.neulbom.drive.ui.components.SectionCard
import com.neulbom.drive.ui.theme.Dimens
import com.neulbom.drive.ui.theme.NeulbomColors
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import com.neulbom.drive.ui.theme.LocalNeulbomPalette
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neulbom.drive.voice.Speaker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 과제 화면의 단계. 상태 전용 [TaskFrame]이 그린다(스크린샷 테스트에서도 사용). */
internal sealed interface Phase {
    data class Intro(val block: Block) : Phase
    data object Fixation : Phase
    data class Showing(val spec: TrialSpec) : Phase
    data object Hidden : Phase
    data class Feedback(val text: String, val ok: Boolean) : Phase
}

/** CAR-05~07: 세 과제 공통 자극 화면. 제시 순서·시간은 프로토콜 값(측정 조건)이라 화면에서 바꾸지 않는다. */
@Composable
fun TaskScreen(container: AppContainer, running: RunnerState.Running, onQuit: () -> Unit) {
    BackHandler(onBack = onQuit)
    val voiceOn = container.settings.settings.collectAsStateWithLifecycle().value.voiceGuide
    val engine = running.engine
    val protocol = running.protocol
    val clock = container.clock
    val scope = rememberCoroutineScope()
    val key = running.assessmentId + running.taskId
    var phase by remember(key) { mutableStateOf<Phase>(Phase.Fixation) }
    var tick by remember(key) { mutableIntStateOf(0) }
    var introducedBlock by remember(key) { mutableStateOf<Block?>(null) }

    fun afterResult(result: TrialResult) {
        container.taskRunner.onTrialRecorded(result)
        if (result.spec.isPractice) {
            val ok = result.outcome == TrialOutcome.CORRECT || result.outcome == TrialOutcome.CORRECT_REJECTION
            phase = Phase.Feedback(
                when (result.outcome) {
                    TrialOutcome.CORRECT, TrialOutcome.CORRECT_REJECTION -> "잘하셨어요"
                    TrialOutcome.OMISSION -> "조금 더 빨리 눌러 보세요"
                    TrialOutcome.COMMISSION -> "이 모양에서는 누르지 않아요"
                    else -> "반대쪽이에요. 다시 해 볼게요"
                },
                ok,
            )
            scope.launch {
                delay(900)
                tick++
            }
        } else {
            tick++
        }
    }

    // 자극 제시 순서·시간은 프로토콜 값(측정 조건)이다.
    LaunchedEffect(key, tick) {
        val spec = engine.current ?: return@LaunchedEffect
        if (spec.block != introducedBlock) {
            phase = Phase.Intro(spec.block)
            return@LaunchedEffect
        }
        phase = Phase.Fixation
        delay(spec.isiMs)
        val requested = clock.monotonicMs()
        phase = Phase.Showing(spec)
        withFrameNanos { } // 자극이 그려질 프레임까지 대기 → 실제 표시 시각 기록(장치 지연)
        val shownAt = clock.monotonicMs()
        engine.stimulusShown(shownAt, requestedAtMs = requested)
        val display = protocol.displayDurationMs
        if (display != null && display < protocol.responseWindowMs) {
            delay(display)
            if (phase is Phase.Showing) phase = Phase.Hidden
            delay(protocol.responseWindowMs - display)
        } else {
            delay(protocol.responseWindowMs)
        }
        engine.windowElapsed(clock.monotonicMs())?.let { afterResult(it) }
    }

    val (done, total) = engine.progress
    TaskFrame(
        // 설명 화면에서는 시행 번호 대신 안내라고 쓴다. 번호는 시행이 시작된 뒤에만.
        header = "${running.taskIndex + 1}/3 ${UiText.taskLabel(running.taskId)} · " + when (val ph = phase) {
            is Phase.Intro -> if (ph.block == Block.PRACTICE) "연습 안내" else "안내"
            else -> "${minOf(done + 1, total)} / $total"
        },
        taskId = running.taskId,
        phase = phase,
        current = engine.current,
        onQuit = onQuit,
        onStartBlock = { block ->
            introducedBlock = block
            tick++
        },
        onRespond = { k -> engine.respond(k, clock.monotonicMs())?.let { afterResult(it) } },
        speaker = if (voiceOn) container.voice else null,
    )
}

/** 과제 화면 그리기(상태만 받는다). 시각·타이밍 로직은 [TaskScreen]에 있다. */
@Composable
internal fun TaskFrame(
    header: String,
    taskId: TaskId,
    phase: Phase,
    current: TrialSpec?,
    onQuit: () -> Unit,
    onStartBlock: (Block) -> Unit,
    onRespond: (ResponseKey) -> Unit,
    speaker: Speaker? = null,
) {
    // 음성 안내: 설명 단계에서만 읽고, 그 밖의 단계(자극·피드백)에서는 즉시 멈춘다.
    LaunchedEffect(phase, speaker) {
        if (speaker == null) return@LaunchedEffect
        if (phase is Phase.Intro) {
            val title = if (phase.block == Block.PRACTICE) "연습" else UiText.taskLabel(taskId)
            speaker.speak("$title. ${introText(taskId, phase.block)} 준비되면 시작을 눌러 주세요.")
        } else {
            speaker.stop()
        }
    }
    // 틀(여백·간격·머리 글자·시작 버튼)만 배치 값으로. 자극 크기·응답 버튼 영역은 연구 프로토콜 값이라 아래 상수로 고정한다.
    val l = pageLayout("task")
    Column(
        modifier = Modifier.fillMaxSize().padding(start = l.padStart.dp, end = l.padEnd.dp, top = l.padTop.dp, bottom = l.padBottom.dp),
        verticalArrangement = Arrangement.spacedBy(l.gap.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(header, style = MaterialTheme.typography.headlineMedium.copy(fontSize = l.titleFontSize.sp, lineHeight = (l.titleFontSize * 1.23f).sp), modifier = Modifier.weight(1f))
            SecondaryButton(UiText.QUIT, onClick = onQuit)
        }
        when (phase) {
            is Phase.Intro -> SectionCard(modifier = Modifier.weight(1f)) {
                Heading(if (phase.block == Block.PRACTICE) "연습" else UiText.taskLabel(taskId))
                Body(introText(taskId, phase.block))
                if (taskId == TaskId.GO_NO_GO) GoNoGoExamples(Modifier.weight(1f).fillMaxWidth())
                PrimaryButton("시작", onClick = { onStartBlock(phase.block) }, modifier = Modifier.fillMaxWidth(), minHeight = l.ctaHeight.dp)
            }
            else -> {
                StimulusArea(phase = phase, current = current, modifier = Modifier.weight(1f))
                ResponseButtons(
                    taskId = taskId,
                    spec = current,
                    enabled = phase is Phase.Showing || phase is Phase.Hidden,
                    onRespond = onRespond,
                )
                // 응답 버튼을 화면 맨 아래(하단 공조 바 바로 위)에서 띄운다.
                Spacer(Modifier.height(ResponseButtonLift))
            }
        }
    }
}

private fun introText(task: TaskId, block: Block): String = when (task) {
    TaskId.VISUAL_ATTENTION -> if (block == Block.PRACTICE) {
        "화면 가운데 화살표가 나오면, 화살표가 가리키는 쪽의 큰 버튼을 눌러 주세요. 먼저 연습해 볼게요."
    } else {
        "이제 이어서 해요. 정확하게, 할 수 있는 만큼 빠르게 눌러 주세요."
    }
    TaskId.GO_NO_GO -> if (block == Block.PRACTICE) {
        "동그라미가 나오면 버튼을 누르고, 빨간 가위표(✕)가 나오면 누르지 않아요. 먼저 연습해 볼게요."
    } else {
        "이제 이어서 해요. 동그라미에만 눌러 주세요."
    }
    TaskId.TASK_SWITCHING -> when (block) {
        Block.PRACTICE -> "숫자가 나오면 위에 적힌 규칙대로 답해요.\n홀짝 규칙: 홀수는 왼쪽, 짝수는 오른쪽\n크기 규칙: 5보다 작으면 왼쪽, 크면 오른쪽\n먼저 연습해 볼게요."
        Block.PURE_PARITY -> "이번에는 홀짝 규칙만 나와요."
        Block.PURE_MAGNITUDE -> "이번에는 크기 규칙만 나와요."
        else -> "이제 두 규칙이 번갈아 나와요. 위에 적힌 규칙 이름을 잘 보고 답해 주세요."
    }
}

@Composable
private fun ruleColor(rule: SwitchRule): Color = if (rule == SwitchRule.PARITY) NeulbomColors.Calm else NeulbomColors.Notice

@Composable
private fun StimulusArea(phase: Phase, current: TrialSpec?, modifier: Modifier) {
    val rule = (current?.stimulus as? Stimulus.Digit)?.rule
    Box(
        modifier = modifier
            .fillMaxWidth()
            .let { m -> if (rule != null) m.border(BorderStroke(6.dp, ruleColor(rule)), RoundedCornerShape(24.dp)) else m },
        contentAlignment = Alignment.Center,
    ) {
        if (rule != null) {
            Text(
                "${rule.label} 규칙",
                style = MaterialTheme.typography.headlineMedium,
                color = ruleColor(rule),
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            )
        }
        when (phase) {
            is Phase.Showing -> StimulusGlyph(phase.spec.stimulus)
            is Phase.Feedback -> Text(phase.text, style = MaterialTheme.typography.headlineLarge, color = if (phase.ok) NeulbomColors.Ok else NeulbomColors.Notice)
            is Phase.Hidden -> Unit
            else -> Text("+", fontSize = 80.sp, color = NeulbomColors.TextSecondary)
        }
    }
}

/** 응답 버튼 아래 여백. 버튼 위치도 측정 조건이라 과제 사이에 같게 둔다. */
internal val ResponseButtonLift = 64.dp

/** 화살표 자극 크기(시각 주의 과제 v3, 자극 세트 arrows-lr v2). 측정 조건이므로 프로토콜 버전 없이 바꾸지 않는다. */
internal val ArrowStimulusSize = 512.dp

@Composable
private fun StimulusGlyph(stimulus: Stimulus) {
    when (stimulus) {
        is Stimulus.Arrow -> ArrowStimulus(left = stimulus.side == ResponseKey.LEFT)
        is Stimulus.Digit -> GlyphStimulus(stimulus.value.toString(), "숫자 ${stimulus.value}")
        is Stimulus.GoNoGo -> GoNoGoStimulus(isGo = stimulus.isGo)
    }
}

/** 좌우가 정확히 같은 모양이 되도록 왼쪽 화살표 하나를 반전해 쓴다. */
@Composable
internal fun ArrowStimulus(left: Boolean) {
    Image(
        painter = painterResource(R.drawable.stimulus_arrow),
        contentDescription = if (left) "왼쪽 화살표" else "오른쪽 화살표",
        colorFilter = ColorFilter.tint(NeulbomColors.Text),
        modifier = Modifier.size(ArrowStimulusSize).graphicsLayer { scaleX = if (left) 1f else -1f },
    )
}

/**
 * Go/No-Go 자극(과제 v3, 자극 세트 circle-cross v2): 512dp 캔버스에 벡터로 그린다.
 * 누르기 = 채운 동그라미(본문 글자색), 멈춤 = 굵은 빨간 가위표. 모양이 달라 색 없이도 구분된다.
 */
@Composable
internal fun GoNoGoStimulus(isGo: Boolean, canvasSize: Dp = GoNoGoStimulusCanvas) {
    val stop = LocalNeulbomPalette.current.stimulusStop
    val go = NeulbomColors.Text
    Canvas(
        Modifier.size(canvasSize).semantics { contentDescription = if (isGo) "누르기 신호" else "멈춤 신호" },
    ) {
        val s = size.minDimension
        if (isGo) {
            drawCircle(color = go, radius = s * 0.36f, center = center)
        } else {
            val a = s * 0.18f
            val b = s * 0.82f
            val stroke = s * 0.14f
            drawLine(stop, Offset(a, a), Offset(b, b), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(stop, Offset(b, a), Offset(a, b), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/**
 * 설명 화면의 큰 예시: 시작 직전에 "이건 누르기 / 이건 누르지 않기"를 한 번 더 보여 준다.
 * 측정에 쓰지 않는 안내라 크기는 화면에 맞추고, 모양·색은 실제 자극과 같다.
 */
@Composable
private fun GoNoGoExamples(modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        listOf(true to "누르기", false to "누르지 않기").forEach { (isGo, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GoNoGoStimulus(isGo = isGo, canvasSize = 260.dp)
                Text(label, style = MaterialTheme.typography.headlineMedium, color = if (isGo) NeulbomColors.Text else LocalNeulbomPalette.current.stimulusStop)
            }
        }
    }
}

/** Go/No-Go 자극 캔버스 크기. 측정 조건이므로 과제 버전 없이 바꾸지 않는다. */
internal val GoNoGoStimulusCanvas = 512.dp

@Composable
private fun GlyphStimulus(text: String, description: String) {
    Text(
        text,
        fontSize = 160.sp,
        fontWeight = FontWeight.Bold,
        color = NeulbomColors.Text,
        modifier = Modifier.semantics { contentDescription = description },
    )
}

@Composable
private fun ResponseButtons(taskId: TaskId, spec: TrialSpec?, enabled: Boolean, onRespond: (ResponseKey) -> Unit) {
    val haptic = LocalHapticFeedback.current
    // 반응을 먼저 기록하고, 그 다음에 가벼운 진동으로 "눌렸다"는 느낌을 준다(측정 경로를 늦추지 않음).
    val respond: (ResponseKey) -> Unit = { k ->
        onRespond(k)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    if (taskId == TaskId.GO_NO_GO) {
        ResponseButton("누르기", enabled, { respond(ResponseKey.TAP) }, Modifier.fillMaxWidth().height(150.dp))
        return
    }
    val rule = (spec?.stimulus as? Stimulus.Digit)?.rule
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.fillMaxWidth()) {
        ResponseButton(rule?.leftLabel ?: "왼쪽", enabled, { respond(ResponseKey.LEFT) }, Modifier.weight(1f).height(150.dp))
        ResponseButton(rule?.rightLabel ?: "오른쪽", enabled, { respond(ResponseKey.RIGHT) }, Modifier.weight(1f).height(150.dp))
    }
}

/**
 * 과제 응답 버튼(프로토콜 v2, [com.neulbom.drive.domain.cognitive.ResponseCapture.TOUCH_DOWN]):
 * 손가락이 **닿는 순간** 한 번 기록하고, 뗄 때는 아무것도 하지 않는다. 스크린리더의 두 번 탭도 한 번 기록.
 */
@Composable
private fun ResponseButton(text: String, enabled: Boolean, onPress: () -> Unit, modifier: Modifier) {
    val p = LocalNeulbomPalette.current
    val press by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(Dimens.CardRadius))
            .background(if (enabled) p.ctaBackground else p.disabled)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (enabled) onClick { press(); true } else disabled()
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    pressed = true
                    press() // 닿는 순간 = 반응 시각
                    waitForUpOrCancellation()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.headlineMedium, color = if (enabled) p.ctaForeground else p.textSecondary)
    }
}

/** 과제 사이 쉬는 화면. 다음 과제는 사용자가 누를 때 시작한다. 안전 감시는 계속된다. */
@Composable
fun BetweenScreen(next: RunnerState.Between, onNext: () -> Unit, onQuit: () -> Unit) {
    BackHandler(onBack = onQuit)
    val l = pageLayout("between")
    CenterPage(layout = l) {
        Caption("${next.nextTaskIndex + 1} / 3")
        Heading("잘하셨어요")
        Body("다음은 '${UiText.taskLabel(next.nextTaskId)}'이에요. 준비되면 시작해 주세요.")
        PrimaryButton("다음 활동 시작", onClick = onNext, modifier = Modifier.fillMaxWidth(), minHeight = l.ctaHeight.dp)
        SecondaryButton(UiText.QUIT, onClick = onQuit, modifier = Modifier.widthIn(min = l.ctaMinWidth.dp))
    }
}

@Composable
fun SavingScreen() {
    CenterPage(layout = pageLayout("saving")) { Heading("기록을 보관하는 중이에요") }
}
