package com.neulbom.drive.domain.driving

import kotlin.math.roundToInt

/**
 * 주행 리포트 문구 템플릿(명세 v0.6 02 §5). 리포트 문장은 이 템플릿으로만 만든다(자유 생성 금지).
 * 관측값과 방향만 말하고, 좋고 나쁨·원인·권고를 말하지 않는다.
 */
object DrivingCopy {
    val METRIC_TEMPLATE = mapOf(
        "driving_days" to "METRIC_DRIVING_DAYS",
        "duration" to "METRIC_DURATION",
        "distance" to "METRIC_DISTANCE",
        "harsh_decel_per_100km" to "METRIC_HARSH_DECEL",
        "night_share" to "METRIC_NIGHT_SHARE",
    )

    private val NOTICES = mapOf(
        "COLLECTING" to "아직 이번 달 주행 기록이 모이지 않았어요.",
        "COMPARISON_NOT_YET" to "지난달과 비교하려면 두 달 모두 8일·100 km 이상의 기록이 필요해요.",
        "QUALITY_LIMITED" to "신호 구성이나 계산 방식이 달라져 지난달과 직접 비교하지 않았어요.",
        "LIMITED_SESSIONS" to "일부 주행은 신호가 끊겨 참고용으로만 포함했어요.",
        "DECEL_IS_RECORD" to "급감속 기록은 순간적으로 속도가 크게 줄어든 횟수예요. 원인은 판단하지 않아요.",
        "EXTERNAL_CONDITIONS_NOT_ADJUSTED" to "도로·교통·날씨 같은 바깥 상황은 반영하지 못해요.",
        "MOCK_DATA" to "모의 주행 기록으로 만든 화면이에요. 실제 차량 데이터가 아니에요.",
        "UNAVAILABLE_SPEED" to "이 차량에서는 속도 정보를 받을 수 없어 표시하지 않아요.",
        "UNAVAILABLE_DECEL_SIGNAL" to "이 차량에서는 감속 정보를 받을 수 없어 표시하지 않아요.",
        "UNAVAILABLE_DECEL_DISTANCE" to "이번 달 50 km 이상 운전하면 급감속 기록을 보여 드려요.",
    )

    private val DIRECTION = mapOf(
        Direction.MORE to "지난달보다 늘었어요.",
        Direction.LESS to "지난달보다 줄었어요.",
        Direction.SIMILAR to "지난달과 비슷해요.",
    )

    val ALL_TEMPLATE_IDS: List<String> = METRIC_TEMPLATE.values.toList() + NOTICES.keys

    fun render(templateId: String, line: MetricLine? = null): String {
        NOTICES[templateId]?.let { return it }
        val v = line?.value ?: return NOTICES.getValue("COLLECTING")
        val body = when (templateId) {
            "METRIC_DRIVING_DAYS" -> "이번 달 ${v.roundToInt()}일, ${(line.extra ?: 0.0).roundToInt()}번 운전했어요."
            "METRIC_DURATION" -> {
                val min = (v / 60).roundToInt()
                "모두 ${min / 60}시간 ${min % 60}분 운전했어요."
            }
            "METRIC_DISTANCE" -> "약 ${"%.1f".format(v)} km를 달렸어요."
            "METRIC_HARSH_DECEL" -> "100 km마다 급감속 기록이 ${"%.1f".format(v)}번 있었어요."
            "METRIC_NIGHT_SHARE" -> "운전 시간 중 ${(v * 100).roundToInt()}%가 저녁 7시~아침 6시였어요."
            else -> error("unknown template: $templateId")
        }
        return line.direction?.let { "$body ${DIRECTION.getValue(it)}" } ?: body
    }
}
