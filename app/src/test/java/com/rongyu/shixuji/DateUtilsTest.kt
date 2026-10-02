package com.rongyu.shixuji

import com.rongyu.shixuji.ui.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

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
}
