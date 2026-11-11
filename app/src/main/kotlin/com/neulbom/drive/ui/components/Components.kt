package com.neulbom.drive.ui.components

import com.neulbom.drive.ui.layout.LocalHeadingSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.theme.Dimens
import com.neulbom.drive.ui.theme.LocalNeulbomPalette
import com.neulbom.drive.ui.theme.NeulbomColors

enum class ChipTone { NEUTRAL, OK, CAUTION }

private val ButtonShape = RoundedCornerShape(Dimens.CardRadius)
private val ButtonPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp)

/** 누르고 있는 동안 살짝 작아지는 느낌(120ms). 클릭 처리 시점은 바꾸지 않는다. */
@Composable
private fun pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")
    return Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** 주 행동 버튼: 주간 네이비, 야간 우드. 최소 높이 72dp. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, minHeight: Dp = Dimens.MinTouch) {
    val p = LocalNeulbomPalette.current
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        interactionSource = source,
        modifier = modifier.then(pressScale(source)).heightIn(min = minHeight),
        contentPadding = ButtonPadding,
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = p.ctaBackground,
            contentColor = p.ctaForeground,
            disabledContainerColor = p.disabled,
            disabledContentColor = p.textSecondary,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

/** 보조 버튼: 테두리만, 바탕 없음. 최소 높이 72dp. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, minHeight: Dp = Dimens.MinTouch) {
    val p = LocalNeulbomPalette.current
    val source = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        interactionSource = source,
        modifier = modifier.then(pressScale(source)).heightIn(min = minHeight),
        contentPadding = ButtonPadding,
        border = BorderStroke(2.dp, if (enabled) p.primary else p.disabled),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = p.primary, disabledContentColor = p.textSecondary),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

/** 이전 이름. Task 8에서 화면을 바꾸며 PrimaryButton/SecondaryButton으로 옮긴다. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    minHeight: Dp = Dimens.MinTouch,
) {
    if (primary) PrimaryButton(text, onClick, modifier, enabled, minHeight)
    else SecondaryButton(text, onClick, modifier, enabled, minHeight)
}

/** 카드: 모서리 20dp, 얇은 경계선, 그림자 없음. */
@Composable
fun InfoCard(modifier: Modifier = Modifier, borderColor: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalNeulbomPalette.current
    val shape = RoundedCornerShape(Dimens.CardRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(p.surface, shape)
            .border(if (borderColor != null) 2.dp else 1.dp, borderColor ?: p.border, shape)
            .padding(Dimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, borderColor: Color? = null, content: @Composable ColumnScope.() -> Unit) =
    InfoCard(modifier, borderColor, content)

/** 상태 표시 칩(예: 주차 확인됨). 색만으로 뜻을 전하지 않도록 항상 글자를 함께 쓴다. */
@Composable
fun StatusChip(text: String, tone: ChipTone = ChipTone.NEUTRAL, modifier: Modifier = Modifier) {
    val p = LocalNeulbomPalette.current
    val color = when (tone) {
        ChipTone.NEUTRAL -> p.textSecondary
        ChipTone.OK -> p.success
        ChipTone.CAUTION -> p.caution
    }
    Box(
        modifier = modifier
            .background(p.woodLight, RoundedCornerShape(999.dp))
            .border(1.dp, color, RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier) {
    val size = LocalHeadingSize.current
    val style = MaterialTheme.typography.headlineMedium.let { if (size != null) it.copy(fontSize = size, lineHeight = size * 1.23f) else it }
    Text(text, style = style, modifier = modifier.semantics { heading() })
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = NeulbomColors.Text) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = color, modifier = modifier)
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = NeulbomColors.TextSecondary, modifier = modifier)
}

@Composable
fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .border(2.dp, color, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun SyntheticBadge() = Badge(UiText.SYNTHETIC_BADGE, NeulbomColors.Notice)

/** 큰 터치 영역의 켜기/끄기 행. 스크린리더가 상태를 읽을 수 있도록 stateDescription 제공. */
@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null, enabled: Boolean = true, subtle: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTouch)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                stateDescription = if (checked) "켜짐" else "꺼짐"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // subtle: 보조 설정(예: 대기 화면의 음성 안내)은 제목을 작고 옅게.
            Text(title, style = if (subtle) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium, color = if (subtle) NeulbomColors.TextSecondary else Color.Unspecified)
            if (description != null) Caption(description)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeulbomColors.OnAccent,
                checkedTrackColor = NeulbomColors.Accent,
                uncheckedThumbColor = NeulbomColors.TextSecondary,
                uncheckedTrackColor = NeulbomColors.SurfaceHigh,
            ),
        )
    }
}

@Composable
fun Disclaimer() {
    Caption(UiText.DISCLAIMER, modifier = Modifier.padding(top = 8.dp))
}
