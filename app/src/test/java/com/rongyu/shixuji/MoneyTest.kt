package com.rongyu.shixuji

import com.rongyu.shixuji.ui.centsToYuanString
import com.rongyu.shixuji.ui.formatCents
import com.rongyu.shixuji.ui.parseYuanToCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 金额换算的回归测试。
 * 这几条正是曾经出过问题的地方：用 (元*100).toLong() 会让 0.29 变成 28 分。
 */
class MoneyTest {

    @Test
    fun 分转显示文本正确() {
        assertEquals("¥0.00", formatCents(0))
        assertEquals("¥0.29", formatCents(29))
        assertEquals("¥12.34", formatCents(1234))
        assertEquals("-¥0.07", formatCents(-7))
    }

    @Test
    fun 元转分不会丢掉一分钱() {
        assertEquals(29L, parseYuanToCents("0.29"))
        assertEquals(115L, parseYuanToCents("1.15"))
        assertEquals(887L, parseYuanToCents("8.87"))
        assertEquals(7L, parseYuanToCents("0.07"))
        assertEquals(213L, parseYuanToCents("2.13"))
        assertEquals(1000L, parseYuanToCents("10"))
        assertEquals(50L, parseYuanToCents("¥0.5"))
        assertEquals(123456L, parseYuanToCents("1,234.56"))
    }

    @Test
    fun 超过两位小数按四舍五入到分() {
        assertEquals(315L, parseYuanToCents("3.145"))
        assertEquals(100L, parseYuanToCents("0.999"))
    }

    @Test
    fun 非法输入返回null() {
        assertNull(parseYuanToCents(""))
        assertNull(parseYuanToCents("."))
        assertNull(parseYuanToCents("-"))
        assertNull(parseYuanToCents("-5"))
        assertNull(parseYuanToCents("abc"))
    }

    @Test
    fun 分转元字符串用于编辑框预填() {
        assertEquals("12.34", centsToYuanString(1234))
        assertEquals("0.29", centsToYuanString(29))
        assertEquals("0.00", centsToYuanString(0))
    }

    @Test
    fun 元转分再转回文本保持一致() {
        listOf("0.29", "1.15", "8.87", "99.99", "1000.00").forEach { s ->
            val cents = parseYuanToCents(s)!!
            assertEquals(s, centsToYuanString(cents))
        }
    }

    /**
     * 边界值：老实现用 kotlin.math.abs(cents)，Long.MIN_VALUE 取绝对值还是负数（补码溢出），
     * 会打印出 "-¥-92233720368547758.08" 这种负的金额主体。
     */
    @Test
    fun 金额极端值不会出现负的主体() {
        assertEquals("¥0.00", formatCents(0))
        assertEquals("-¥0.01", formatCents(-1))
        assertEquals("¥1.00", formatCents(100))
        assertEquals("-¥1.00", formatCents(-100))
        assertEquals("¥92233720368547758.07", formatCents(Long.MAX_VALUE))
        assertEquals("-¥92233720368547758.08", formatCents(Long.MIN_VALUE))
        // 每个数字都以数字符号开头（借「正/负号后面紧跟货币符号」验证主体不是负数）
        listOf(0L, 1L, -1L, 99L, -99L, Long.MAX_VALUE, Long.MIN_VALUE).forEach {
            val s = formatCents(it)
            assertTrue("$it → $s 主体里出现了第二个负号", s.count { c -> c == '-' } <= 1)
        }
    }

    /** 金额显示不能跟着系统语言变：阿拉伯语等「本地数字形」语言下 %d 会输出 ٩٢٣ */
    @Test
    fun 金额显示不受系统语言影响() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("¥12.34", formatCents(1234))
            assertEquals("-¥0.07", formatCents(-7))
            assertEquals("-¥92233720368547758.08", formatCents(Long.MIN_VALUE))
            Locale.setDefault(Locale.forLanguageTag("hi-IN"))
            assertEquals("¥12.34", formatCents(1234))
        } finally {
            Locale.setDefault(old)
        }
    }
}
