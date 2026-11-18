package com.rongyu.shixuji.ui

import com.rongyu.shixuji.data.Todo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 月视图（纸上年鉴）/ 年视图（格阵）用到的纯函数（2026-11-18 改版）。
 *
 * 画在屏幕上的东西只能靠肉眼验，但「聚合对不对、热力分档对不对、脚注怎么措辞」
 * 这几件事必须能跑成断言 —— 之前它们藏在 Composable 的 remember{} 里，
 * 每次改都要装模拟器点进去看，回归一次要十几分钟。
 */
class CalendarGridsTest {

    // 月键 = year*100 + (月份-1)，别再手写魔法数字（202610 是 11 月，不是 10 月）
    private val NOV26 = DateUtils.monthKey(2026, 10)
    private val DEC26 = DateUtils.monthKey(2026, 11)
    private val JAN27 = DateUtils.monthKey(2027, 0)

    private fun at(year: Int, month1: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month1 - 1, day, hour, 0, 0)
        }.timeInMillis

    private fun t(id: Long, due: Long, done: Boolean = false, prio: Int = 0) =
        Todo(id = id, title = "t$id", done = done, dueDate = due, priority = prio)

    // ---------------- buildDayBriefs ----------------

    /** 同一天多件事：件数累加、完成数只数 done、最高优先级取最大 */
    @Test
    fun aggregatesPerDay() {
        val m = buildDayBriefs(
            listOf(
                t(1, at(2026, 11, 9), done = false, prio = PRIORITY_LOW),
                t(2, at(2026, 11, 9), done = true, prio = PRIORITY_HIGH),
                t(3, at(2026, 11, 9), done = true, prio = 0),
                t(4, at(2026, 11, 10), done = false, prio = PRIORITY_MID)
            )
        )
        val d9 = m[NOV26]!![9]!!
        assertEquals(3, d9.total)
        assertEquals(2, d9.done)
        assertEquals(PRIORITY_HIGH, d9.maxPrio)

        val d10 = m[NOV26]!![10]!!
        assertEquals(1, d10.total)
        assertEquals(0, d10.done)
        assertEquals(PRIORITY_MID, d10.maxPrio)
    }

    /** 无期限（dueDate = 0）不属于任何一天，不能进日历 —— 否则今天会被它的件数染色 */
    @Test
    fun ignoresUndated() {
        val m = buildDayBriefs(listOf(t(1, 0L, prio = PRIORITY_HIGH), t(2, at(2026, 11, 18))))
        assertEquals(1, m[NOV26]!![18]!!.total)
        assertEquals(1, m.values.sumOf { it.size })
    }

    /** 跨月/跨年落在各自的月键里（monthKey = year*100 + month0） */
    @Test
    fun splitsByMonthKey() {
        val m = buildDayBriefs(
            listOf(t(1, at(2026, 11, 30)), t(2, at(2026, 12, 1)), t(3, at(2027, 1, 1)))
        )
        assertEquals(1, m[NOV26]!![30]!!.total)
        assertEquals(1, m[DEC26]!![1]!!.total)
        assertEquals(1, m[JAN27]!![1]!!.total)
        assertTrue(!m.containsKey(DEC26 * 10))
    }

    // ---------------- 热力分档 ----------------

    @Test
    fun heatLevelBoundaries() {
        assertEquals(0, heatLevel(0))
        assertEquals(1, heatLevel(1))
        assertEquals(2, heatLevel(2))
        assertEquals(3, heatLevel(3))
        assertEquals(4, heatLevel(4))
        assertEquals(4, heatLevel(99))
    }

    /** 年视图色块：4 档必须单调加深，且第 4 档是实心（不能再靠文字区分，块里没有字了） */
    @Test
    fun blockAlphaMonotonic() {
        val a = (1..4).map { blockAlpha(it) }
        assertEquals(listOf(0.14f, 0.34f, 0.60f, 1f), a)
        assertEquals(1f, blockAlpha(9))          // 超过 4 件仍是实心
        assertTrue(a[0] < a[1] && a[1] < a[2] && a[2] < a[3])
    }

    // ---------------- 月视图脚注 ----------------

    @Test
    fun monthSummaryNormal() {
        val briefs = mapOf(9 to DayBrief(4, 2, PRIORITY_HIGH), 18 to DayBrief(3, 1, PRIORITY_MID))
        val total = briefs.values.sumOf { it.total }
        val done = briefs.values.sumOf { it.done }
        assertEquals("本月共 7 件 · 已完成 3 · 最忙 9 日（4 件）", monthSummary(briefs, total, done))
    }

    /** 并列最忙时取更早的那天（否则同分月份会随 HashMap 顺序跳来跳去） */
    @Test
    fun monthSummaryTieTakesEarlierDay() {
        val briefs = mapOf(28 to DayBrief(5, 0, 0), 3 to DayBrief(5, 0, 0), 11 to DayBrief(1, 0, 0))
        val s = monthSummary(briefs, 11, 0)
        assertTrue(s, s.endsWith("最忙 3 日（5 件）"))
    }

    /** 一件都没有时不显示「最忙…」（月视图那边也整块不画脚注） */
    @Test
    fun monthSummaryEmpty() {
        assertEquals("本月共 0 件 · 已完成 0", monthSummary(emptyMap(), 0, 0))
    }
}
