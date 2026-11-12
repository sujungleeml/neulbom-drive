package com.neulbom.drive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.neulbom.drive.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 디자인 토큰 한 벌(Quiet Luxury × Warm Mobility × Brain Wellness).
 * 값은 docs/design/home-dashboard-mockup.html 의 :root 변수와 같다.
 */
data class NeulbomPalette(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val primaryLight: Color,
    val wood: Color,
    val woodLight: Color,
    val text: Color,
    val textSecondary: Color,
    val border: Color,
    val success: Color,
    val caution: Color,
    val danger: Color,
    val ctaBackground: Color,
    val ctaForeground: Color,
    val disabled: Color,
    /** Go/No-Go 멈춤(✕) 자극 색. 측정 조건이므로 과제 버전 없이 바꾸지 않는다. */
    val stimulusStop: Color,
)

object NeulbomColors {
    /** 주간: 따뜻한 아이보리 바탕, 네이비 버튼. */
    val day = NeulbomPalette(
        background = Color(0xFFF7F5F0),
        surface = Color(0xFFFFFFFF),
        primary = Color(0xFF182B45),
        primaryLight = Color(0xFF344B65),
        wood = Color(0xFFDCC5A5),
        woodLight = Color(0xFFEFE5D7),
        text = Color(0xFF263447),
        textSecondary = Color(0xFF647184),
        border = Color(0xFFE4E4DF),
        success = Color(0xFF52796F),
        caution = Color(0xFFA67840),
        danger = Color(0xFF9A4A3F),
        ctaBackground = Color(0xFF182B45),
        ctaForeground = Color(0xFFFFFFFF),
        disabled = Color(0xFFCFCDC6),
        stimulusStop = Color(0xFFC62828), // 바탕 대비 5.2:1
    )

    /** 야간: 깊은 네이비 바탕, 우드 버튼. 눈부심을 줄인다. */
    val night = NeulbomPalette(
        background = Color(0xFF0F1B2C),
        surface = Color(0xFF16263A),
        primary = Color(0xFFEFE9DF),
        primaryLight = Color(0xFFC9D2DD),
        wood = Color(0xFFC9AE86),
        woodLight = Color(0xFF233348),
        text = Color(0xFFE6E2DA),
        textSecondary = Color(0xFF98A5B6),
        border = Color(0xFF253850),
        success = Color(0xFF86AFA3),
        caution = Color(0xFFCFA06A),
        danger = Color(0xFFE0907F),
        ctaBackground = Color(0xFFDCC5A5),
        ctaForeground = Color(0xFF14243A),
        disabled = Color(0xFF34465E),
        stimulusStop = Color(0xFFFF6B6B), // 바탕 대비 6.2:1
    )

    // 기존 화면용 이름(Task 8에서 화면을 다시 만들 때 정리). 현재 테마의 팔레트를 따른다.
    val Background: Color @Composable @ReadOnlyComposable get() = LocalNeulbomPalette.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalNeulbomPalette.current.surface
    val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalNeulbomPalette.current.woodLight
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalNeulbomPalette.current.ctaBackground
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalNeulbomPalette.current.ctaForeground
    val Text: Color @Composable @ReadOnlyComposable get() = LocalNeulbomPalette.current.text
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalNeulbomPalette.current.textSecondary
    val Calm: Color @Composable @ReadOnlyComposable get() = LocalNeulbomPalette.current.primaryLight
    val Notice: Color @Composable @ReadOnlyComposable get() = LocalNeulbomPalette.current.caution
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalNeulbomPalette.current.danger
    val Ok: Color @Composable @ReadOnlyComposable get() = LocalNeulbomPalette.current.success
    val Disabled: Color @Composable @ReadOnlyComposable get() = LocalNeulbomPalette.current.disabled
}

val LocalNeulbomPalette = staticCompositionLocalOf { NeulbomColors.day }

/** 주간·야간 에셋을 고를 때 쓴다. */
val NeulbomPalette.isNight: Boolean get() = this == NeulbomColors.night

/** 터치 영역 최소 크기(T08). 일반 권장 48dp보다 크게 잡는다. */
object Dimens {
    val MinTouch = 72.dp
    val BigTouch = 112.dp
    val Gutter = 32.dp
    val CardRadius = 20.dp
}

private fun NeulbomPalette.toScheme(night: Boolean) = if (night) {
    darkColorScheme(
        primary = ctaBackground, onPrimary = ctaForeground,
        secondary = wood, onSecondary = ctaForeground,
        background = background, onBackground = text,
        surface = surface, onSurface = text,
        surfaceVariant = woodLight, onSurfaceVariant = textSecondary,
        outline = border, error = danger,
    )
} else {
    lightColorScheme(
        primary = ctaBackground, onPrimary = ctaForeground,
        secondary = wood, onSecondary = primary,
        background = background, onBackground = text,
        surface = surface, onSurface = text,
        surfaceVariant = woodLight, onSurfaceVariant = textSecondary,
        outline = border, error = danger,
    )
}

/** Pretendard(OFL 1.1, docs/licenses/Pretendard.md). 없는 글리프는 시스템 Noto Sans KR로 대체된다. */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

// 기준: Pleos Connect 앱 영역 1680×1210dp(mdpi). 가장 작은 글자도 24sp 아래로 내리지 않는다.
// displayLarge(과제 자극)는 측정 조건이라 별도 라운드에서 다룬다.
private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Pretendard, fontSize = 96.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = Pretendard, fontSize = 56.sp, fontWeight = FontWeight.Bold, lineHeight = 68.sp),
    headlineMedium = TextStyle(fontFamily = Pretendard, fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 64.sp),
    titleLarge = TextStyle(fontFamily = Pretendard, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, lineHeight = 46.sp),
    titleMedium = TextStyle(fontFamily = Pretendard, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 42.sp),
    bodyLarge = TextStyle(fontFamily = Pretendard, fontSize = 30.sp, lineHeight = 42.sp),
    bodyMedium = TextStyle(fontFamily = Pretendard, fontSize = 26.sp, lineHeight = 36.sp),
    labelLarge = TextStyle(fontFamily = Pretendard, fontSize = 32.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontFamily = Pretendard, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
)

/** night 기본값은 시스템(차량) 야간 모드를 따른다. */
@Composable
fun NeulbomTheme(night: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (night) NeulbomColors.night else NeulbomColors.day
    CompositionLocalProvider(LocalNeulbomPalette provides palette) {
        MaterialTheme(colorScheme = palette.toScheme(night), typography = typography) {
            // 색을 지정하지 않은 글자도 바탕과 대비가 맞도록 기본 글자색을 둔다.
            CompositionLocalProvider(LocalContentColor provides palette.text, content = content)
        }
    }
}
