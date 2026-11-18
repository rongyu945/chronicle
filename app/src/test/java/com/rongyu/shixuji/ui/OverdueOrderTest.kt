package com.rongyu.shixuji.ui

import com.rongyu.shixuji.data.Todo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「过往未完成」一节的排序口径（用户 2026-11-17）：
 * 优先级在时间之前；同优先级内到期日新的在上；拖过排序的整节按用户摆的名次。
 */
class OverdueOrderTest {

    private fun t(id: Long, priority: Int, due: Long, sortOrder: Int = 0) = Todo(
        id = id,
        title = "t$id",
        dueDate = due,
        priority = priority,
        sortOrder = sortOrder
    )

    private fun ids(list: List<Todo>) = list.sortedWith(overdueTodosOrder).map { it.id }

    /** 优先级压过时间：低优先级的「昨天」不能排在高优先级的「上个月」前面 */
    @Test
    fun priorityBeatsDate() {
        val lowNew = t(1, PRIORITY_LOW, 1_700_000_000_000L)   // 新的、低优先级
        val highOld = t(2, PRIORITY_HIGH, 1_600_000_000_000L) // 旧的、高优先级
        assertEquals(listOf(2L, 1L), ids(listOf(lowNew, highOld)))
    }

    /** 同优先级内：到期日新的（逾期浅的）在上，旧的在下面 */
    @Test
    fun samePriorityNewerFirst() {
        val oldest = t(1, PRIORITY_MID, 1_600_000_000_000L)
        val middle = t(2, PRIORITY_MID, 1_650_000_000_000L)
        val newest = t(3, PRIORITY_MID, 1_700_000_000_000L)
        assertEquals(listOf(3L, 2L, 1L), ids(listOf(oldest, newest, middle)))
    }

    /** 四档优先级：高 → 中 → 低 → 无，无优先级的沉到最后 */
    @Test
    fun allPriorityLevels() {
        val none = t(1, PRIORITY_NONE, 1_700_000_000_000L)
        val low = t(2, PRIORITY_LOW, 1_690_000_000_000L)
        val mid = t(3, PRIORITY_MID, 1_680_000_000_000L)
        val high = t(4, PRIORITY_HIGH, 1_670_000_000_000L)
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(listOf(none, low, mid, high)))
    }

    /** 没拖过的条目（sortOrder=0）排在拖过的整节之前，拖过的那节按名次 */
    @Test
    fun draggedOrderWins() {
        val undragged = t(1, PRIORITY_NONE, 1_600_000_000_000L)
        val draggedSecond = t(2, PRIORITY_HIGH, 1_700_000_000_000L, sortOrder = 10_000_002)
        val draggedFirst = t(3, PRIORITY_LOW, 1_650_000_000_000L, sortOrder = 10_000_001)
        assertEquals(listOf(1L, 3L, 2L), ids(listOf(draggedSecond, draggedFirst, undragged)))
    }

    /** 排序键唯一：优先级与到期日全相同也稳定（按 id），不会每次刷新乱跳 */
    @Test
    fun stableTieBreak() {
        val a = t(7, PRIORITY_MID, 1_700_000_000_000L)
        val b = t(5, PRIORITY_MID, 1_700_000_000_000L)
        val c = t(9, PRIORITY_MID, 1_700_000_000_000L)
        assertEquals(listOf(5L, 7L, 9L), ids(listOf(a, c, b)))
    }
}
