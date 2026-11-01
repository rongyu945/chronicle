package com.rongyu.shixuji

import com.rongyu.shixuji.ui.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** 日历计算的回归测试：月末列对齐、跨月跨年翻页、周一=1 的换算 */
class DateUtilsTest {

    @Test
    fun 一月一号之前的空白格数() {
        // 2026-10-01 是周四 → 周一/二/三 三格留空
        assertEquals(3, DateUtils.leadingBlanks(2026, 9))
        // 2026-11-01 是周日 → 前留 6 格
        assertEquals(6, DateUtils.leadingBlanks(2026, 10))
        // 2026-09-01 是周二 → 1 格
        assertEquals(1, DateUtils.leadingBlanks(2026, 8))
        // 闰年 2024-02-01 是周四 → 3 格
        assertEquals(3, DateUtils.leadingBlanks(2024, 1))
    }

    @Test
    fun 每月天数含闰年二月() {
        assertEquals(31, DateUtils.daysInMonth(2026, 9))
        assertEquals(30, DateUtils.daysInMonth(2026, 10))
        assertEquals(28, DateUtils.daysInMonth(2026, 1))
        assertEquals(29, DateUtils.daysInMonth(2024, 1))
    }

    @Test
    fun 月视图格子总数固定为42便于左对齐() {
        for (y in 2024..2030) {
            for (m in 0..11) {
                val cells = DateUtils.leadingBlanks(y, m) + DateUtils.daysInMonth(y, m)
                assertTrue("$y-$m 格数 $cells 超过 42", cells <= 42)
            }
        }
    }

    @Test
    fun 跨月跨年加减月份() {
        assertEquals(2025 to 11, DateUtils.addMonths(2026, 0, -1))   // 2026-01 往前一月 = 2025-12
        assertEquals(2027 to 0, DateUtils.addMonths(2026, 11, 1))   // 2026-12 往后一月 = 2027-01
        assertEquals(2026 to 9, DateUtils.addMonths(2026, 8, 1))
        assertEquals(2026 to 8, DateUtils.addMonths(2026, 9, -1))
        assertEquals(2025 to 6, DateUtils.addMonths(2026, 5, -11))  // 跨年回溯 11 个月
    }

    @Test
    fun 周一到周日的编号换算() {
        // 2026-10-01 是周四
        val thu = DateUtils.month(2026, 9).apply { set(Calendar.DAY_OF_MONTH, 1) }
        assertEquals(4, DateUtils.isoDayOfWeek(thu))
        assertEquals("周四", DateUtils.weekdayCn(thu))
        assertEquals("四", DateUtils.weekdayShortCn(thu))

        // 周日应换算成 7，而不是 0/1
        val sun = DateUtils.month(2026, 10).apply { set(Calendar.DAY_OF_MONTH, 1) }
        assertEquals(7, DateUtils.isoDayOfWeek(sun))
        assertEquals("周日", DateUtils.weekdayCn(sun))
    }

    @Test
    fun 同一周的起点是周一零点() {
        val d = DateUtils.month(2026, 9).apply { set(Calendar.DAY_OF_MONTH, 1) }  // 周四
        val start = DateUtils.startOfWeek(d)
        assertEquals(Calendar.MONDAY, start.get(Calendar.DAY_OF_WEEK))
        assertEquals(28, start.get(Calendar.DAY_OF_MONTH))   // 2026-09-28 周一
        assertEquals(8, start.get(Calendar.MONTH))           // Calendar.MONTH 从 0 起算，9 月 = 8
    }

    @Test
    fun 同一天判定与加减天数() {
        val a = DateUtils.month(2026, 9).apply { set(Calendar.DAY_OF_MONTH, 1) }
        assertTrue(DateUtils.isSameDay(a, DateUtils.addDays(a, 0)))
        val next = DateUtils.addDays(a, 1)
        assertEquals(2, next.get(Calendar.DAY_OF_MONTH))
        assertTrue(!DateUtils.isSameDay(a, next))
        // 跨月加天数
        val monthEnd = DateUtils.month(2026, 9).apply { set(Calendar.DAY_OF_MONTH, 31) }
        assertEquals(1, DateUtils.addDays(monthEnd, 1).get(Calendar.DAY_OF_MONTH))
        assertEquals(10, DateUtils.addDays(monthEnd, 1).get(Calendar.MONTH))
    }

