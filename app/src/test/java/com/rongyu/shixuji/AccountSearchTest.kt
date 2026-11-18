package com.rongyu.shixuji

import com.rongyu.shixuji.data.Account
import com.rongyu.shixuji.data.AccountSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 账目搜索的回归测试。
 *
 * 重点保证两件事：
 * ① 关键字能命中「备注 / 标签 / 金额」三条路，且空关键字等于不过滤（一进搜索页就看到全部）；
 * ② 本月 / 上月 / 今年 的边界一格都不差（跨年 1 月点「上月」要落到去年 12 月）。
 */
class AccountSearchTest {

    private var nextId = 1L

    private fun acc(title: String, cents: Long, tag: String = "", type: Int = 0, at: Calendar): Account =
        Account(id = nextId++, title = title, amountCents = cents, type = type, tag = tag, date = at.timeInMillis)

    private fun cal(y: Int, m0: Int, d: Int, h: Int = 12): Calendar =
        Calendar.getInstance().apply { clear(); set(y, m0, d, h, 0, 0) }

    private val today = cal(2026, 10, 15)          // 2026-11-15

    private val 午饭 = acc("午饭", 3500, "吃饭", at = cal(2026, 10, 15))
    private val 打车 = acc("打车回家", 2350, "交通", at = cal(2026, 10, 3))
    private val 上月的饭 = acc("工作餐", 1200, "吃饭", at = cal(2026, 9, 20))
    private val 去年的饭 = acc("去年的饭", 999, "吃饭", at = cal(2025, 11, 31))
    private val 工资 = acc("十月工资", 1200000, "工资", type = 1, at = cal(2026, 10, 1))

    private val all = listOf(午饭, 打车, 上月的饭, 去年的饭, 工资)

    @Test
    fun 关键字命中备注() {
        val r = AccountSearch.filter(all, "打车")
        assertEquals(1, r.size)
        assertEquals(打车.id, r[0].id)
    }

    @Test
    fun 关键字命中标签() {
        val r = AccountSearch.filter(all, "吃饭")
        assertEquals(3, r.size)          // 午饭 / 工作餐 / 去年的饭
        assertTrue(r.all { it.tag == "吃饭" })
    }

    @Test
    fun 关键字命中金额() {
        // 备注和标签里都没有 2350，只能靠金额命中
        assertEquals(1, AccountSearch.filter(all, "2350").size)
        assertEquals(1, AccountSearch.filter(all, "23.5").size)
        assertEquals(1, AccountSearch.filter(all, "¥2350").size)
        assertEquals(0, AccountSearch.filter(all, "23501").size)
    }

    @Test
    fun 空关键字等于不过滤() {
        val r = AccountSearch.filter(all, "   ")
        assertEquals(all.size, r.size)
        // 恒按时间倒序：最新的在最上面
        assertEquals(午饭.id, r[0].id)
        assertEquals(去年的饭.id, r[r.size - 1].id)
    }

    @Test
    fun 本月边界() {
        val r = AccountSearch.filter(all, "", AccountSearch.Range.THIS_MONTH, today.timeInMillis)
        assertEquals(listOf(午饭.id, 打车.id, 工资.id).sorted(), r.map { it.id }.sorted())
        val bounds = AccountSearch.rangeBounds(AccountSearch.Range.THIS_MONTH, today.timeInMillis)!!
        assertEquals(cal(2026, 10, 1, 0).timeInMillis, bounds.first)
        assertEquals(cal(2026, 11, 1, 0).timeInMillis - 1, bounds.last)
    }

    @Test
    fun 上月边界会跨年() {
        val jan = cal(2026, 0, 5)        // 2026-01-05，上月是 2025-12
        val bounds = AccountSearch.rangeBounds(AccountSearch.Range.LAST_MONTH, jan.timeInMillis)!!
        assertEquals(cal(2025, 11, 1, 0).timeInMillis, bounds.first)
        assertEquals(cal(2026, 0, 1, 0).timeInMillis - 1, bounds.last)
        val nov = AccountSearch.rangeBounds(AccountSearch.Range.LAST_MONTH, today.timeInMillis)!!
        assertEquals(cal(2026, 9, 1, 0).timeInMillis, nov.first)
        assertEquals(cal(2026, 10, 1, 0).timeInMillis - 1, nov.last)
    }

