package com.rongyu.shixuji

import com.rongyu.shixuji.ui.centsToYuanString
import com.rongyu.shixuji.ui.formatCents
import com.rongyu.shixuji.ui.parseYuanToCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
