package com.neulbom.drive.ui.layout

import org.json.JSONObject

/**
 * 대기(IDLE) 화면에서 편집할 수 있는 배치 값. 기본값은 `assets/layout/idle.json`(Visual Layout Studio가 저장하는 파일)이고,
 * 파일에 없는 키는 아래 기본값을 쓴다. 단위는 dp·sp. 점검 기능·데이터 로직과는 무관한 **표시 값만** 담는다.
 */
data class IdleLayout(
    /** 실제 가용 영역(앱 창 − 시스템 바) 안쪽에 더 둘 여백. */
    val safeTop: Float = 0f,
    val safeBottom: Float = 24f,
    val safeStart: Float = 32f,
    val safeEnd: Float = 0f,
    val brand: Brand = Brand(),
    val main: Main = Main(),
) {
    data class Brand(
        val visible: Boolean = true,
        /** 바꾼 일러스트(assets/layout 기준 경로, 예: images/idle_day/…png). 비면 앱에 들어 있는 기본 그림. */
        val image: String = "",
        /** 가로 비율(0.2~0.6) */
        val widthFraction: Float = 0.4f,
        val cornerRadius: Float = 20f,
        /** crop: 영역을 채우고 넘치는 부분을 자름 / fit: 그림 전체가 보이게 */
        val imageFit: String = "crop",
        /** 그림 기준점(0=왼쪽·위, 1=오른쪽·아래). crop일 때 어느 부분을 남길지. */
        val imageAlignX: Float = 0.5f,
        val imageAlignY: Float = 0f,
        val imageScale: Float = 1f,
        val missionVisible: Boolean = true,
        val missionFontSize: Float = 36f,
        val missionPaddingX: Float = 40f,
        val missionPaddingBottom: Float = 40f,
        /** 문장 뒤 반투명 바탕(그림이 문장 자리를 덮을 때 가독성). */
        val missionBackdrop: Boolean = true,
    )

    data class Main(
        val paddingStart: Float = 64f,
        val paddingEnd: Float = 64f,
        val paddingTop: Float = 32f,
        val gap: Float = 20f,
        val titleFontSize: Float = 52f,
        /** bottom: 행동 버튼을 아래에 / afterContent: 내용 바로 아래에 */
        val actionsPlacement: String = "bottom",
        /** 행동 버튼 아래 추가 간격(버튼만 위로 올린다. 브랜드 패널은 그대로). */
        val actionsBottomOffset: Float = 0f,
        val ctaHeight: Float = 112f,
        val ctaGap: Float = 16f,
        val primaryWeight: Float = 2f,
        val secondaryWeight: Float = 1f,
    )

    companion object {
        /** 이미지 경로는 images/ 아래 파일 이름만 허용(경로 조작 방지). 아니면 기본 그림. */
        fun safeImagePath(p: String): String = if (Regex("^images/[a-z_]+/[A-Za-z0-9._-]+\\.(png|jpg|jpeg|webp)$").matches(p)) p else ""

        /** 고령 운전자용 최소값(편집기와 같은 기준). 이보다 작게 들어오면 최소값으로 올린다. */
        const val MIN_TOUCH = 72f
        const val MIN_TITLE = 40f
        const val MIN_MISSION = 28f

        fun parse(o: JSONObject?): IdleLayout {
            if (o == null) return IdleLayout()
            val d = IdleLayout()
            val b = o.optJSONObject("brand")
            val m = o.optJSONObject("main")
            fun JSONObject?.f(k: String, def: Float) = this?.optDouble(k, def.toDouble())?.toFloat() ?: def
            fun JSONObject?.s(k: String, def: String) = this?.optString(k, def) ?: def
            fun JSONObject?.bool(k: String, def: Boolean) = this?.optBoolean(k, def) ?: def
            return IdleLayout(
                safeTop = o.f("safeTop", d.safeTop).coerceIn(0f, 200f),
                safeBottom = o.f("safeBottom", d.safeBottom).coerceIn(0f, 200f),
                safeStart = o.f("safeStart", d.safeStart).coerceIn(0f, 200f),
                safeEnd = o.f("safeEnd", d.safeEnd).coerceIn(0f, 200f),
                brand = Brand(
                    visible = b.bool("visible", d.brand.visible),
                    image = safeImagePath(b.s("image", "")),
                    widthFraction = b.f("widthFraction", d.brand.widthFraction).coerceIn(0.2f, 0.6f),
                    cornerRadius = b.f("cornerRadius", d.brand.cornerRadius).coerceIn(0f, 60f),
                    imageFit = b.s("imageFit", d.brand.imageFit).takeIf { it in listOf("crop", "fit") } ?: d.brand.imageFit,
                    imageAlignX = b.f("imageAlignX", d.brand.imageAlignX).coerceIn(0f, 1f),
                    imageAlignY = b.f("imageAlignY", d.brand.imageAlignY).coerceIn(0f, 1f),
                    imageScale = b.f("imageScale", d.brand.imageScale).coerceIn(1f, 3f),
                    missionVisible = b.bool("missionVisible", d.brand.missionVisible),
                    missionFontSize = b.f("missionFontSize", d.brand.missionFontSize).coerceIn(MIN_MISSION, 64f),
                    missionPaddingX = b.f("missionPaddingX", d.brand.missionPaddingX).coerceIn(0f, 200f),
                    missionPaddingBottom = b.f("missionPaddingBottom", d.brand.missionPaddingBottom).coerceIn(0f, 400f),
                    missionBackdrop = b.bool("missionBackdrop", d.brand.missionBackdrop),
                ),
                main = Main(
                    paddingStart = m.f("paddingStart", d.main.paddingStart).coerceIn(0f, 200f),
                    paddingEnd = m.f("paddingEnd", d.main.paddingEnd).coerceIn(0f, 200f),
                    paddingTop = m.f("paddingTop", d.main.paddingTop).coerceIn(0f, 300f),
                    gap = m.f("gap", d.main.gap).coerceIn(0f, 80f),
                    titleFontSize = m.f("titleFontSize", d.main.titleFontSize).coerceIn(MIN_TITLE, 72f),
                    actionsPlacement = m.s("actionsPlacement", d.main.actionsPlacement).takeIf { it in listOf("bottom", "afterContent") } ?: d.main.actionsPlacement,
                    actionsBottomOffset = m.f("actionsBottomOffset", d.main.actionsBottomOffset).coerceIn(0f, 400f),
                    ctaHeight = m.f("ctaHeight", d.main.ctaHeight).coerceIn(MIN_TOUCH, 200f),
                    ctaGap = m.f("ctaGap", d.main.ctaGap).coerceIn(8f, 80f),
                    primaryWeight = m.f("primaryWeight", d.main.primaryWeight).coerceIn(0.5f, 4f),
                    secondaryWeight = m.f("secondaryWeight", d.main.secondaryWeight).coerceIn(0.5f, 4f),
                ),
            )
        }
    }
}

