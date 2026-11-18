package com.rongyu.shixuji.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「底部那套 chrome（玻璃底栏 + 悬浮球）现在该不该在台上」。
 *
 * 2026-11-06 对照 Apple 设计准则审查发现：弹窗打开时背景整片糊掉了，底栏却依然锐利、
 * 还能点——看起来像模态、实际不是模态。Apple 的说法是 modal 要把背景推后压暗，
 * 而这里只推了背景、没退底栏。整理页同理：它自己有「取消」，底栏等于第二条退出路径，
 * 还占着操作条最需要的位置。
 *
 * 用法：谁进入全屏/模态态，谁在 DisposableEffect 里用**自己独有的 token** 占台，
 * 离开时用同一个 token 退场。MainScreen 读 [hidden] 决定底栏和悬浮球的透明度与位移。
 *
 * ⚠️ 2026-11-10 修过一个「退出整理页底栏永久消失」的 bug —— 语义只能是**按身份登记**，
 * 不能是加减计数：
 *
 *   // 错：两个坑叠在一起
 *   DisposableEffect(batching) {
 *       if (batching) ChromeState.hold()
 *       onDispose { if (batching) ChromeState.release() }   // ← 这里读的是**最新值**
 *   }
 *
 * ① `by remember { mutableStateOf(...) }` 是委托属性，闭包捕获的是 State 对象本身，
 *    在 onDispose 里读 `batching` 读到的永远是**当前值**，不是这一次 effect 建立时快照。
 *    于是 true→false 时 onDispose 读到 false、不 release；而 false→true 时 onDispose
 *    读到 true、白 release 一次。进出一次，计数净 +1 且永不归零，`hidden` 就永久为 true
 *    —— 底栏 alpha 卡在 0，看着像「彻底消失」（占位还在，但没有玻璃条、没有三个标签）。
 * ② 计数本身也不幂等：多做一次 release 会把别的占台方一起清掉，少做一次就永久漏。
 *    `coerceAtLeast(0)` 只是把错误掩盖成「看起来没崩」。
 *
 * 按 token 登记两个方向都安全：同一个 token 重复 release 只是 remove 一个不存在的元素。
 */
internal object ChromeState {
    var hidden by mutableStateOf(false)
        private set

    /** 谁在占台。多界面同时占台时，最后一个走才算散场。 */
    private val owners = mutableSetOf<Any>()

    fun hold(owner: Any) {
        owners.add(owner)
        hidden = true
    }

    fun release(owner: Any) {
        owners.remove(owner)
        hidden = owners.isNotEmpty()
    }

    /** 兜底：重组丢失配对时强制散场（防止 chrome 永久不见）。 */
    fun reset() {
        owners.clear()
        hidden = false
    }

    /** 调试用：当前占台方数量。 */
    internal val ownerCount: Int get() = owners.size
}
