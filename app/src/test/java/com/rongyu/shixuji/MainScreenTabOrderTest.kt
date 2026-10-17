package com.rongyu.shixuji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 底栏顺序偏好（parseTabOrder）的回归测试。
 *
 * 用户存档里可能还带着已经删掉的「模型」（底栏曾经四项），
 * 解析必须**先按当前 Tab 集合过滤再校验数量**，否则永远对不上、只能回落默认顺序。
 */
class MainScreenTabOrderTest {

    @Test
    fun 合法顺序按原样返回() {
        assertEquals(listOf("账目", "待办", "设置"), parseTabOrder("账目,待办,设置"))
        assertEquals(listOf("待办", "账目", "设置"), parseTabOrder("待办,账目,设置"))
    }

    @Test
    fun 历史存档里已删除的模型项被过滤掉() {
        assertEquals(listOf("待办", "账目", "设置"), parseTabOrder("待办,账目,模型,设置"))
        assertEquals(listOf("设置", "待办", "账目"), parseTabOrder("设置,模型,待办,账目"))
    }

    @Test
    fun 缺项或多余项_回落默认() {
        assertNull(parseTabOrder("待办,账目"))
        assertNull(parseTabOrder("待办"))
        assertNull(parseTabOrder(""))
        assertNull(parseTabOrder("模型"))
    }

    @Test
    fun 重复项只算一次() {
        // 「待办,待办,账目,设置」过滤去重后正好三项 → 合法
        assertEquals(listOf("待办", "账目", "设置"), parseTabOrder("待办,待办,账目,设置"))
    }

    @Test
    fun 空白与逗号容错() {
        assertEquals(listOf("待办", "账目", "设置"), parseTabOrder(" 待办 , 账目 , 设置 "))
        assertEquals(listOf("待办", "账目", "设置"), parseTabOrder("待办,,账目,设置,"))
    }
}
