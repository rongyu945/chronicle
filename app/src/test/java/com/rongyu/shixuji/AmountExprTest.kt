package com.rongyu.shixuji

import com.rongyu.shixuji.data.AmountExpr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 金额算式（5.7.0 新增）。口径见 [AmountExpr] 的注释：
 * 只认 + - * / 与括号、结果四舍五入到分、负数/除零/括号不配对一律 null。
 */
class AmountExprTest {

    @Test fun `纯数字不算算式`() {
        assertFalse(AmountExpr.isExpression("12.34"))
        assertFalse(AmountExpr.isExpression(""))
        assertTrue(AmountExpr.isExpression("12.34+1"))
    }

    @Test fun `加减乘除与优先级`() {
        assertEquals(2050L, AmountExpr.evalToCents("12.5+8"))
        assertEquals(6000L, AmountExpr.evalToCents("100-20*2"))
        assertEquals(16915L, AmountExpr.evalToCents("199*0.85"))
        assertEquals(250L, AmountExpr.evalToCents("(8+2)/4"))
        assertEquals(35000L, AmountExpr.evalToCents("100/2+300/1"))   // 50 + 300
    }

    @Test fun `除法四舍五入到分`() {
        // 128 / 3 = 42.6666… → 42.67
        assertEquals(4267L, AmountExpr.evalToCents("128/3"))
        // 10 / 3 = 3.3333… → 3.33
        assertEquals(333L, AmountExpr.evalToCents("10/3"))
        // 1.005 元的算式结果按分四舍五入（不是向下截断）
        assertEquals(101L, AmountExpr.evalToCents("1.005*1"))
    }

    @Test fun `没有浮点误差`() {
        // Double 下 0.1+0.2 = 0.30000000000000004，乘 100 截断会变 30（侥幸对），
        // 但 1.1+2.2 之类会出错；这里统一走 BigDecimal，三毛就是三毛。
        assertEquals(30L, AmountExpr.evalToCents("0.1+0.2"))
        assertEquals(330L, AmountExpr.evalToCents("1.1+2.2"))
        assertEquals(300L, AmountExpr.evalToCents("0.1*30"))
    }

    @Test fun `全角符号也能算`() {
        assertEquals(2400L, AmountExpr.evalToCents("12×2"))
        assertEquals(600L, AmountExpr.evalToCents("12÷2"))
        assertEquals(2000L, AmountExpr.evalToCents("（8+2）×2"))
    }

    @Test fun `负结果不认`() {
        assertNull(AmountExpr.evalToCents("5-10"))
        assertNull(AmountExpr.evalToCents("-5"))
    }

    @Test fun `写坏了就返回 null`() {
        assertNull(AmountExpr.evalToCents("12+"))
        assertNull(AmountExpr.evalToCents("(8+2"))
        assertNull(AmountExpr.evalToCents("8+2)"))
        assertNull(AmountExpr.evalToCents("1/0"))
        assertNull(AmountExpr.evalToCents("1.2.3"))
        assertNull(AmountExpr.evalToCents(""))
        assertNull(AmountExpr.evalToCents("+"))
    }

    @Test fun `超过上限不认（与普通金额同一个上限）`() {
        assertEquals(99_999_999_999L, AmountExpr.evalToCents("999999999.99"))
        assertNull(AmountExpr.evalToCents("1000000000"))
    }

    @Test fun `输入过滤只留算式字符`() {
        assertEquals("12+3", AmountExpr.sanitize("12+3"))
        assertEquals("12+3", AmountExpr.sanitize("12＋3"))
        assertEquals("12+3", AmountExpr.sanitize("12abc+3"))
        assertEquals("", AmountExpr.sanitize("午饭"))
        // 长度上限：不会让人往框里粘一篇文章
        assertTrue(AmountExpr.sanitize("1".repeat(200)).length <= 48)
    }

    /**
     * 2026-11-08 审计：`Char.isDigit()` 对全角 `'１'` 也是 true，
     * 于是全角数字被解析器吃进数字段、再交给 `BigDecimal("１")` 抛异常 ——
     * 用户输入的是合法金额却只看到「算式无效」。现在 normalize 先归一成全角→ASCII。
     */
    @Test
    fun 全角数字与全角小数点归一() {
        assertEquals("12+3", AmountExpr.sanitize("１２＋３"))
        assertEquals(1500L, AmountExpr.evalToCents("１２＋３"))
        assertEquals(1250L, AmountExpr.evalToCents("１２．５"))
        // 归一后的算式仍要能算
        assertEquals(500L, AmountExpr.evalToCents("１0 ＊ 0.5"))
    }

