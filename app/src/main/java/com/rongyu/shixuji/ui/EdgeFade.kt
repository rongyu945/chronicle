package com.rongyu.shixuji.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rongyu.shixuji.ui.motion.Motion
import com.rongyu.shixuji.ui.motion.motionSpec

/**
 * 横向滚动内容的**两端渐隐**（记账弹窗的标签行用）。
 *
 * 做法不是「在内容上盖一层跟背景同色的渐变」——那种只在纯色背景上成立，
 * 一放到弹窗（有阴影/圆角/半透明）或深色模式上就会露出一块方形色块。
 * 这里用离屏层 + `BlendMode.DstIn` 直接**把内容本身擦淡**：
 * 擦掉的地方 alpha = 0，底下是什么就透出什么，任何背景都成立。
 *
 * 两端各自独立：贴到最左（或最右）时那一侧不渐隐 ——
 * 否则第一个标签左边永远糊着一块，看着像被裁掉了半个字。
 * 强度不做成"透明度"，而是做成**渐隐带的宽度**（强度 0 → 宽度 0），
 * 这样滚动状态下切换时是宽度平滑伸缩，不会出现整条带子一起变淡的怪相。
 */
@Composable
fun Modifier.horizontalEdgeFade(
    state: LazyListState,
    fade: Dp = 22.dp,
): Modifier {
    val canLeft by remember(state) {
        derivedStateOf {
            // 首条只要被裁掉一点点也算「还能往左滑」。
            // 只看 firstVisibleItemIndex>0 是不够的：滑到一半停住时首条 index 仍是 0，
            // 但它的左半截已经在屏幕外了，那时左边却没有渐隐。
            val info = state.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf false
            first.index > 0 || first.offset < info.viewportStartOffset
        }
    }
    val canRight by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            // ★ 同样不能只看 index：末条**露出一半**时它的 index 已经是 total-1 了，
            // 旧写法据此判「到底了」→ 不渐隐 → 那一半就被硬生生切在边上
            // （记账弹窗的标签行、搜索页的标签行都是这个症状：最后一个胶囊只有半个圆）。
            last.index < info.totalItemsCount - 1 ||
                last.offset + last.size > info.viewportEndOffset
        }
    }
    // 7.4.1（方案 §19.1）：180ms 是「渐隐带宽度」的变化，不是位移动画。
    // 这条带子一直跟着手指滚，任何可见的滞后都会读成"边缘在追着内容跑"——
    // 所以取 200ms Glide（前段快），并且走 motionSpec：减弱动效时瞬变。
    val left: Float by animateFloatAsState(if (canLeft) 1f else 0f, motionSpec(Motion.glide(200)), label = "edgeFadeL")
    val right: Float by animateFloatAsState(if (canRight) 1f else 0f, motionSpec(Motion.glide(200)), label = "edgeFadeR")
    return this.horizontalEdgeFade(left = left, right = right, fade = fade)
}

/** 直接给两个 0..1 的强度（不想绑 LazyListState 时用）。0 = 那一端不渐隐。 */
fun Modifier.horizontalEdgeFade(
    left: Float,
    right: Float,
    fade: Dp,
): Modifier {
    if (left <= 0.001f && right <= 0.001f) return this
    return this
        // 必须先开离屏层：DstIn 只作用在本图层内，否则会拿整窗口当目标去擦
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val maxW = size.width / 2f
            val wl = (fade.toPx() * left).coerceIn(0f, maxW)
            val wr = (fade.toPx() * right).coerceIn(0f, maxW)
            val w = size.width
            val stops = buildList<Pair<Float, Color>> {
                if (wl > 0f) {
                    add(0f to Color.Transparent)
                    add((wl / w) to Color.Black)
                } else {
                    add(0f to Color.Black)
                }
                if (wr > 0f) {
                    add((1f - wr / w) to Color.Black)
                    add(1f to Color.Transparent)
                } else {
                    add(1f to Color.Black)
                }
            }.toTypedArray()
            drawRect(
                brush = Brush.horizontalGradient(*stops),
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
}

/**
 * 纵向滚动内容的**顶部渐隐**（设置页用）。
 *
 * 要解决的是：设置页内容区在标题下面那条硬边界上一刀切断 ——
 * 一张玻璃大卡滚到那里，圆角还没进来、卡体已经没了，看着像被人拿尺子裁掉。
 *
 * 和 [horizontalEdgeFade] 同一个思路：离屏层 + `BlendMode.DstIn` 把**内容本身**擦淡，
 * 不是盖一层背景色。设置页背后是纸底渐变 + 顶部花枝，盖色会糊掉花枝，
 * 擦淡则是「卡片淡出、花枝从后面透出来」，任何背景都成立。
 *
 * 贴到最顶时不渐隐（`state.value <= 0`）—— 否则第一张卡的标题永远糊着一层。
 *
 * @param fade 渐隐带高度。太长会把首屏第一条内容吃进去。
 */
@Composable
fun Modifier.verticalTopFade(
    state: ScrollState,
    fade: Dp = 56.dp,
): Modifier {
    val canUp by remember(state) {
        derivedStateOf { state.value > 0 }
    }
    val a by animateFloatAsState(if (canUp) 1f else 0f, tween(200), label = "topFade")
    return this.verticalTopFade(a, fade)
}

/** 直接给强度（0 = 不渐隐、1 = 满）。不想绑 ScrollState 时用。 */
fun Modifier.verticalTopFade(strength: Float, fade: Dp): Modifier {
    if (strength <= 0.001f) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val h = (fade.toPx() * strength).coerceIn(0f, size.height / 2f)
            if (h <= 0f) return@drawWithContent
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    (h / size.height) to Color.Black
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
}
