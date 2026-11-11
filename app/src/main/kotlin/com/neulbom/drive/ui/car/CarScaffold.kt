package com.neulbom.drive.ui.car

import com.neulbom.drive.ui.layout.PageLayout
import com.neulbom.drive.ui.layout.LocalHeadingSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.BiasAlignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.components.Heading
import com.neulbom.drive.ui.components.SecondaryButton
import com.neulbom.drive.ui.theme.Dimens

/**
 * 차량 화면 공통 틀: 위쪽에 "← 처음으로"(있을 때), 가운데 내용, 아래쪽에 행동 버튼.
 * 시스템 뒤로 가기도 같은 동작을 한다 — 어디서든 돌아가는 길이 한 가지로 보이게.
 */
@Composable
fun CarPage(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    bottomPadding: Dp = Dimens.Gutter,
    /** Visual Layout Studio 배치 값(있으면 여백·간격·제목 크기·버튼 아래 간격을 이 값으로). */
    layout: PageLayout? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onBack != null) BackHandler(onBack = onBack)
    val pad = if (layout != null) Modifier.padding(start = layout.padStart.dp, end = layout.padEnd.dp, top = layout.padTop.dp, bottom = layout.padBottom.dp)
    else Modifier.padding(start = Dimens.Gutter * 2, end = Dimens.Gutter * 2, top = Dimens.Gutter, bottom = bottomPadding)
    Column(
        modifier = Modifier.fillMaxSize().then(pad),
        verticalArrangement = Arrangement.spacedBy((layout?.gap ?: 20f).dp),
    ) {
        if (onBack != null) SecondaryButton("← ${UiText.BACK}", onClick = onBack)
        CompositionLocalProvider(LocalHeadingSize provides layout?.titleFontSize?.sp) { Heading(title) }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        if (actions != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((layout?.ctaGap ?: 16f).dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = (layout?.actionsBottomOffset ?: 0f).dp),
                content = actions,
            )
        }
    }
}

/** 한 가지 소식만 크게 보여 주는 가운데 정렬 화면(완료·중단·주행 등). layout이 있으면 여백·최대 너비·세로 위치·간격·제목 크기를 그 값으로. */
@Composable
fun CenterPage(maxWidth: Dp = 1040.dp, layout: PageLayout? = null, content: @Composable ColumnScope.() -> Unit) {
    val pad = if (layout != null) Modifier.padding(start = layout.padStart.dp, end = layout.padEnd.dp, top = layout.padTop.dp, bottom = layout.padBottom.dp)
    else Modifier.padding(Dimens.Gutter)
    Box(modifier = Modifier.fillMaxSize().then(pad), contentAlignment = BiasAlignment(0f, layout?.verticalBias ?: 0f)) {
        CompositionLocalProvider(LocalHeadingSize provides layout?.titleFontSize?.sp) {
            Column(
                modifier = Modifier.widthIn(max = layout?.maxWidth?.dp ?: maxWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((layout?.gap ?: 24f).dp),
                content = content,
            )
        }
    }
}
