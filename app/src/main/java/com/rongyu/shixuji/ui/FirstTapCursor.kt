package com.rongyu.shixuji.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 光标落点的分工（用户 2026-11-02 明确要的效果）：
 *
 *  - **第一次**点进这个输入框 → 光标落到文本末尾（长备注一进来就看见尾巴）；
 *  - 之后的第二、第三次点击 → 完全交给输入框自己，点两个字之间就停在两个字之间。
 *
 * 判断依据是「按下那一刻这个框有没有焦点」：
 * 没焦点的那一下，就是把它点开的那一下 → 抬手后把光标设到末尾；
 * 已经有焦点的点击 → 什么都不做。
 *
 * 为什么要在 Final 通道做、还要等抬手：点击时 TextField 自己会在 Main 通道里按手指位置定位光标，
 * 我们在它之后（Final）改，才是**同一帧内确定性地最后生效**，不靠"延两帧"赌时序
 * （早前那版延帧写法在模拟器上通过、在真机上失效）。
 *
 * 为什么要 `pointerInput(Unit)` + `rememberUpdatedState` 之外的读法：
 * 三个 lambda 都是每次重组新建的，pointerInput 只保留第一次的那个，
 * 所以它们内部读的必须是 remember 下来的状态对象（MutableState / TextFieldValue 的 getter），
 * 不能捕获某个具体值。
 */
fun Modifier.firstTapCursorToEnd(
    isFocused: () -> Boolean,
    get: () -> TextFieldValue,
    set: (TextFieldValue) -> Unit
): Modifier = composed {
    val focusedNow by rememberUpdatedState(isFocused)
    val valueNow by rememberUpdatedState(get)
    val setNow by rememberUpdatedState(set)
    this.pointerInput(Unit) {
        awaitEachGesture {
            // 按下之前这个框有没有焦点 —— 决定这一次点击是不是「点开它的那一下」
            val alreadyFocused = focusedNow()
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Final)
                if (ev.changes.none { it.pressed }) break
            }
            if (!alreadyFocused) {
                val v = valueNow()
                val end = TextRange(v.text.length)
                if (v.selection != end) setNow(v.copy(selection = end))
            }
        }
    }
}
