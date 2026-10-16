package com.rongyu.shixuji.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 点一下输入框 → 光标（和视图）一起落到文本最后。
 *
 * 为什么不能只在焦点回调里做：点击时 TextField 自己会把光标放到**手指点的位置**，
 * 那一手在焦点回调**之后**，直接改会被覆盖。
 *
 * 为什么不用「延两帧再改」：那是靠时间赌。模拟器（快）上连点两次的时序刚好输给它，
 * 真机（慢）上 TextField 的定位可能在第 3 帧才处理完，就把光标又拽回手指点的地方 ——
 * 同一份代码在模拟器通过、在用户手机上失效（被用户批评过「你怎么也听不懂」）。
 *
 * 正解=参与同一次事件分发，但走 **Final 通道**：TextField 的定位在 Main 通道处理，
 * Final 通道在它之后、同一帧内执行，所以我们这里是**确定性地最后生效**，不依赖帧数。
 *
 * 用法：`modifier = Modifier.fillMaxWidth().jumpCursorToEnd({ detailV }) { detailV = it }`
 */
fun Modifier.jumpCursorToEnd(
    get: () -> TextFieldValue,
    set: (TextFieldValue) -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        // Initial 通道不消费任何事件，只是"搭车"监听，不影响输入框自己收事件
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // 等手指抬起（Final 通道：即便事件已被 TextField 消费，我们仍能收到）
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Final)
            if (ev.changes.none { it.pressed }) break
        }
        val v = get()
        val end = TextRange(v.text.length)
        // 已经停在末尾就不重复赋值，避免无谓重组
        if (v.selection != end) set(v.copy(selection = end))
    }
}
