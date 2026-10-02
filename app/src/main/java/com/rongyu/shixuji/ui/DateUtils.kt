package com.rongyu.shixuji.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 日历与格式化工具（全局唯一入口）。
 *
 * 约定：业务统一用「周一=1 … 周日=7」，与 Calendar 的 DAY_OF_WEEK（周日=1）不同，
 * 一律通过 isoDayOfWeek() 换算，避免各处手写 -1 / if (== 0) 7 导致周日周一错位。
 */
object DateUtils {
    val WEEK_CN = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /** SimpleDateFormat 非线程安全，用 ThreadLocal 复用而不是每次 new */
    private val hhmm = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    fun todayCal(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    /** 毫秒 → 该天零点 Calendar */
    fun calFrom(ms: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    /** 毫秒 → 保留原时刻的 Calendar */
    fun calAt(ms: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ms }

    /** 某个月的日历（日=1） */
    fun month(year: Int, month0: Int): Calendar = todayCal().apply { set(year, month0, 1) }

    fun daysInMonth(year: Int, month0: Int): Int =
        month(year, month0).getActualMaximum(Calendar.DAY_OF_MONTH)

    /** 某月 1 号的星期几（Calendar 约定：1=周日） */
    fun firstDayOfWeek(year: Int, month0: Int): Int = month(year, month0).get(Calendar.DAY_OF_WEEK)

    /** 1 号之前应留的空格数（以周一为首列） */
    fun leadingBlanks(year: Int, month0: Int): Int =
        (firstDayOfWeek(year, month0) - Calendar.MONDAY + 7) % 7

    /** 周一=1 … 周日=7 */
    fun isoDayOfWeek(c: Calendar): Int {
        val d = c.get(Calendar.DAY_OF_WEEK)
        return if (d == Calendar.SUNDAY) 7 else d - 1
    }

    fun weekdayCn(c: Calendar): String = WEEK_CN[isoDayOfWeek(c) - 1]

    /** 只取一个字：一 / 二 / … / 日 */
    fun weekdayShortCn(c: Calendar): String = weekdayCn(c).substring(1)

    /** 某天所在周的周一零点 */
    fun startOfWeek(cal: Calendar): Calendar {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c
    }

    fun addDays(cal: Calendar, days: Int): Calendar =
        (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, days) }

    /** 年/月加减，返回 (年, 月0)，可安全跨年 */
    fun addMonths(year: Int, month0: Int, delta: Int): Pair<Int, Int> {
        val total = year * 12 + month0 + delta
        return total / 12 to ((total % 12) + 12) % 12
    }

    fun endOfDay(cal: Calendar): Calendar {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999)
        return c
    }

    /** 把一个日期换成"今天此刻的时间"（同一天内的排序仍然合理） */
    fun atCurrentTime(day: Calendar): Long {
        val now = Calendar.getInstance()
        return (day.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, now.get(Calendar.MINUTE))
            set(Calendar.SECOND, now.get(Calendar.SECOND))
            set(Calendar.MILLISECOND, now.get(Calendar.MILLISECOND))
        }.timeInMillis
    }

    fun startOfDayMs(ms: Long): Long = calFrom(ms).timeInMillis
    fun endOfDayMs(ms: Long): Long = endOfDay(calFrom(ms)).timeInMillis

    fun startOfToday(): Calendar = todayCal()

    fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    fun isToday(cal: Calendar): Boolean = isSameDay(cal, todayCal())

    /** 日期在 [dayStart, dayEnd] 之内（含两端） */
    fun inDay(ms: Long, dayStart: Long, dayEnd: Long): Boolean = ms in dayStart..dayEnd

    fun formatTime(ms: Long): String = hhmm.get()!!.format(Date(ms))

    fun formatDateCn(ms: Long): String {
        val c = calAt(ms)
        return "${c.get(Calendar.YEAR)}年${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日"
    }

    /** "10月2日" */
    fun formatMonthDayCn(cal: Calendar): String =
        "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"

    /** 月视图缓存 key：year*100 + month0 */
    fun monthKey(year: Int, month0: Int): Int = year * 100 + month0
}
