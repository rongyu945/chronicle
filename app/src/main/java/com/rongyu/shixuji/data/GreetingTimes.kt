package com.rongyu.shixuji.data

import kotlin.math.roundToInt

/**
 * 欢迎页三个问候时段的分界（2026-11-11，7.2.5；7.5.0 起边界操作收口到本文件）。
 *
 * **7.6.1 起八个端点全部可调**（原来早安起点钉死 00:00、晚安终点钉死 24:00）：
 *
 * | 键 | 含义 | 默认 |
 * |---|---|---|
 * | [morningStart] | 早安开始 | 00:00（0） |
 * | [morningEnd]   | 早安结束 = 午安之前的空隙起点 | 07:00（420） |
 * | [noonStart]    | 午安开始 | 12:00（720） |
 * | [noonEnd]      | 午安结束 | 13:00（780） |
 * | [nightStart]   | 晚安开始 | 21:00（1260） |
 * | [nightEnd]     | 晚安结束 | 24:00（1440） |
 *
 * 于是一天里可能出现**三段以外的空白**：00:00–morningStart、各段之间、nightEnd–24:00，
 * 这些时间**不显示欢迎页，直接进主界面**。默认配置仍等价于旧行为（早安从 0 点、晚安到 24 点）。
 *
 * 区间口径统一 **左闭右开** `[a, b)`：12:00 整算午安，13:00 整已不算午安。
 * 这样相邻两段永远不会在整点那一刻同时命中。
 *
 * 单位一律是「当天第几分钟」（0..1439），不存 HH:MM 字符串 —— 排时刻、算长度、
 * 画墨线都直接用整数，省掉每次拆字符串。
 */