    /**
     * 7.5.1（外部审查报告 P1-A）：旧实现每次除法都 `MathContext(12)` 截断，
     * 误差在后续乘法里被放大 —— 下面这条算式因此少 1 分（.136 → 舍成 .14，真值是 .1342857… → .13）。
     * 现在全程精确有理数，只在转「分」时舍入一次。
     */
    @Test
    fun 除法链只在最后舍入到分() {
        assertEquals(85_714_285_713L, AmountExpr.evalToCents("999999999.99/7*6"))
        // 同一个式子的另一条写法：先算 6/7 再乘，结果必须一样
        assertEquals(85_714_285_713L, AmountExpr.evalToCents("999999999.99*(6/7)"))
        // 循环小数乘回原数：数学上是整数，不能因为「差一点」舍成少一分
        assertEquals(100L, AmountExpr.evalToCents("1/3*3"))
        assertEquals(200L, AmountExpr.evalToCents("2/3*3"))
        assertEquals(100L, AmountExpr.evalToCents("1/7*7"))
        assertEquals(500L, AmountExpr.evalToCents("10/6*3"))
        assertEquals(500L, AmountExpr.evalToCents("(10/6)*3"))
        // 多次连除再乘回
        assertEquals(100L, AmountExpr.evalToCents("1/3/7*21"))
    }

    @Test
    fun 恰好落在半分时向上取整() {
        assertEquals(1L, AmountExpr.evalToCents("0.005"))       // 0.005 元 = 半分 → 1 分
        assertEquals(3L, AmountExpr.evalToCents("0.025"))       // 2.5 分 → 3 分
        assertEquals(0L, AmountExpr.evalToCents("0.004"))
        assertEquals(0L, AmountExpr.evalToCents("1/400"))       // 0.0025 元 = 0.25 分 → 0
        assertEquals(1L, AmountExpr.evalToCents("1/200"))       // 0.005 元 = 0.5 分 → 1
    }

    /**
     * 7.5.3：自绘小键盘把乘除写成 `×` `÷`（中文记账习惯写法），
     * 解析器必须先归一成 `*` `/` 再算 —— 否则用户按了键盘却提示「算式无效」。
     */
    @Test fun `小键盘的乘除符号能直接算`() {
        assertEquals(2500L, AmountExpr.evalToCents("12.5×2"))   // 12.5 × 2 = 25.00
        assertEquals(1600L, AmountExpr.evalToCents("128÷8"))   // 128 ÷ 8 = 16.00
        assertEquals(85_714_285_713L, AmountExpr.evalToCents("999999999.99÷7×6"))
        assertEquals(500L, AmountExpr.evalToCents("(8+2)÷2"))
        assertEquals(750L, AmountExpr.evalToCents("12.5×0.6"))
    }

    @Test fun `小键盘的减号用的是全角减号也能算`() {
        // 键盘上那颗「−」是 U+2212 还是 U+FF0D 都归一成 ASCII 减号
        assertEquals(600L, AmountExpr.evalToCents("10−4"))
        assertEquals(600L, AmountExpr.evalToCents("10－4"))
    }

    @Test fun `显示过滤保留乘除符号而求值仍然正确`() {
        assertEquals("128÷3×6", AmountExpr.sanitizeDisplay("128÷3×6"))
        assertEquals("128/3*6", AmountExpr.sanitizeDisplay("128/3*6"))   // ASCII 写法也照收
        assertEquals("12.5+8", AmountExpr.sanitizeDisplay("12.5+8汉字"))
        assertEquals(25_600L, AmountExpr.evalToCents(AmountExpr.sanitizeDisplay("128÷3×6")))
    }

