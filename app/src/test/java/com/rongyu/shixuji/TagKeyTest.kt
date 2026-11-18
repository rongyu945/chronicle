package com.rongyu.shixuji

import com.rongyu.shixuji.ui.tagKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 7.5.5（复核报告 2.6）：标签判重用的归一键。
 *
 * 账目/常用记账是按**标签名字符串**关联标签的，所以库里出现「吃饭」和「吃饭 」两条，
 * 就会出现：颜色互相覆盖、删一个把另一个的账目也清空。判重必须走同一套归一规则。
 * 注意归一**只用于比较**，显示名仍然是用户原样输入（前后空白除外）。
 */
class TagKeyTest {

    @Test
    fun 首尾空白与连续空白都不算区别() {
        assertEquals(tagKey("吃饭"), tagKey("吃饭 "))
        assertEquals(tagKey("吃饭"), tagKey("  吃饭\t"))
        assertEquals(tagKey("日常 开销"), tagKey("日常   开销"))
        assertEquals(tagKey("日常 开销"), tagKey("日常\u3000开销"))   // 全角空格
    }

    @Test
    fun 英文不分大小写_中文不受影响() {
        assertEquals(tagKey("Lunch"), tagKey("lunch"))
        assertEquals(tagKey("LUNCH"), tagKey("Lunch"))
        assertEquals(tagKey("买菜"), tagKey("买菜"))
        // 不同名字必须仍然不同（归一不能过度）
        assertNotEquals(tagKey("吃饭"), tagKey("吃饭钱"))
        assertNotEquals(tagKey("lunch"), tagKey("launch"))
    }
}