data class GreetingTimes(
    val morningStart: Int = 0,
    val morningEnd: Int = 420,
    val noonStart: Int = 720,
    val noonEnd: Int = 780,
    val nightStart: Int = 1260,
    val nightEnd: Int = DAY,
) {
    /** 0 = 早安，1 = 午安，2 = 晚安，null = 这个时刻不显示欢迎页。 */
    fun greetingAt(minuteOfDay: Int): Int? {
        val m = minuteOfDay.coerceIn(0, DAY - 1)
        return when {
            m >= morningStart && m < morningEnd -> GREETING_MORNING
            m >= noonStart && m < noonEnd -> GREETING_NOON
            m >= nightStart && m < nightEnd -> GREETING_NIGHT
            else -> null
        }
    }

    /** 某一段的区间。 */
    fun span(kind: Int): IntRange = when (kind) {
        GREETING_MORNING -> morningStart until morningEnd
        GREETING_NOON -> noonStart until noonEnd
        else -> nightStart until nightEnd
    }

    fun lengthOf(kind: Int): Int = when (kind) {
        GREETING_MORNING -> morningEnd - morningStart
        GREETING_NOON -> noonEnd - noonStart
        else -> nightEnd - nightStart
    }

    /** 第 [kind] 段「之前」的空档分钟数（早安之前是 00:00–morningStart）。 */
    fun gapBefore(kind: Int): Int = when (kind) {
        GREETING_MORNING -> morningStart
        GREETING_NOON -> noonStart - morningEnd
        else -> nightStart - noonEnd
    }

    /** 第 [kind] 段「之后」的空档分钟数（晚安之后是 nightEnd–24:00）。 */
    fun gapAfter(kind: Int): Int = when (kind) {
        GREETING_MORNING -> noonStart - morningEnd
        GREETING_NOON -> nightStart - noonEnd
        else -> DAY - nightEnd
    }

    /** 这条边界在库里的当前值。 */
    fun valueOf(edge: GreetingEdge): Int = when (edge) {
        GreetingEdge.MORNING_START -> morningStart
        GreetingEdge.MORNING_END -> morningEnd
        GreetingEdge.NOON_START -> noonStart
        GreetingEdge.NOON_END -> noonEnd
        GreetingEdge.NIGHT_START -> nightStart
        GreetingEdge.NIGHT_END -> nightEnd
    }

    /**
     * 这条边界允许的取值区间（分钟，未吸附）。
     *
     * 与 [updateEdge] 的夹取口径**逐条对应** —— 历史上两处各写一套，会出现「拖动按 A 夹、
     * 落库按 B 夹」，表现是松手后数字又跳一下。
     */
    fun rangeOf(edge: GreetingEdge): IntRange = when (edge) {
        // 7.6.1：首尾两个端点也能动了 —— 早安起点可晚于 00:00、晚安终点可早于 24:00
        GreetingEdge.MORNING_START -> 0..(morningEnd - STEP)
        GreetingEdge.MORNING_END -> (morningStart + STEP)..noonStart
        GreetingEdge.NOON_START -> morningEnd..(noonEnd - STEP)
        GreetingEdge.NOON_END -> (noonStart + STEP)..nightStart
        GreetingEdge.NIGHT_START -> noonEnd..(nightEnd - STEP)
        GreetingEdge.NIGHT_END -> (nightStart + STEP)..DAY
    }

    /**
     * 把这条边界改成 [minute]：吸附到 5 分钟、夹进 [rangeOf]、**其它三个字段一个都不碰**。
     *
     * 计划书 §9.2 要求「不要让四个 UI 入口分别实现边界夹取」。所有编辑入口
     * （拖端点 / 滚轮 / 以后的 ±5 分钟按钮）都走这一个函数。
     */
    fun updateEdge(edge: GreetingEdge, minute: Int): GreetingTimes {
        val r = rangeOf(edge)
        val v = snapToStep(minute).coerceIn(r.first, r.last)
        return when (edge) {
            GreetingEdge.MORNING_START -> copy(morningStart = v)
            GreetingEdge.MORNING_END -> copy(morningEnd = v)
            GreetingEdge.NOON_START -> copy(noonStart = v)
            GreetingEdge.NOON_END -> copy(noonEnd = v)
            GreetingEdge.NIGHT_START -> copy(nightStart = v)
            GreetingEdge.NIGHT_END -> copy(nightEnd = v)
        }
    }

    /**
     * 这条边界所有合法取值（5 分钟步进、落在 [rangeOf] 内）。
     *
     * 滚轮弹层直接用这份列表生成两列 —— 不允许用户先选到非法时间、点确认才被打回。
     */
    fun optionsOf(edge: GreetingEdge): List<Int> {
        val r = rangeOf(edge)
        val lo = ((r.first + STEP - 1) / STEP) * STEP
        if (lo > r.last) return listOf(r.first.coerceIn(0, DAY))
        return (lo..r.last step STEP).toList()
    }

    /**
     * 三段是否自洽：每段非空、且按顺序不重叠。用来挡住手改库 / 旧备份里的坏值。
     *
     * 允许相邻两段**贴在一起**（`morningEnd == noonStart` 即早安 0–12、午安 12–13，
     * 中间不留空隙）—— 那是个完全合理的设置，只有重叠或反向才算坏值。
     */
    fun isValid(): Boolean =
        morningStart >= 0 && morningStart + STEP <= morningEnd &&
            morningEnd <= noonStart &&
            noonStart < noonEnd &&
            noonEnd <= nightStart &&
            nightStart + STEP <= nightEnd && nightEnd <= DAY

    /** 把任意四个数夹回合法区间（宁可整体回默认，也不让界面画出一条反向墨线）。 */
    fun sanitized(): GreetingTimes {
        val fixed = GreetingTimes(
            morningStart = snapToStep(morningStart), morningEnd = snapToStep(morningEnd),
            noonStart = snapToStep(noonStart), noonEnd = snapToStep(noonEnd),
            nightStart = snapToStep(nightStart), nightEnd = snapToStep(nightEnd)
        )
        return if (fixed.isValid()) fixed else DEFAULT
    }

    companion object {
        const val DAY = 1440

        /** 时间步进：滚轮、拖动、吸附都按 5 分钟。 */
        const val STEP = 5

        const val GREETING_MORNING = 0
        const val GREETING_NOON = 1
        const val GREETING_NIGHT = 2

        val DEFAULT = GreetingTimes()

        /**
         * 吸附到 5 分钟：**四舍五入**（7.5.0 统一口径）。
         *
         * 旧版 `sanitized()` 向下取整、拖动四舍五入，同一份数据两条路能算出不同结果
         * （423 拖成 425、存进去却变 420）。现在两边都走这里，正中间值统一向上。
         */
        fun snapToStep(value: Int, step: Int = STEP): Int =
            ((value.toFloat() / step).roundToInt() * step).coerceIn(0, DAY)

        /** app_prefs 里的键名（顺序与字段一致，备份恢复按同一组走）。 */
        const val KEY_MORNING_START = "greet_morning_start"
        const val KEY_MORNING_END = "greet_morning_end"
        const val KEY_NOON_START = "greet_noon_start"
        const val KEY_NOON_END = "greet_noon_end"
        const val KEY_NIGHT_START = "greet_night_start"
        const val KEY_NIGHT_END = "greet_night_end"

        val PREF_KEYS = listOf(
            KEY_MORNING_START, KEY_MORNING_END, KEY_NOON_START,
            KEY_NOON_END, KEY_NIGHT_START, KEY_NIGHT_END
        )

        /**
         * 从整套偏好（`Map<key, value>`）里取。
         *
         * 缺键就用默认值 —— 老版本升级上来的库只有 dark/songti 那几个键，
         * 不读默认值的话时段会变成 0，欢迎页整天都不出现。
         */
        fun fromPrefs(prefs: Map<String, String>?): GreetingTimes {
            if (prefs == null) return DEFAULT
            fun read(k: String, def: Int) = prefs[k]?.toIntOrNull() ?: def
            return GreetingTimes(
                morningStart = read(KEY_MORNING_START, DEFAULT.morningStart),
                morningEnd = read(KEY_MORNING_END, DEFAULT.morningEnd),
                noonStart = read(KEY_NOON_START, DEFAULT.noonStart),
                noonEnd = read(KEY_NOON_END, DEFAULT.noonEnd),
                nightStart = read(KEY_NIGHT_START, DEFAULT.nightStart),
                nightEnd = read(KEY_NIGHT_END, DEFAULT.nightEnd),
            ).sanitized()
        }

        fun toPrefs(t: GreetingTimes): Map<String, String> = mapOf(
            KEY_MORNING_START to t.morningStart.toString(),
            KEY_MORNING_END to t.morningEnd.toString(),
            KEY_NOON_START to t.noonStart.toString(),
            KEY_NOON_END to t.noonEnd.toString(),
            KEY_NIGHT_START to t.nightStart.toString(),
            KEY_NIGHT_END to t.nightEnd.toString(),
        )
    }
}

