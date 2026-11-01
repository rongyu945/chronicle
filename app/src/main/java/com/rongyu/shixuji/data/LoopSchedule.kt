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

    /** 稳定的日序号（用来在 [LoopTodo.doneDates] 里记「哪一天勾的」） */
    fun dayNumber(ms: Long): Long = Math.floorDiv(dayStart(ms), DAY_MS)

    /** 间隔天数：至少 1 天（两个日期填成同一天就等于每天提醒） */
    fun intervalOf(startDate: Long, nextDate: Long): Int =
        daysBetween(startDate, nextDate).coerceAtLeast(1L).coerceAtMost(3650L).toInt()

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
