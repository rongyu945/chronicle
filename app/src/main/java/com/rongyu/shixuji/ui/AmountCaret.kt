package com.rongyu.shixuji.ui

import com.rongyu.shixuji.data.AmountExpr

/**
 * 金额输入框的状态：**文本 + 光标**（7.7.2）。
 *
 * 7.5.3 起金额由自绘键盘驱动，状态是一个裸 `String`，所有按键都只能「追加到末尾 / 退格删末尾」。
 * 复核报告 §4.3 指出的就是这件事：「改一个已经打好的数字，只能一路删到那一位」。
 * 现在把光标位置一并存下来，`insert / backspace` 都按光标走，点击金额框可以把光标挪到任意位置。
 *
 * 为什么不做成 `TextFieldValue`：金额框仍然**不是**可获焦的输入框（任何可获焦的输入框都会把
 * 系统输入法拉起来，两条键盘叠在一起既挤又乱，见 [AmountDisplay] 的注释）。这里只需要
 * 「一个整数 + 两个纯函数」，不引入焦点 / IME / 选区那一套。
 *
 * [caret] 的合法范围恒为 `0..text.length`，所有构造入口都做夹取，越界值绝不外流。
 */
internal data class AmountField(val text: String, val caret: Int) {

    /** 光标停在末尾（打字最常见的自然态）。 */
    val atEnd: Boolean get() = caret >= text.length

    companion object {
        /** 空输入框：光标在 0。 */
        val Empty = AmountField("", 0)

        /** 用一段已有文本建状态，光标停在末尾（编辑已有账目时用）。 */
        fun of(text: String): AmountField {
            val clean = AmountExpr.sanitizeDisplay(text)
            return AmountField(clean, clean.length)
        }
    }
}

/**
 * 金额输入的三个动作 + 光标定位，全部是纯函数（JVM 单测见 `AmountCaretTest`）。
 *
 * 不变式：
 * 1. 返回值的 `caret` 永远落在 `0..text.length`；
 * 2. 插入的键若整体是非法的（比如传进来一个字母），**文本与光标都不动**；
 * 3. 文本被 `sanitizeDisplay` 截断到长度上限时，光标跟着收到新的末尾，不会留在不存在的字符上。
 */
internal object AmountCaret {

    /**
     * 在光标处插入 [key]（一个键的字符串，可能是 `"("` 也可能是 `"÷"`）。
     *
     * 光标落在「刚插入内容的末尾」——连续按同一排数字键时，观感与追加到末尾完全一致。
     */
    fun insert(field: AmountField, key: String): AmountField {
        val text = field.text
        val caret = field.caret.coerceIn(0, text.length)
        // 先过一遍 sanitize：整键非法（字母、空格以外的怪字符）时什么都不改，
        // 否则光标会「往前走一格但屏幕上看不出变化」。
        if (AmountExpr.sanitizeDisplay(key).isEmpty()) return AmountField(text, caret)
        val raw = text.substring(0, caret) + key + text.substring(caret)
        val clean = AmountExpr.sanitizeDisplay(raw)
        // 想落到的位置：插入内容之后。被长度上限截断时退回末尾。
        val want = caret + key.length
        return AmountField(clean, want.coerceIn(0, clean.length))
    }

    /** 删掉光标**左边**一个字符；光标已在最前则只把位置归 0，文本不变。 */
    fun backspace(field: AmountField): AmountField {
        val text = field.text
        val caret = field.caret.coerceIn(0, text.length)
        if (caret == 0) return AmountField(text, 0)
        return AmountField(text.removeRange(caret - 1, caret), caret - 1)
    }

    /** 点「AC」：全清，光标回到 0。 */
    fun clear(): AmountField = AmountField.Empty

    /** 点金额框的某一处：把光标挪过去（越界夹取，不会因为测量误差抛异常）。 */
    fun moveTo(field: AmountField, caret: Int): AmountField =
        AmountField(field.text, caret.coerceIn(0, field.text.length))
}