/**
 * 四个可调边界（7.5.0）。
 *
 * 引入它的唯一理由是「编辑上下文必须能完整传递」：拖端点、点数字开滚轮、写库夹取，
 * 三处以前传的是 `(kind, isStart)` 两个裸参数，容易写反（历史上真写反过：拖早安左钮
 * 改的是早安结束）。现在只有这一个枚举，`isStart` 不再靠调用方口头约定。
 */
enum class GreetingEdge(val kind: Int, val isStart: Boolean) {
    MORNING_START(GreetingTimes.GREETING_MORNING, true),
    MORNING_END(GreetingTimes.GREETING_MORNING, false),
    NOON_START(GreetingTimes.GREETING_NOON, true),
    NOON_END(GreetingTimes.GREETING_NOON, false),
    NIGHT_START(GreetingTimes.GREETING_NIGHT, true),
    NIGHT_END(GreetingTimes.GREETING_NIGHT, false);

    /**
     * 这个端点是否可编辑。
     *
     * 7.6.1 起**六个端点全部可拖**（原来说早安 00:00 / 晚安 24:00 钉死），
     * 所以这里恒为 true —— 保留这个属性只是为了让 UI 有一条「以后想再钉死某一端」的口子。
     */
    val editable: Boolean get() = true

    companion object {
        /** 三段的两个端点 → 边界枚举（六个都有，不再返回 null）。 */
        fun of(kind: Int, isStart: Boolean): GreetingEdge = when (kind) {
            GreetingTimes.GREETING_MORNING -> if (isStart) MORNING_START else MORNING_END
            GreetingTimes.GREETING_NOON -> if (isStart) NOON_START else NOON_END
            else -> if (isStart) NIGHT_START else NIGHT_END
        }
    }
}
