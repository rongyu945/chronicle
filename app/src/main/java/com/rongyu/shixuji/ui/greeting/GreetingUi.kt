package com.rongyu.shixuji.ui.greeting

import androidx.compose.ui.graphics.Color
import com.rongyu.shixuji.data.GreetingEdge
import com.rongyu.shixuji.data.GreetingTimes

/*
 * 「问候时段」编辑器的公共定义（7.5.0 按《欢迎时间功能重构与交互优化方案》拆分）。
 *
 * 这一段里只放三样东西：三个段的元信息、时间格式、段/空档的显示口径。
 * 所有页面（设置页入口行、编辑器、滚轮、预览）都从这里取，避免出现
 * 「入口行写 0–7、编辑器写 00:00–07:00、预览又写 07:00」这种三套口径。
 */

/** 三段辅助色（「墨线分时」：暖赭 / 青瓷 / 黛青）。 */
internal val GreetMorning = Color(0xFFBDA477)
internal val GreetNoon = Color(0xFF88A99A)
internal val GreetNight = Color(0xFF68748A)

/**
 * 一段的展示模型（只读投影，真值仍在 [GreetingTimes]）。
 *
 * @param startEditable / endEditable 只用于界面文案与手势候选，不参与数据校验 ——
 *        校验一律走 [GreetingTimes.updateEdge]。
 */
internal data class GreetingSegment(
    val kind: Int,
    val title: String,
    val sub: String,
    val greeting: String,
    val start: Int,
    val end: Int,
    val color: Color,
    val startEditable: Boolean,
    val endEditable: Boolean,
) {
    val duration: Int get() = end - start
    val startEdge: GreetingEdge get() = GreetingEdge.of(kind, true)
    val endEdge: GreetingEdge get() = GreetingEdge.of(kind, false)
}

internal fun GreetingTimes.segments(): List<GreetingSegment> = listOf(
    GreetingSegment(
        kind = GreetingTimes.GREETING_MORNING,
        title = "早间问候", sub = "晨 · 第一段", greeting = "早安",
        start = morningStart, end = morningEnd, color = GreetMorning,
        startEditable = true, endEditable = true,
    ),
    GreetingSegment(
        kind = GreetingTimes.GREETING_NOON,
        title = "午间问候", sub = "午 · 第二段", greeting = "午安",
        start = noonStart, end = noonEnd, color = GreetNoon,
        startEditable = true, endEditable = true,
    ),
    GreetingSegment(
        kind = GreetingTimes.GREETING_NIGHT,
        title = "夜间问候", sub = "夜 · 第三段", greeting = "晚安",
        start = nightStart, end = nightEnd, color = GreetNight,
        startEditable = true, endEditable = true,
    ),
)

internal fun segmentOf(t: GreetingTimes, kind: Int): GreetingSegment = t.segments()[kind]

/** `24:00` 必须特判：1440 取模会变成 00:00，读起来像「时长 0」。 */
internal fun fmtMin(m: Int): String {
    val v = m.coerceIn(0, GreetingTimes.DAY)
    return if (v == GreetingTimes.DAY) "24:00" else "%02d:%02d".format(v / 60, v % 60)
}

/** 设置行右侧的紧凑写法：整点只写小时（0 / 7 / 24），非整点写 HH:MM。 */
internal fun compactEdge(m: Int): String = if (m % 60 == 0) (m / 60).toString() else fmtMin(m)

/** 设置页「问候时段」行右侧那一串：三段同显。 */
internal fun greetingSummary(t: GreetingTimes): String =
    compactEdge(0) + "–" + compactEdge(t.morningEnd) + " · " +
        compactEdge(t.noonStart) + "–" + compactEdge(t.noonEnd) + " · " +
        compactEdge(t.nightStart) + "–24"

/** 时长的口语写法：420 →「7 小时」，65 →「1 小时 5 分钟」，30 →「30 分钟」。 */
internal fun durationText(minutes: Int): String {
    if (minutes <= 0) return "0 分钟"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m 分钟"
        m == 0 -> "$h 小时"
        else -> "$h 小时 $m 分钟"
    }
}

/** 空档的说法：「无空档」比「0 分钟」清楚，用户不会以为出错了。 */
internal fun gapText(minutes: Int): String =
    if (minutes <= 0) "无空档" else "空档 ${durationText(minutes)}"

/** 边界的界面名称（滚轮标题、无障碍描述共用）。 */
internal val GreetingEdge.title: String
    get() = when (this) {
        GreetingEdge.MORNING_START -> "调整早间开始时间"
        GreetingEdge.MORNING_END -> "调整早间结束时间"
        GreetingEdge.NOON_START -> "调整午间开始时间"
        GreetingEdge.NOON_END -> "调整午间结束时间"
        GreetingEdge.NIGHT_START -> "调整夜间开始时间"
        GreetingEdge.NIGHT_END -> "调整夜间结束时间"
    }

/** 边界在段内的角色，用于副标题与端点说明。 */
internal val GreetingEdge.role: String get() = if (isStart) "起点" else "终点"

/** 端点的说明文案（7.6.1 起八个端点全可调，不再有「固定」这一说）。 */
internal val GreetingEdge.fixedNote: String get() = "可调整"

/** 撞到合法边界时的解释（不能说「卡住了」，得说清为什么）。 */
internal fun limitNote(edge: GreetingEdge, atMin: Boolean): String = when {
    edge == GreetingEdge.MORNING_START && atMin -> "已经不早于 00:00 了"
    edge == GreetingEdge.MORNING_START -> "最晚只能到早间问候结束前 5 分钟"
    edge == GreetingEdge.NIGHT_END && !atMin -> "已经不晚于 24:00 了"
    edge == GreetingEdge.NIGHT_END -> "最早只能到夜间问候开始后 5 分钟"
    edge == GreetingEdge.MORNING_END && !atMin -> "最晚只能到午间问候开始"
    edge == GreetingEdge.MORNING_END -> "早安至少保留 5 分钟"
    edge == GreetingEdge.NOON_START && !atMin -> "午间至少保留 5 分钟"
    edge == GreetingEdge.NOON_START -> "最早只能到早间问候结束"
    edge == GreetingEdge.NOON_END && atMin -> "午间至少保留 5 分钟"
    edge == GreetingEdge.NOON_END -> "最晚只能到夜间问候开始"
    edge == GreetingEdge.NIGHT_START && !atMin -> "夜间至少保留 5 分钟"
    else -> "最早只能到午间问候结束"
}