    /**
     * 7.5.5（复核报告 2.1 / 8.2）：失败要**分得清**，不能一律「算式看不懂」。
     * 界面据此就地解释，用户改掉那个字符提示就消失。
     */
    @Test fun `失败原因分得清`() {
        fun kind(t: String) = (AmountExpr.evaluate(t) as AmountExpr.Eval.Bad).kind
        assertEquals(AmountExpr.ErrorKind.DIV_ZERO, kind("1/0"))
        assertEquals(AmountExpr.ErrorKind.DIV_ZERO, kind("12/(3-3)"))
        assertEquals(AmountExpr.ErrorKind.INCOMPLETE, kind("12÷"))
        assertEquals(AmountExpr.ErrorKind.INCOMPLETE, kind("(12+"))
        assertEquals(AmountExpr.ErrorKind.PAREN, kind("(12+3"))
        assertEquals(AmountExpr.ErrorKind.NEGATIVE, kind("3-10"))
        assertEquals(AmountExpr.ErrorKind.RANGE, kind("999999999.99*100"))
        assertEquals(AmountExpr.ErrorKind.SYNTAX, kind("12..5"))
        assertEquals(AmountExpr.ErrorKind.INCOMPLETE, kind(""))
        // 成功的分支必须仍然是 Ok，且值与 evalToCents 一致
        assertEquals(2500L, (AmountExpr.evaluate("12.5×2") as AmountExpr.Eval.Ok).cents)
        assertEquals(AmountExpr.evalToCents("12.5×2")!!,
            (AmountExpr.evaluate("12.5×2") as AmountExpr.Eval.Ok).cents)
    }

    /** 连续除法/括号：不能每一步先舍入到分（复核报告 2.1 的第 1、3 条） */
    @Test fun `连续除法与括号都不提前舍入`() {
        assertEquals(111L, AmountExpr.evalToCents("10/3/3"))        // 1.1111… → 1.11
        assertEquals(204L, AmountExpr.evalToCents("100/7/7"))      // 2.0408… → 2.04
        assertEquals(1000L, AmountExpr.evalToCents("(10/3)*3"))    // 10.00 整（先舍入会变 9.99）
        assertEquals(111L, AmountExpr.evalToCents("10/(3*3)"))     // 1.1111… → 1.11
    }

    /** 上限与负数都在「转成分之后」判，不足一分的正数不会被误伤（复核报告 2.1 的第 4、8 条） */
    @Test fun `上限与负数的边界`() {
        assertEquals(99_999_999_999L, AmountExpr.evalToCents("999999999.99"))
        assertNull(AmountExpr.evalToCents("999999999.995"))        // 舍入后越界
        assertEquals(0L, AmountExpr.evalToCents("0.001*1"))        // 不足一分 → 0，不是失败
        assertNull(AmountExpr.evalToCents("0-1"))                  // 负数
    }

    /**
     * 7.5.5（复核报告 2.1）：把「只在最后舍入一次」这件事钉死 —— 每一步都先舍入到分，
     * 连除的结果会明显偏大。
     */
    @Test
    fun 连除与括号只在最后舍入一次() {
        // 10/3/3 真值 1.1111… 元 = 111.11 分 → 111；若每步舍入：10/3→333 分，/3→111 分（同值，看不出差别）
        assertEquals(AmountExpr.Eval.Ok(111L), AmountExpr.evaluate("10/3/3"))
        // 100/7/7 真值 2.0408 元 = 204.08 分 → 204；每步舍入会得到 204 也相同，但下面的连乘混合会拉开
        assertEquals(AmountExpr.Eval.Ok(204L), AmountExpr.evaluate("100/7/7"))
        assertEquals(AmountExpr.Eval.Ok(1000L), AmountExpr.evaluate("(10/3)*3"))
        assertEquals(AmountExpr.Eval.Ok(111L), AmountExpr.evaluate("10/(3*3)"))
        assertEquals(AmountExpr.Eval.Ok(25600L), AmountExpr.evaluate("128/3*6"))
    }

    /** 半分边界：恰好在 0.5 分上要进位（HALF_UP），不能银行家舍入也不能截断 */
    @Test
    fun 半分边界向上进位() {
        assertEquals(AmountExpr.Eval.Ok(1L), AmountExpr.evaluate("0.005"))
        assertEquals(AmountExpr.Eval.Ok(101L), AmountExpr.evaluate("1.005"))
        assertEquals(AmountExpr.Eval.Ok(2L), AmountExpr.evaluate("0.015"))
    }

    /** 上限是硬边界：正好到顶能存，超一分就拒 */
    @Test
    fun 上限边界() {
        assertEquals(AmountExpr.Eval.Ok(AmountExpr.MAX_CENTS), AmountExpr.evaluate("999999999.99"))
        assertEquals(AmountExpr.Eval.Bad(AmountExpr.ErrorKind.RANGE), AmountExpr.evaluate("999999999.99*1.01"))
        // 负数一律拒绝（负数金额在库里用 type 表达，不靠符号）
        assertEquals(AmountExpr.Eval.Bad(AmountExpr.ErrorKind.NEGATIVE), AmountExpr.evaluate("0-0.005"))
    }
}
