package com.rongyu.shixuji.ui

import android.view.WindowManager
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.rongyu.shixuji.ui.motion.LocalReducedMotion

/** 系统输入法（或自绘键盘）与弹窗之间固定留的缝：12dp —— 与键盘面板那档一致 */
private val IME_GAP = 12.dp

/**
 * 弹窗在屏幕上的**垂直定位**（7.7.6）。
 *
 * 用户：「当输入框出现的时候，弹窗带有动画的自然上移，从而和键盘之间相隔一点距离」。
 *
 * 做法：window 设 [WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING]（系统别插手），
 * IME 只作为 inset 上报；这里读 [WindowInsets.ime] 用 [animateDpAsState] 缓动让位。
 * 为什么不让系统 adjustResize：那是一次**跳变**（实测弹窗瞬移 148px、中间没有任何过渡），
 * 间距还随输入法高度乱变；而完全不接管的话弹窗底部被键盘盖住，保存按钮点不到。
 *
 * 几何要点（三个坑，都实测踩过）：
 *  1. 不能用「Box 的 alignment + 底部 padding」：居中对齐时底部 padding 只把卡片往中间挤，
 *     实际只上移**一半** —— 实测 IME 起来后弹窗底边与键盘顶只差 2px。
 *     所以改成 `layout` 直接把玻璃体摆到「容器高 − 让位量 − 玻璃高」。
 *  2. 这个 `layout` 必须是 modifier 链的**最外层**（要写在 glassDialogModifier() 之前）：
 *     写在后面就成了「在 Surface 内部摆放正文」，place 的 y 会被当成内容偏移、正文被推出卡片。
 *  3. 基准取 **layout 约束的 maxHeight**（= Dialog 内容视图高），不要换算屏幕高度：
 *     Dialog 的内容视图并不从屏幕顶端开始、底边又比屏幕底高出一条导航条，
 *     用 currentWindowMetrics 去算会整体错 30~190px。IME inset 恰好是相对**同一条底边**的，
 *     两者天然对齐，所以让位量就是 `ime + IME_GAP`，不用再补导航条。
 *
 * @param shadowPadV 玻璃体外侧的阴影画布留白（= 传进来的 shadowPadV）
 * @param bottomClearance 底部固定让位（自绘键盘面板那档）；0 表示垂直居中
 */
@Composable
fun Modifier.dialogPlacement(bottomClearance: Dp, shadowPadV: Dp): Modifier {
    // 自绘键盘面板升起时弹窗是**不动的**（用户 7.7.3 明确要求），所以这里只处理系统输入法。
    val view = LocalView.current
    DisposableEffect(view) {
        (view.parent as? DialogWindowProvider)?.window?.let { w ->
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            // 关掉 decor 自己处理 insets，IME 高度才会以「inset」形式报到 Compose
            // （窗口既不 resize 也不上报 → 两边都不让位，键盘直接把弹窗盖掉）
            WindowCompat.setDecorFitsSystemWindows(w, false)
        }
        onDispose { }
    }
    val density = LocalDensity.current
    val imeDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val imeClearance = if (imeDp > 0.dp) imeDp + IME_GAP else 0.dp
    // 取两者较大：系统输入法在时按 IME 让位，自绘键盘在时按面板让位，两者不会同时出现
    val reducedMotion = LocalReducedMotion.current
    val animatedClearance by animateDpAsState(
        targetValue = maxOf(bottomClearance, imeClearance),
        animationSpec = if (reducedMotion) snap() else tween(260, easing = LinearOutSlowInEasing),
        label = "dialog-ime-lift"
    )
    return this.layout { measurable, c ->
        val p = measurable.measure(c)
        val padV = shadowPadV.roundToPx()
        val glassH = (p.height - 2 * padV).coerceAtLeast(0)
        val containerPx = c.maxHeight
        // 无输入法时「玻璃体底边离容器底多远」：让位型按 bottomClearance，其余垂直居中
        val baseBottom =
            if (bottomClearance > 0.dp) bottomClearance.roundToPx()
            else ((containerPx - glassH) / 2).coerceAtLeast(0)
        val targetBottom = maxOf(baseBottom, animatedClearance.roundToPx())
        val top = (containerPx - targetBottom - glassH - padV).coerceAtLeast(0)
        layout(c.maxWidth, c.maxHeight) {
            p.place((c.maxWidth - p.width) / 2, top)
        }
    }
}
