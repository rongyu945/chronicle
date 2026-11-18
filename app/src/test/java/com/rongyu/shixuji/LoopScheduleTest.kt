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

    // ===== 日序号口径（2026-11-08 审计 A 项）==================================

    @Test
    fun 日序号是公历日序号不随系统时区漂移() {
        // 2026-11-08 在任何时区都必须是同一个数（1970-01-01 = 0）
        assertEquals(20765L, LoopSchedule.daysFromCivil(2026, 11, 8))
        assertEquals(0L, LoopSchedule.daysFromCivil(1970, 1, 1))
        // 闰日不能少算：2024-02-29 紧跟 2024-02-28
        assertEquals(
            LoopSchedule.daysFromCivil(2024, 2, 28) + 1,
            LoopSchedule.daysFromCivil(2024, 2, 29)
        )
        assertEquals(
            LoopSchedule.daysFromCivil(2024, 2, 29) + 1,
            LoopSchedule.daysFromCivil(2024, 3, 1)
        )
        // 世纪闰年规则：1900 不是闰年，2000 是
        assertEquals(365L, LoopSchedule.daysFromCivil(1901, 1, 1) - LoopSchedule.daysFromCivil(1900, 1, 1))
        assertEquals(366L, LoopSchedule.daysFromCivil(2001, 1, 1) - LoopSchedule.daysFromCivil(2000, 1, 1))
        // 与 Calendar 无关：同一天的不同时刻，日序号相同
        assertEquals(LoopSchedule.dayNumber(day(2026, 11, 8)), LoopSchedule.dayNumber(day(2026, 11, 8) + 23 * 3600_000L))
    }

    @Test
    fun 旧格式doneDates整体平移后仍能对上勾选() {
        // 老库里 11-08 记的是旧口径（东八区 = 新口径 − 1）
        val shifted = LoopSchedule.shiftDoneDates("20764,20760", 1)
        assertEquals("20765,20761", shifted)
        val l = loop(day(2026, 11, 1), day(2026, 11, 5)).copy(doneDates = shifted)
        assertTrue("平移后 11-08 应该是已勾", LoopSchedule.isDoneOn(l, day(2026, 11, 8)))
        assertTrue("平移后 11-04 也是已勾（20761 = 11-04）", LoopSchedule.isDoneOn(l, day(2026, 11, 4)))
        assertFalse("没记过的 11-03 不该是已勾", LoopSchedule.isDoneOn(l, day(2026, 11, 3)))
        // delta = 0（西半球）时原样返回；空串不炸
        assertEquals("20764", LoopSchedule.shiftDoneDates("20764", 0))
        assertEquals("", LoopSchedule.shiftDoneDates("", 1))
        // 脏数据里的非数字项丢掉，不把整串写坏
        assertEquals("20765,20761", LoopSchedule.shiftDoneDates("20764,,abc,20760", 1))
    }

    // ===== 7.5.2（审查 5.3 / 5.4）=====

    @Test fun `间隔超过上限时夹到 MAX_INTERVAL_DAYS`() {
        val start = LoopSchedule.dayStart(1_800_000_000_000L)
        val far = start + 5000L * 86_400_000L
        assertEquals(LoopSchedule.MAX_INTERVAL_DAYS, LoopSchedule.intervalOf(start, far))
        // 上限以内原样返回
        assertEquals(3650, LoopSchedule.intervalOf(start, start + 3650L * 86_400_000L))
        assertEquals(1, LoopSchedule.intervalOf(start, start))
    }

    @Test fun `日序号上下界与毫秒时间戳的区分`() {
        assertTrue(LoopSchedule.isDayNumber(0))
        assertTrue(LoopSchedule.isDayNumber(LoopSchedule.MAX_DAY_NUMBER))   // 约 4708 年，上限本身合法
        assertFalse(LoopSchedule.isDayNumber(1_700_000L))                  // 超上限（约 6384 年）
        assertFalse(LoopSchedule.isDayNumber(-1))
        assertFalse(LoopSchedule.isDayNumber(1_700_000_000_000L))  // 毫秒时间戳，不是日序号
        assertFalse(LoopSchedule.isDayNumber(LoopSchedule.MAX_DAY_NUMBER + 1))
    }

    @Test fun `迁移平移到毫秒时间戳时原样保留`() {
        val raw = "19000,1700000000000"
        val shifted = LoopSchedule.shiftDoneDates(raw, 1)
        assertTrue(shifted.contains("1700000000000"))
        assertTrue(shifted.contains("19001"))
    }

    /**
     * 7.5.5（复核报告 3.9）：编辑已有条目时，**间隔以库里那份为准**，不能按两个日期反推。
     *
     * 实测事故：吃维生素 intervalDays=1，startDate 10月30日、nextDate 被自动推到了 11月11日；
     * 打开弹窗什么也没改、点保存，间隔就变成 12 天，提醒序列跟着改。
     */
    @Test fun `编辑时保留库里的间隔_不按日期反推`() {
        val start = day(2026, 10, 30)
        val next = day(2026, 11, 11)
        // 库里是 1 天：哪怕两个日期差 12 天，也必须是 1
        assertEquals(1, LoopSchedule.intervalForEdit(1, start, next))
        assertEquals(4, LoopSchedule.intervalForEdit(4, start, next))
        // 新建（库里没有）：才由两个日期推
        assertEquals(12, LoopSchedule.intervalForEdit(null, start, next))
        // 库里是坏值（0 / 负数 / 超过上限）一律夹到合法范围，不让它把序列算飞
        assertEquals(1, LoopSchedule.intervalForEdit(0, start, next))
        assertEquals(1, LoopSchedule.intervalForEdit(-3, start, next))
        assertEquals(LoopSchedule.MAX_INTERVAL_DAYS, LoopSchedule.intervalForEdit(99_999, start, next))
    }
}
