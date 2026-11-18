package com.rongyu.shixuji.data

import java.util.Calendar

/**
 * 循环待办的日程算法（纯函数，全部可单测）。
 *
 * 语义（5.5.0 起，用户 2026-11-01 定）：
 * 用户只设两个日期 —— **开始日期** [LoopTodo.startDate] 与 **下一次提醒日期** [LoopTodo.nextDate]，
 * 间隔天数 = 两者相差的天数（保存时算出来固化进 [LoopTodo.intervalDays]）。
 * 之后每次提醒都在「下一次提醒日期」的基础上加一个间隔，所以整条序列是：
 *
 *     nextDate, nextDate ± k·intervalDays   （k 为整数，早于 startDate 的日子不算）
 *
 * 用「锚点 + 整数倍间隔」而不是「从开始日期逐次数」，是因为日子往后走时要把 nextDate
 * 推到最近的一次（见 [advanceNext]）—— 加的是整倍数，序列本身一点不变，
 * 而已勾选的记录（[LoopTodo.doneDates]）仍然对得上原来的日子。
 */
object LoopSchedule {

    const val DAY_MS = 86_400_000L

    /** 当天零点（分钟/秒/毫秒都归零，按系统时区） */
    fun dayStart(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** bMs 所在日 − aMs 所在日，单位「天」（四舍五入，夏令时地区也不会差一天） */
    fun daysBetween(aMs: Long, bMs: Long): Long =
        Math.round((dayStart(bMs) - dayStart(aMs)).toDouble() / DAY_MS)

    /**
     * 稳定的日序号（用来在 [LoopTodo.doneDates] 里记「哪一天勾的」）。
     *
     * ★ 5.9.x（2026-11-08 审计）：改成**公历日序号**（2000-01-01 为基准的 epochDay）——
     *   由「年/月/日」三个数直接算出来，与时区无关。
     *
     *   旧实现是 `floorDiv(本地零点, 86400000)`。本地零点在 UTC+n 的时区里等于
     *   **UTC 前一天 16:00**，所以算出来是 epochDay − 1；在 UTC−n 的时区里又正好等于
     *   epochDay。也就是说这个「日序号」带时区偏移，用户一换时区（出差、改系统时区、
     *   夏令时切换）整个序号平移一天，[LoopTodo.doneDates] 里记过的日子全部对不上：
     *   勾过的显示未完成，再勾一次还会写重复。
     *
     *   老数据由 [AppContainer] 里的一次性迁移（`loop_epoch_day_v2`）平移过来。
     */
    fun dayNumber(ms: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return daysFromCivil(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * 公历 → 日序号（1970-01-01 = 0）。
     *
     * Howard Hinnant 的 days_from_civil：纯整数运算，不看时区/夏令时/闰秒，
     * 1875 年之后、10000 年之前都准确（够用到没人再打开这个 App 的时候）。
     */
    fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400                                     // 0..399
        val mp = (month + 9) % 12                                   // 3 月 = 0
        val doy = (153 * mp + 2) / 5 + day - 1                       // 0..365
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy              // 0..146096
        return era.toLong() * 146097L + doe - 719468L
    }

    /**
     * 旧格式（`floorDiv(本地零点)`）与现在这个公历日序号的差，单位「天」。
     *
     * ★ 已知局限（审查 5.6）：这里只看**执行迁移那一刻**的系统时区。
     *   如果用户在用老版本时出过国、或迁移前后改过系统时区，旧数据写入时的时区
     *   就可能和现在不同，平移量会算错。历史版本没有记录「写入时的时区」，
     *   无法可靠推断 —— 能做的是把局限写清楚、别在此基础上继续加规则。
     *   （备份导入走 [com.rongyu.shixuji.data.BackupCodec] 里按 appVersion 判定的同一条口径。）
     *
     * UTC+n（含 +5:30 这类）记为差 1，UTC−n 差 0 —— 与偏移量的大小无关，只看符号：
     * 本地零点永远是「当地那天的 00:00」，而它在 UTC 里落在前一天还是同一天，
     * 只取决于当地是东经还是西经时区。
     */
    fun legacyDayShift(): Long {
        val offset = Calendar.getInstance().timeZone.getOffset(System.currentTimeMillis())
        return if (offset > 0) 1L else 0L
    }

    /**
     * `doneDates` 里合法「日序号」的上限：约 4700 年。
     *
     * 超过它的值不可能是公历日序号 —— 更老的版本曾把**毫秒时间戳**塞进这个字段
     * （1.7e12 量级），它们排序在所有正常日序号之后，会把「只留最近 120 天」的名额占满，
     * 把真实的完成记录挤掉。清洗与迁移必须用**同一个**上下界，否则一边认一边不认。
     */
    /**
     * 日序号的上界。
     *
     * 依据（复核报告 3.4 要求写明）：公历日序号从 1970-01-01 = 0 起算，1,000,000 天 ≈ 公元 4708 年，
     * 任何真实日期都远小于它；同时毫秒时间戳 ≥ 1.6e12（2020 年），远大于它 ——
     * 上下两边都留了三个数量级的空档，老版本塞进 `doneDates` 的毫秒时间戳不会被误当成日序号。
     */
    const val MAX_DAY_NUMBER = 1_000_000L

    /** 这个数像不像一个日序号（[0, MAX_DAY_NUMBER]） */
    fun isDayNumber(v: Long): Boolean = v in 0L..MAX_DAY_NUMBER

    /** 循环间隔上限（天）。UI 与算法共用，别各写一份。 */
    const val MAX_INTERVAL_DAYS = 3650

    /**
     * 迁移用：把一整串旧格式的 doneDates 整体平移 [delta] 天（去重、降序、仍只留最近 MAX_KEPT 天）。
     *
     * 只平移「像日序号」的值（见 [isDayNumber]）。更老的版本曾经往这个字段里
     * 塞过毫秒时间戳，那种值平移一下也是废数 —— 原样留着，别把用户的账搅得更乱。
     */
    fun shiftDoneDates(raw: String, delta: Long): String {
        if (delta == 0L || raw.isBlank()) return raw
        return raw.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .map { if (isDayNumber(it)) it + delta else it }
            .distinct()
            .sortedDescending()
            .take(MAX_KEPT)
            .joinToString(",")
    }

    /**
     * 编辑已有循环待办时该用哪个间隔。
     *
     * ★ 7.5.5（复核报告 3.9）：`nextDate` 会被 [advanceNext] 不断往前推，所以「两个日期相减」
     *   得到的不是周期，而是「距上次提醒过去了多少天」。旧版保存时无条件按日期反推 ——
     *   打开弹窗、什么都不改、点保存，周期就从 1 天变成 12 天，提醒序列跟着变（实测：吃维生素）。
     *
     *   现在：库里已经有 [LoopTodo.intervalDays] 就**以它为准**（权威值），只有新建
     *   （`stored == null`）才由两个日期推出初次间隔。用户想改周期，用弹窗里「间隔」那行的
     *   ‹ › 显式改 —— 那是一次明确的动作，不再是「打开即变」的副作用。
     */
    fun intervalForEdit(stored: Int?, startDate: Long, nextDate: Long): Int =
        stored?.coerceIn(1, MAX_INTERVAL_DAYS) ?: intervalOf(startDate, nextDate)

    /** 两个日期之间的间隔天数：至少 1 天（填成同一天就等于每天提醒），最多 [MAX_INTERVAL_DAYS] 天 */
    fun intervalOf(startDate: Long, nextDate: Long): Int =
        daysBetween(startDate, nextDate)
            .coerceAtLeast(1L)
            .coerceAtMost(MAX_INTERVAL_DAYS.toLong())
            .toInt()

    /**
     * 这一天该不该出现这条循环待办。
     *
     * 早于开始日期不出；其余看「与锚点差的天数是不是间隔的整数倍」。
     * `%` 对负数会给出负数（Kotlin/Java 的取余是从截断除法来的），所以用 `Math.floorMod`。
     */
    fun isDueOn(loop: LoopTodo, dayMs: Long): Boolean {
        if (loop.nextDate <= 0L) return false
        val day = dayStart(dayMs)
        if (day < dayStart(loop.startDate)) return false
        val interval = loop.intervalDays.coerceAtLeast(1).toLong()
        val diff = daysBetween(loop.nextDate, day)
        return Math.floorMod(diff, interval) == 0L
    }

    /**
     * 日子往前走之后，「下一次提醒」应该落在哪一天：返回需要写入的新 nextDate，
     * 已经不需要动就返回 null（调用方据此跳过写库，避免每天无谓地写一次而触发自动备份）。
     *
     * 加的是**整倍数**间隔，所以提醒日序列不变，只是字段值追上今天。
     */
    fun advanceNext(loop: LoopTodo, todayMs: Long): Long? {
        if (loop.nextDate <= 0L) return null
        val today = dayStart(todayMs)
        if (loop.nextDate >= today) return null
        val interval = loop.intervalDays.coerceAtLeast(1).toLong()
        val k = Math.floorDiv(daysBetween(loop.nextDate, today) + interval - 1, interval)
        val cal = Calendar.getInstance().apply { timeInMillis = loop.nextDate }
        cal.add(Calendar.DAY_OF_MONTH, (k * interval).toInt())
        return cal.timeInMillis
    }

    /** 最近一次「还没有过的」提醒日（用于设置页显示「下次 11月5日」） */
    fun upcomingDate(loop: LoopTodo, todayMs: Long): Long {
        val today = dayStart(todayMs)
        val next = if (loop.nextDate >= today) loop.nextDate else (advanceNext(loop, today) ?: loop.nextDate)
        return next
    }

    fun isDoneOn(loop: LoopTodo, dayMs: Long): Boolean =
        loop.doneDates.split(',').any { it.trim() == dayNumber(dayMs).toString() }

    /**
     * 勾选/取消勾选某一天，返回新的 doneDates 串。
     *
     * 只留最近 [MAX_KEPT] 天：串是逗号分隔的明文，无上限地长下去会把备份文件撑大，
     * 而这个字段的用途只是「回头看一眼哪天做过」，不用留全史。
     */
    fun withDone(loop: LoopTodo, dayMs: Long, done: Boolean): String {
        val target = dayNumber(dayMs)
        val days = loop.doneDates.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it != target }          // 先剔掉这一天，再决定要不要加回来
            .toMutableList()
        if (done) days.add(target)
        days.sortDescending()
        return days.take(MAX_KEPT).joinToString(",")
    }

    private const val MAX_KEPT = 120
}
