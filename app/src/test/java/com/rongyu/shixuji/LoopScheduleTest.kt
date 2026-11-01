package com.rongyu.shixuji

import com.rongyu.shixuji.data.LoopSchedule
import com.rongyu.shixuji.data.LoopTodo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 循环待办日程算法（5.5.0：开始日期 + 下一次提醒日期 → 间隔天数）。
 *
 * 这层是纯函数，所有口径都在这儿钉住，界面只负责显示：
 * ① 间隔由两个日期算出来，永远 ≥ 1 天；
 * ② 哪一天该出现 = 与锚点相差的天数是间隔的整数倍，且不早于开始日期；
 * ③ 日子走过之后把 nextDate 推到最近一次，**加的是整倍数间隔**，序列不变（这条最关键，
 *    写成「nextDate = today」会把用户设定的相位冲掉，之后每天都提醒）。
 */
class LoopScheduleTest {

    /** 造一个某年某月某日 0 点的毫秒时间戳 */
    private fun day(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply {
        set(y, m - 1, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun loop(start: Long, next: Long, interval: Int = 0) = LoopTodo(
        id = 1, title = "t", detail = "",
        startDate = start, nextDate = next,
        intervalDays = if (interval > 0) interval else LoopSchedule.intervalOf(start, next),
    )

    @Test
    fun 间隔由两个日期算出且至少一天() {
        assertEquals(4, LoopSchedule.intervalOf(day(2026, 11, 1), day(2026, 11, 5)))
        assertEquals(1, LoopSchedule.intervalOf(day(2026, 11, 1), day(2026, 11, 1)))
        // 下一次提醒填到开始日期之前：间隔仍然按最少 1 天算，不出现 0 或负数（否则取余会崩）
        assertEquals(1, LoopSchedule.intervalOf(day(2026, 11, 5), day(2026, 11, 1)))
    }

    @Test
    fun 按间隔成序列出现() {
        val l = loop(day(2026, 11, 1), day(2026, 11, 5))              // 每 4 天
        assertFalse("开始日期之前不出现", LoopSchedule.isDueOn(l, day(2026, 10, 31)))
        assertTrue("锚点当天出现", LoopSchedule.isDueOn(l, day(2026, 11, 5)))
        assertFalse(LoopSchedule.isDueOn(l, day(2026, 11, 6)))
        assertFalse(LoopSchedule.isDueOn(l, day(2026, 11, 8)))
        assertTrue("锚点 + 1 个间隔", LoopSchedule.isDueOn(l, day(2026, 11, 9)))
        assertTrue("锚点 + 2 个间隔", LoopSchedule.isDueOn(l, day(2026, 11, 13)))
        // 早于锚点但晚于开始日期：负数天数的取余必须用 floorMod（Java 的 % 会漏掉这些日子）
        assertTrue("开始日期当天（与锚点差 -4 天）也该出现", LoopSchedule.isDueOn(l, day(2026, 11, 1)))
        assertFalse(LoopSchedule.isDueOn(l, day(2026, 11, 2)))
    }

    @Test
    fun 日期走过之后推到最近一次且相位不变() {
        val start = day(2026, 11, 1)
        val l = loop(start, day(2026, 11, 5))                          // 5 / 9 / 13 / 17 …
        val newNext = LoopSchedule.advanceNext(l, day(2026, 11, 10))!!
        assertEquals("11月10日 → 推到 11月13日（不是今天）", day(2026, 11, 13), newNext)
        // 推完之后，序列里原有的那些日子一个不少
        val moved = l.copy(nextDate = newNext)
        listOf(1, 5, 9, 13, 17, 21).forEach {
            assertTrue("$it 日应仍在序列里", LoopSchedule.isDueOn(moved, day(2026, 11, it)))
        }
        listOf(2, 6, 10, 14).forEach {
            assertFalse("$it 日不该出现", LoopSchedule.isDueOn(moved, day(2026, 11, it)))
        }
    }

    @Test
    fun 没走过就不动() {
        val l = loop(day(2026, 11, 1), day(2026, 11, 5))
        assertNull(LoopSchedule.advanceNext(l, day(2026, 11, 5)))
        assertNull(LoopSchedule.advanceNext(l, day(2026, 11, 1)))
    }

    @Test
    fun 勾选按天记且可取消() {
        val l = loop(day(2026, 11, 1), day(2026, 11, 5))
        assertFalse(LoopSchedule.isDoneOn(l, day(2026, 11, 5)))
        val s1 = LoopSchedule.withDone(l, day(2026, 11, 5), true)
        assertTrue(LoopSchedule.isDoneOn(l.copy(doneDates = s1), day(2026, 11, 5)))
        // 隔一次再勾：两条都在（布尔标记做不到这件事）
        val s2 = LoopSchedule.withDone(l.copy(doneDates = s1), day(2026, 11, 9), true)
        val both = l.copy(doneDates = s2)
        assertTrue(LoopSchedule.isDoneOn(both, day(2026, 11, 5)))
        assertTrue(LoopSchedule.isDoneOn(both, day(2026, 11, 9)))
        // 取消只影响那一天
        val s3 = LoopSchedule.withDone(both, day(2026, 11, 5), false)
        val after = l.copy(doneDates = s3)
        assertFalse(LoopSchedule.isDoneOn(after, day(2026, 11, 5)))
        assertTrue(LoopSchedule.isDoneOn(after, day(2026, 11, 9)))
    }

    @Test
    fun 下一次提醒日显示用最近的一期() {
        val l = loop(day(2026, 11, 1), day(2026, 11, 5))
        assertEquals(day(2026, 11, 5), LoopSchedule.upcomingDate(l, day(2026, 11, 2)))
        assertEquals("今天就是提醒日 → 显示今天", day(2026, 11, 5), LoopSchedule.upcomingDate(l, day(2026, 11, 5)))
        assertEquals(day(2026, 11, 9), LoopSchedule.upcomingDate(l, day(2026, 11, 6)))
    }
}
