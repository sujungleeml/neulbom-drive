package com.neulbom.drive.ui.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neulbom.drive.di.AppContainer
import com.neulbom.drive.domain.consent.ConsentType
import com.neulbom.drive.presentation.UiText
import com.neulbom.drive.ui.components.BigButton
import com.neulbom.drive.ui.components.Body
import com.neulbom.drive.ui.components.Caption
import com.neulbom.drive.ui.components.Disclaimer
import com.neulbom.drive.ui.components.Heading
import com.neulbom.drive.ui.components.SectionCard
import com.neulbom.drive.ui.components.ToggleRow
import com.neulbom.drive.ui.theme.Dimens
import com.neulbom.drive.ui.theme.NeulbomColors
import kotlinx.coroutines.launch

/**
 * CAR-01 온보딩: 소개 → 목적별 개별 동의(기본 OFF) → 샘플 조작 튜토리얼(기록하지 않음) → 개인 기준 안내.
 */
@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val consents by container.consents.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var tutorialTapped by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Caption("${step + 1} / 4")
        when (step) {
            0 -> {
                Heading(UiText.MISSION)
                Body("늘봄 Drive는 내 운전 습관과, 주차 중 약 3분 동안 하는 주의·반응 점검 기록을 내가 살펴보도록 돕는 서비스예요.")
                SectionCard {
                    Body("• 운전 중에는 알림·점검을 하지 않아요")
                    Body("• 동의한 기록만 보관하고, 위치·경로·목적지는 받지 않아요")
                    Body("• 가족 공유는 내가 원할 때만 해요")
                    Body("• 운전을 계속할지 판단하거나 권하지 않아요")
                }
                Disclaimer()
            }
            1 -> {
                Heading("무엇을 허락할지 하나씩 고르세요")
                Caption("모두 꺼진 상태로 시작해요. 나중에 늘봄 웹의 설정에서 언제든 바꿀 수 있어요.")
                ConsentType.entries.forEach { type ->
                    SectionCard {
                        ToggleRow(
                            title = UiText.consentTitle(type),
                            checked = consents.isGranted(type),
                            onChange = { granted -> scope.launch { container.consents.update(type, granted) } },
                        )
                        Caption(UiText.consentDetail(type, settings.retentionDays))
                        Text(UiText.withoutConsent(type), style = MaterialTheme.typography.bodyMedium, color = NeulbomColors.Calm)
                    }
                }
            }
            2 -> {
                Heading("연습해 볼까요?")
                Body("큰 버튼을 하나 눌러 보세요. 이 연습은 기록하지 않아요.")
                listOf("왼쪽", "오른쪽").chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { fav ->
                            BigButton(fav, onClick = { tutorialTapped = fav }, modifier = Modifier.weight(1f), minHeight = Dimens.BigTouch)
                        }
                    }
                }
                tutorialTapped?.let { Body("잘하셨어요! '$it'을(를) 고르셨어요. 실제 화면에서도 이렇게 누르면 돼요.", color = NeulbomColors.Ok) }
            }
            else -> {
                Heading("나만의 기준을 만들어요")
                Body("처음 약 4주 동안 평소처럼 사용하시면 나의 평소 사용 방식이 만들어져요. 그 전에는 비교나 권유를 하지 않아요.")
                Body("주차 후 점검은 직접 시작하실 때만, 안전하게 주차된 것이 확인될 때만 진행돼요.")
                Disclaimer()
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            if (step > 0) BigButton("이전", onClick = { step-- }, primary = false, modifier = Modifier.weight(1f))
            BigButton(
                if (step < 3) "다음" else "시작하기",
                onClick = {
                    if (step < 3) {
                        step++
                    } else {
                        container.settings.update { it.copy(onboardingDone = true) }
                        onDone()
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
