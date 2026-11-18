package com.rongyu.shixuji.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 金额「文本 + 光标」的纯逻辑（7.7.2，复核报告 §4.3）。
 *
 * 这一层不碰 Compose：点框定位是 `TextLayoutResult` 的事，插/删/清是纯字符串的事，
 * 后者必须逐条钉住 —— 记账金额错一位就是真钱错一位。
 */
class AmountCaretTest {

    @Test
    fun insertAtEndAppends() {
        var f = AmountField.Empty
        f = AmountCaret.insert(f, "1")
        f = AmountCaret.insert(f, "2")
        f = AmountCaret.insert(f, "8")
        assertEquals("128", f.text)
        assertEquals(3, f.caret)
        assertTrue(f.atEnd)
    }

    @Test
    fun insertAtMiddleGoesWhereTheCaretIs() {
        // 光标在 1，输入 9 → 1|28 变 19|28（不是追加到末尾变成 1289）
        val f = AmountCaret.insert(AmountField("128", 1), "9")
        assertEquals("1928", f.text)
        assertEquals(2, f.caret)
    }

    @Test
    fun insertAtStartPrepends() {
        val f = AmountCaret.insert(AmountField("28", 0), "1")
        assertEquals("128", f.text)
        assertEquals(1, f.caret)
    }

    @Test
    fun backspaceDeletesLeftOfCaretNotLastChar() {
        // 光标在 3（1.5 后面），退格应删掉「5」而不是末尾的「0」
        val f = AmountCaret.backspace(AmountField("1.50", 3))
        assertEquals("1.0", f.text)
        assertEquals(2, f.caret)
    }

    @Test
    fun backspaceAtStartIsNoOp() {
        val f = AmountCaret.backspace(AmountField("128", 0))
        assertEquals("128", f.text)
        assertEquals(0, f.caret)
    }

    @Test
    fun backspaceOnEmptyStaysEmpty() {
        val f = AmountCaret.backspace(AmountField.Empty)
        assertEquals("", f.text)
        assertEquals(0, f.caret)
    }

    @Test
    fun clearResetsBoth() {
        val f = AmountCaret.clear()
        assertEquals("", f.text)
        assertEquals(0, f.caret)
    }

    @Test
    fun illegalKeyChangesNothing() {
        // 非法键：不能出现「光标往前走一格但屏幕上没变化」的鬼影
        val f = AmountCaret.insert(AmountField("128", 1), "a")
        assertEquals("128", f.text)
        assertEquals(1, f.caret)
    }

    @Test
    fun operatorsAndFullWidthMultiplySurvive() {
        val f = AmountCaret.insert(AmountField("12", 2), "×")
        val g = AmountCaret.insert(f, "3")
        assertEquals("12×3", g.text)
        assertEquals(4, g.caret)
    }

    @Test
    fun caretIsClampedToTextBounds() {
        // 越界光标（点击测量的边界情况）被夹到合法范围，不外流
        assertEquals(3, AmountCaret.moveTo(AmountField("128", 0), 99).caret)
        assertEquals(0, AmountCaret.moveTo(AmountField("128", 3), -5).caret)
        // 用越界光标插入也不许抛异常
        val f = AmountCaret.insert(AmountField("128", 99), "0")
        assertEquals("1280", f.text)
        assertEquals(4, f.caret)
        val g = AmountCaret.backspace(AmountField("128", -3))
        assertEquals("128", g.text)
        assertEquals(0, g.caret)
    }

    @Test
    fun lengthCapKeepsCaretInsideText() {
        // sanitizeDisplay 有 48 位上限：撞上限后光标必须收到新末尾，不能停在幻影位置
        val long = "1".repeat(50)
        val f = AmountCaret.insert(AmountField.of(long), "9")
        assertEquals(48, f.text.length)
        assertTrue("caret=${f.caret} 超出 text.length=${f.text.length}", f.caret <= f.text.length)
    }

    @Test
    fun ofPutsCaretAtEndAndSanitizes() {
        val f = AmountField.of("128.50")
        assertEquals("128.50", f.text)
        assertEquals(6, f.caret)
        assertTrue(f.atEnd)
        // 编辑已有金额时，夹带的非法字符（历史脏数据）一并清掉
        assertEquals("128", AmountField.of("12a8").text)
    }
}
