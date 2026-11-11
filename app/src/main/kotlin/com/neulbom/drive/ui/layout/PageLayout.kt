package com.neulbom.drive.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import com.neulbom.drive.ui.theme.LocalNeulbomPalette
import com.neulbom.drive.ui.theme.isNight
import org.json.JSONObject

/**
 * IDLE을 뺀 차량 화면의 공통 배치 값(`assets/layout/<screen>.json`, Visual Layout Studio가 저장).
 * 화면마다 쓰는 항목만 의미가 있다(예: verticalBias는 가운데 정렬 화면, actionsBottomOffset은 위·아래 구성 화면).
 *
 * **과제 화면(task)의 자극 크기·노출 시간·응답 버튼 영역은 여기 없다** — 연구 프로토콜 값이라 코드에 고정하고 편집기에서는 잠근다.
 */
data class PageLayout(
    val padStart: Float,
    val padEnd: Float,
    val padTop: Float,
    val padBottom: Float,
    /** 요소 사이 간격 */
    val gap: Float,
    /** 가운데 정렬 화면의 내용 최대 너비 */
    val maxWidth: Float = 1040f,
    /** 가운데 정렬 화면의 세로 위치(−1 위, 0 가운데, 1 아래) */
    val verticalBias: Float = 0f,
    val titleFontSize: Float = 52f,
    val ctaHeight: Float = 112f,
    val ctaGap: Float = 16f,
    /** 닫기·그만하기처럼 너비가 내용에 맞는 버튼의 최소 너비 */
    val ctaMinWidth: Float = 280f,
    /** 위·아래 구성 화면에서 행동 버튼 아래 추가 간격 */
    val actionsBottomOffset: Float = 0f,
    /** 완료 일러스트 크기 */
    val imageSize: Float = 240f,
    /** 바꾼 일러스트(assets/layout 기준 경로). 비면 앱 기본 그림. */
    val image: String = "",
) {
    fun parse(o: JSONObject?): PageLayout {
        if (o == null) return this
        fun f(k: String, def: Float, lo: Float, hi: Float) = o.optDouble(k, def.toDouble()).toFloat().coerceIn(lo, hi)
        return PageLayout(
            padStart = f("padStart", padStart, 0f, 300f),
            padEnd = f("padEnd", padEnd, 0f, 300f),
            padTop = f("padTop", padTop, 0f, 400f),
            padBottom = f("padBottom", padBottom, 0f, 400f),
            gap = f("gap", gap, 0f, 80f),
            maxWidth = f("maxWidth", maxWidth, 600f, 1680f),
            verticalBias = f("verticalBias", verticalBias, -1f, 1f),
            titleFontSize = f("titleFontSize", titleFontSize, IdleLayout.MIN_TITLE, 80f),
            ctaHeight = f("ctaHeight", ctaHeight, IdleLayout.MIN_TOUCH, 200f),
            ctaGap = f("ctaGap", ctaGap, 8f, 80f),
            ctaMinWidth = f("ctaMinWidth", ctaMinWidth, 200f, 800f),
            actionsBottomOffset = f("actionsBottomOffset", actionsBottomOffset, 0f, 400f),
            imageSize = f("imageSize", imageSize, 120f, 600f),
            image = IdleLayout.safeImagePath(o.optString("image", image)),
        )
    }

    companion object {
        private val CENTER = PageLayout(padStart = 32f, padEnd = 32f, padTop = 32f, padBottom = 32f, gap = 24f)
        private val TOP_BOTTOM = PageLayout(padStart = 64f, padEnd = 64f, padTop = 32f, padBottom = 32f, gap = 20f)

        /** 화면별 코드 기본값(편집 전 화면과 같다). assets 파일과 편집기 기본값이 이 값과 같은지 테스트한다. */
        val DEFAULTS: Map<String, PageLayout> = mapOf(
            "offer" to CENTER.copy(maxWidth = 1280f),
            "guide" to TOP_BOTTOM,
            "between" to CENTER,
            "saving" to CENTER,
            // 닫기 버튼은 지금까지 기본 높이(72dp)였다.
            "completion" to CENTER.copy(ctaHeight = 72f),
            "interrupted" to CENTER.copy(ctaHeight = 72f),
            "driving" to CENTER.copy(titleFontSize = 56f),
            // 과제 화면 틀: 머리 글자·바깥 여백·간격·시작 버튼만. 자극·응답 영역은 잠금.
            "task" to PageLayout(padStart = 32f, padEnd = 32f, padTop = 32f, padBottom = 32f, gap = 16f),
        )

        fun default(screen: String) = DEFAULTS.getValue(screen)
    }
}

/** 지금 그리는 화면의 제목 글자 크기(Heading이 읽는다). null이면 테마 기본. */
val LocalHeadingSize = staticCompositionLocalOf<TextUnit?> { null }

/** 화면 이름으로 지금 모드(주간·야간)의 배치 값을 꺼낸다. */
@Composable
fun pageLayout(screen: String): PageLayout = LocalScreenLayouts.current.page(screen, LocalNeulbomPalette.current.isNight)