    @Test
    fun 闰日与跨年边界() {
        // 2024-02-29 的下一天是 2024-03-01（不是 02-30，也不是 03-02）
        val feb29 = DateUtils.month(2024, 1).apply { set(Calendar.DAY_OF_MONTH, 29) }
        val mar01 = DateUtils.addDays(feb29, 1)
        assertEquals(2, mar01.get(Calendar.MONTH))
        assertEquals(1, mar01.get(Calendar.DAY_OF_MONTH))
        assertTrue(!DateUtils.isSameDay(feb29, mar01))

        // 2026-12-31 → 2027-01-01，年份要跟着进位
        val dec31 = DateUtils.month(2026, 11).apply { set(Calendar.DAY_OF_MONTH, 31) }
        val newYear = DateUtils.addDays(dec31, 1)
        assertEquals(2027, newYear.get(Calendar.YEAR))
        assertEquals(0, newYear.get(Calendar.MONTH))
        assertEquals(1, newYear.get(Calendar.DAY_OF_MONTH))

        // 跨年翻 12 个月不回到原地
        assertEquals(2027 to 0, DateUtils.addMonths(2026, 0, 12))
        assertEquals(2025 to 11, DateUtils.addMonths(2026, 0, -1))
        // 平年/闰年二月天数与首列留白
        assertEquals(28, DateUtils.daysInMonth(2026, 1))
        assertEquals(29, DateUtils.daysInMonth(2024, 1))
        assertEquals(3, DateUtils.leadingBlanks(2024, 1))
    }

    /**
     * 夏令时：起止时刻必须按**日历天**算，不能拿「毫秒差 ÷ 86400000」。
     * 老实现最容易在春秋拨表这两天出错（那天只有 23 小时 / 有 25 小时）。
     */
    @Test
    fun 夏令时切换当天的起止时刻() {
        val oldTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            // 2026-03-08 春季拨快一小时 → 这一天 23 小时
            val spring = instant(2026, 3, 8, 12, 0, "America/New_York")
            val s1 = DateUtils.startOfDayMs(spring)
            assertEquals(23 * 3_600_000L - 1, DateUtils.endOfDayMs(s1) - s1)
            // 2026-11-01 秋季拨慢一小时 → 这一天 25 小时
            val autumn = instant(2026, 11, 1, 12, 0, "America/New_York")
            val s2 = DateUtils.startOfDayMs(autumn)
            assertEquals(25 * 3_600_000L - 1, DateUtils.endOfDayMs(s2) - s2)

            // 加一天仍是「次日零点」：拨表那天不会多走/少走一天
            val next = DateUtils.addDays(DateUtils.calFrom(s1), 1)
            assertEquals(9, next.get(Calendar.DAY_OF_MONTH))
            assertEquals(0, next.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, next.get(Calendar.MINUTE))
        } finally {
            TimeZone.setDefault(oldTz)
        }
    }

    /**
     * 换时区 / 跨时区：不管在哪个时区，`startOfDayMs(t) <= t <= endOfDayMs(t)` 必成立，
     * 且起点零点、终点 23:59:59.999 都落在**同一个日历日**上。
     */
    @Test
    fun 任何时区下当天起止都把该时刻包在里面() {
        val oldTz = TimeZone.getDefault()
        try {
            val instants = listOf(
                instant(2026, 3, 8, 3, 30, "America/New_York"),    // 春天拨快后那一小时
                instant(2026, 11, 1, 1, 30, "America/New_York"),   // 秋天重复的那一小时
                instant(2026, 12, 31, 23, 59, "Asia/Shanghai"),    // 跨年前一刻
                instant(2027, 1, 1, 0, 0, "Asia/Shanghai"),        // 跨年后一刻
            )
            for (tz in listOf("Asia/Shanghai", "America/New_York", "Europe/London", "Pacific/Kiritimati")) {
                TimeZone.setDefault(TimeZone.getTimeZone(tz))
                for (ms in instants) {
                    val s = DateUtils.startOfDayMs(ms)
                    val e = DateUtils.endOfDayMs(ms)
                    assertTrue("$tz：$ms 不在 [$s, $e] 里", ms in s..e)
                    val expectedDay = DateUtils.calAt(ms).get(Calendar.DAY_OF_MONTH)
                    // 注意用 calAt（保留时刻）：calFrom 会把时分秒归零，量出来的「终点是 0 点」是假的
                    val start = DateUtils.calAt(s)
                    val end = DateUtils.calAt(e)
                    assertEquals("$tz 起点落在别的日子", expectedDay, start.get(Calendar.DAY_OF_MONTH))
                    assertEquals("$tz 终点落在别的日子", expectedDay, end.get(Calendar.DAY_OF_MONTH))
                    assertEquals("$tz 起点不是零点", 0, start.get(Calendar.HOUR_OF_DAY))
                    assertEquals("$tz 起点不是零点", 0, start.get(Calendar.MINUTE))
                    assertEquals("$tz 终点不是 23 点", 23, end.get(Calendar.HOUR_OF_DAY))
                    assertEquals("$tz 终点不是 59 分", 59, end.get(Calendar.MINUTE))
                }
            }
        } finally {
            TimeZone.setDefault(oldTz)
        }
    }

    /** 指定时区里的某个时刻（毫秒） */
    private fun instant(y: Int, m: Int, d: Int, h: Int, min: Int, tz: String): Long =
        GregorianCalendar(TimeZone.getTimeZone(tz)).apply {
            clear()
            set(y, m - 1, d, h, min, 0)
        }.timeInMillis
}