/**
 * 화면별 주간·야간 배치. 파일 형식(화면마다 하나): `{"schemaVersion":1,"screen":"idle","revision":n,"day":{…},"night":{…}}`
 * revision은 모든 화면 파일 판의 합(편집기가 어느 화면이든 저장하면 바뀌어 앱이 다시 그린다).
 */
data class ScreenLayouts(
    val revision: Long = 0,
    val idleDay: IdleLayout = IdleLayout(),
    val idleNight: IdleLayout = IdleLayout(),
    val pages: Map<String, Pair<PageLayout, PageLayout>> = emptyMap(),
) {
    fun idle(night: Boolean) = if (night) idleNight else idleDay

    fun page(screen: String, night: Boolean): PageLayout =
        pages[screen]?.let { if (night) it.second else it.first } ?: PageLayout.default(screen)

    companion object {
        const val SCHEMA_VERSION = 1
        val SCREENS = listOf("idle") + PageLayout.DEFAULTS.keys

        private fun checked(text: String): JSONObject {
            val o = JSONObject(text)
            require(o.optInt("schemaVersion", SCHEMA_VERSION) == SCHEMA_VERSION) { "unsupported layout schema" }
            return o
        }

        fun parseIdle(text: String): ScreenLayouts {
            val o = checked(text)
            return ScreenLayouts(
                revision = o.optLong("revision", 0),
                idleDay = IdleLayout.parse(o.optJSONObject("day")),
                idleNight = IdleLayout.parse(o.optJSONObject("night") ?: o.optJSONObject("day")),
            )
        }

        fun parsePage(screen: String, text: String): Pair<PageLayout, PageLayout> {
            val o = checked(text)
            val d = PageLayout.default(screen)
            return d.parse(o.optJSONObject("day")) to d.parse(o.optJSONObject("night") ?: o.optJSONObject("day"))
        }

        /** 화면 이름 → 파일 내용(없는 화면은 코드 기본값). */
        fun parseAll(files: Map<String, String>): ScreenLayouts {
            val idle = files["idle"]?.let(::parseIdle) ?: ScreenLayouts()
            val pages = files.filterKeys { it != "idle" && it in PageLayout.DEFAULTS }.mapValues { (k, v) -> parsePage(k, v) }
            val revision = files.values.sumOf { runCatching { JSONObject(it).optLong("revision", 0) }.getOrDefault(0L) }
            return idle.copy(revision = revision, pages = pages)
        }
    }
}
