package com.rongyu.shixuji.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 预算环里的百分比文字（用户 2026-11-17：超支时环里要写真实数字，可以超过 100）。
 * 「弧线夹满一圈、文字不夹」是两条独立的规则，这里只钉文字那条。
 */
class AccountPctTest {

    private fun pct(spent: Long, budget: Long) = pctText(spent.toFloat() / budget)

    @Test
    fun zeroAndTiny() {
        assertEquals("0", pct(0, 200_000))
        assertEquals("<1", pct(1, 200_000))      // 0.5%：取整成 0% 会被读成「一分钱没花」
        assertEquals("<1", pct(199_999, 20_000_000_000))
    }

    @Test
    fun normalRange() {
        assertEquals("50", pct(100_000, 200_000))
        assertEquals("99", pct(199_000, 200_000))
        assertEquals("100", pct(200_000, 200_000))
    }

    /** 核心：超过 100% 之后不能再被夹成 100 */
    @Test
    fun overBudgetKeepsRealNumber() {
        assertEquals("137", pct(274_000, 200_000))
        assertEquals("101", pct(202_000, 200_000))
        assertEquals("250", pct(500_000, 200_000))
        assertEquals("1250", pct(2_500_000, 200_000))   // 极端：位数多了靠字号缩
    }
}