    @Test
    fun 今年边界() {
        val r = AccountSearch.filter(all, "", AccountSearch.Range.THIS_YEAR, today.timeInMillis)
        assertFalse(r.any { it.id == 去年的饭.id })
        assertEquals(4, r.size)
        val bounds = AccountSearch.rangeBounds(AccountSearch.Range.THIS_YEAR, today.timeInMillis)!!
        assertEquals(cal(2026, 0, 1, 0).timeInMillis, bounds.first)
        assertEquals(cal(2027, 0, 1, 0).timeInMillis - 1, bounds.last)
    }

    @Test
    fun 收支方向三态互斥() {
        // 审查 6.5：旧版只有「只看收入」一个开关，筛不出「只看支出」，关掉时也看不出当前处在哪种状态。
        val 收支 = AccountSearch.filter(all, "", kind = AccountSearch.Kind.BOTH)
        val 支出 = AccountSearch.filter(all, "", kind = AccountSearch.Kind.EXPENSE)
        val 收入 = AccountSearch.filter(all, "", kind = AccountSearch.Kind.INCOME)

        assertEquals(5, 收支.size)
        assertEquals(4, 支出.size)
        assertTrue(支出.none { it.type == 1 })
        assertEquals(1, 收入.size)
        assertEquals(工资.id, 收入[0].id)
        // 两态加起来正好是全量：既没漏也没重叠
        assertEquals(收支.size, 支出.size + 收入.size)
    }

    @Test
    fun 收支方向与时间范围标签可以叠加() {
        val r = AccountSearch.filter(
            all, "", range = AccountSearch.Range.THIS_MONTH, kind = AccountSearch.Kind.EXPENSE
        )
        assertEquals(2, r.size)                       // 本月（11 月）的午饭 + 打车，工资是收入被筛掉
        assertTrue(r.all { it.type == 0 && it.date >= cal(2026, 10, 1, 0).timeInMillis })

        // 上月的支出单独筛得出来（旧版「只看收入」做不到这件事）
        val last = AccountSearch.filter(
            all, "", range = AccountSearch.Range.LAST_MONTH, kind = AccountSearch.Kind.EXPENSE
        )
        assertEquals(1, last.size)
        assertEquals(上月的饭.id, last[0].id)
    }

    @Test
    fun 标签筛选() {
        val 吃饭 = AccountSearch.filter(all, "", tag = "吃饭")
        assertEquals(3, 吃饭.size)
        assertTrue(吃饭.all { it.tag == "吃饭" })

        // 标签 + 关键字同时生效
        assertEquals(1, AccountSearch.filter(all, "工作", tag = "吃饭").size)
    }

    @Test
    fun 金额文本不会打出负数() {
        assertEquals("0.00", AccountSearch.amountText(0))
        assertEquals("12.34", AccountSearch.amountText(1234))
        assertEquals("92233720368547758.08", AccountSearch.amountText(Long.MIN_VALUE))
        // 负号在搜索里不该出现（支出的金额本身是正数，符号只用于显示）
        assertFalse(AccountSearch.amountText(Long.MIN_VALUE).startsWith("-"))
    }

    /**
     * 7.5.5（复核报告 6.1）：收支方向判定写成显式分支。
     *
     * 旧写法是 `(kind == INCOME) == (type == 1)` —— 在 type 只有 0/1 时等价，
     * 但把「不是收入」默默等同于「支出」。这里把三种取值的行为钉死：
     * 0 与 1 是正常值，未知取值按支出处理（与旧行为一致，不会突然少数据）。
     */
    @Test
    fun 收支方向判定包含非法类型() {
        assertEquals(true, AccountSearch.kindMatches(AccountSearch.Kind.BOTH, 0))
        assertEquals(true, AccountSearch.kindMatches(AccountSearch.Kind.BOTH, 1))
        assertEquals(true, AccountSearch.kindMatches(AccountSearch.Kind.BOTH, 7))   // 未知也照常出现

        assertEquals(true, AccountSearch.kindMatches(AccountSearch.Kind.EXPENSE, 0))
        assertEquals(false, AccountSearch.kindMatches(AccountSearch.Kind.EXPENSE, 1))
        assertEquals(true, AccountSearch.kindMatches(AccountSearch.Kind.EXPENSE, 7)) // 未知按支出

        assertEquals(false, AccountSearch.kindMatches(AccountSearch.Kind.INCOME, 0))
        assertEquals(true, AccountSearch.kindMatches(AccountSearch.Kind.INCOME, 1))
        assertEquals(false, AccountSearch.kindMatches(AccountSearch.Kind.INCOME, 7))
    }
}
