package com.rongyu.shixuji.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * 分节「展开 / 收起」的**单一进度模型**（6.9.31 起）。
 *
 * 旧做法：每条待办各自一个 `AnimatedVisibility`，各自一套 enter/exit 时钟，
 * 靠 `delayMillis` 错峰。问题是时钟有 N 个：任何一处起点/结束条件不一致，
 * 观感就是「多张卡片各演各的」；而且总时长会随条目数线性变长。
 *
 * 新做法：一个分节只有一个 [androidx.compose.animation.core.Animatable] 进度 p∈[0,1]，
 * 每一条的可见高度、文字透明度、箭头角度、相邻分节间距全部从 p 派生。
 * 主时钟可打断（Animatable.animateTo 被新目标打断时从当前值继续），
 * 总时长恒等于主时长，与条目数无关。
 *
 * 本文件只放三样东西：
 *  ① 三个纯函数（错峰起点 / 条目局部进度 / 内容透明度）——可单测，见 SectionRevealMathTest；
 *  ② `AnimatedReveal`：按进度报告真实布局高度的容器（参与 LazyColumn 测量，不是 scaleY）；
 *  ③ `Modifier.revealAlpha`：把透明度交给绘制阶段。
 */

// ── 纯计算 ────────────────────────────────────────────────────────────────

/**
 * 第 [index] 条（0 起）在分节进度上的起点，范围 0..[staggerSpan]。
 *
 * 语义：起点越大 ⇒ 越晚开始变高（展开时靠后），也越早收完（收起时靠前）。
 * 所以**同一个映射天然给出「展开自上而下、收起自下而上」**，不需要两套公式。
 */
fun itemStartProgress(index: Int, itemCount: Int, staggerSpan: Float): Float {
    if (itemCount <= 1) return 0f
    val span = if (staggerSpan.isNaN()) 0f else staggerSpan.coerceIn(0f, 0.95f)
    if (span == 0f) return 0f
    val i = index.coerceIn(0, itemCount - 1)
    return i.toFloat() / (itemCount - 1).toFloat() * span
}

/**
 * 由分节进度得到某一条的局部进度 0..1。
 * p 到达该条的起点才开始动，p=1 时所有条目都到 1（不会留下「永远差一点」的尾巴）。
 */
fun itemLocalProgress(sectionProgress: Float, startProgress: Float): Float {
    val p = if (sectionProgress.isNaN()) 0f else sectionProgress.coerceIn(0f, 1f)
    val s = if (startProgress.isNaN()) 0f else startProgress.coerceIn(0f, 0.95f)
    if (p <= s) return 0f
    return ((p - s) / (1f - s)).coerceIn(0f, 1f)
}

/**
 * 内容透明度：局部进度很小时（卡片还不够高、字会被裁）保持全透明，
 * 到 [fadeStart]+[fadeRange] 时到 1。收起时同一函数自动反向。
 */
fun contentAlpha(
    localProgress: Float,
    fadeStart: Float = 0.08f,
    fadeRange: Float = 0.64f
): Float {
    val lp = if (localProgress.isNaN()) 0f else localProgress.coerceIn(0f, 1f)
    val start = fadeStart.coerceIn(0f, 0.9f)
    val range = if (fadeRange <= 0f || fadeRange.isNaN()) 1f else fadeRange
    return ((lp - start) / range).coerceIn(0f, 1f)
}

/** 分节间距（如「无期限」标题上边距 4dp→12dp）同样从进度派生，不另起一个动画。 */
fun sectionGapDp(progress: Float, collapsed: Float, expanded: Float): Float {
    val p = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    return collapsed + (expanded - collapsed) * p
}

// ── 按进度报告真实高度的容器 ─────────────────────────────────────────────

/**
 * 把 [content] 的自然高度按 [progress] 裁出来，**并且把裁后的高度报给父布局**。
 *
 * 和 `Modifier.graphicsLayer { scaleY = p }` 的区别：后者只改绘制，
 * LazyColumn 仍按原高度排版，下面的行会提前占位、出现空白或重叠。
 * 这里是在 measure 阶段就把高度改成 `fullHeight * p`，所以下面的行每一帧都跟着真实地上下移动。
 *
 * 性能：进度在 measure 阶段读取 —— 只触发重新测量，不触发重组（比把 p 读进 composition 便宜得多）。
 * 高度为 0 时不摆放子节点：不会留下可点击 / 可聚焦 / 可被 TalkBack 读到的透明区域。
 */
@Composable
fun AnimatedReveal(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(modifier = modifier.clipToBounds(), content = content) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(0, 0) {}
        }
        // 子项按自然高度量（不限最大高度），裁剪由我们自己按进度做
        val child = measurables.first().measure(
            Constraints(
                minWidth = 0,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = Constraints.Infinity
            )
        )
        val p = progress().let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) }
        val full = child.height
        val visible = when {
            p <= 0f -> 0
            p >= 1f -> full
            else -> (full * p).roundToInt().coerceIn(0, full)
        }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else child.width
        layout(width, visible) {
            if (visible > 0) child.placeRelative(0, 0)
        }
    }
}

/**
 * 把透明度交给绘制阶段（不触发重组/重测）。
 * 只在确实需要额外一层时使用；能合并进已有的 `graphicsLayer` 就别叠第二层。
 */
fun Modifier.revealAlpha(alpha: () -> Float): Modifier = graphicsLayer {
    this.alpha = alpha().let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) }
}
