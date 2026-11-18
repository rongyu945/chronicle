package com.rongyu.shixuji.ui

import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rongyu.shixuji.ui.motion.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.rongyu.shixuji.ui.motion.Motion

/**
 * 页面大标题的折叠量（0 = 展开的 28sp，1 = 收起的小字号）。
 *
 * 这里记录一下为什么是「滚动位置的函数 + 吸列表」这一套，而不是更直觉的
 * 「越过阈值 → 播一段 200ms 动画」：
 *
 * 1. **跟手**。旧写法有两个状态（collapsed 布尔 + collapseT 动画）：阈值以下滚动时
 *    标题纹丝不动，越过那一帧才开始追 200ms——手指一直在走、字号却慢半拍，手感是断的。
 *    现在 fold 直接由 firstVisibleItemScrollOffset 线性算出，滚多少缩多少，零滞后。
 *
 * 2. **不留跳变**。旧写法里字号是连续动画、paddding 却按 `collapseT > 0.5f` 布尔切档，
 *    折叠过半那一帧 header 高度瞬少 4dp，列表会往上弹一下。现在所有受折叠影响的量
 *    （字号、上下 padding）都从同一个 fold 连续取值，没有第二个状态可以对不上。
 *
 * 3. **吸附要吸在列表上**。松手停稳后若卡在半路，就把**列表自己**滚到最近的端点。
 *    早先试过只把 fold 动画到 0/1：标题看起来收起了，列表其实还停在半路，用户再碰一下
 *    屏幕 fold 立刻被真实滚动位置拽回去，标题「啪」地弹回大字。吸列表才是自洽的。
 *
 * 4. **吸附动画自己也会让 isScrollInProgress 变 true**。不加 snapping 标记的话，
 *    snapshotFlow 会在启动吸附的下一帧把任务自己取消掉——症状是「完全不吸」。
 */

/** 从完整字号收到小字号所对应的滚动距离。 */
val TITLE_FOLD_DISTANCE: Dp = 56.dp
/** 展开态 / 收起态的字号（sp）。 */
const val TITLE_SIZE_EXPANDED = 28f
const val TITLE_SIZE_COLLAPSED = 17f

/** 折叠量与字号插值。 */
fun titleSizeFor(fold: Float) = TITLE_SIZE_EXPANDED - (TITLE_SIZE_EXPANDED - TITLE_SIZE_COLLAPSED) * fold

/**
 * 折叠量与**字距**插值。
 *
 * 主题里 headlineMedium 的 letterSpacing 是 -0.5sp，那是给 28sp 定的（大字号收紧是对的）。
 * 但折叠到底只剩 17sp，宋体在这个尺寸本来就瘦，再吃 -0.5sp 会显得挤、笔画发黏。
 * Apple 那条排版规则是「tracking 必须随字号变」：大字收紧、小字微微放开。所以这里
 * 从小字往回放到 +0.1sp，和字号共用同一个 fold，不出现第二个状态。
 */
fun titleTrackingFor(fold: Float) = -0.5f + 0.6f * fold

// 7.4.0（方案 §22「标题吸附」）：0.86/300 是"弹一下再回"，实际 settle 偏长且带可见过冲；
// 换 SettleSoft（0.96/420）—— 280–420ms 内稳稳停住，不来回幌。
private val SNAP_SPEC = Motion.SettleSoft

/** LazyColumn 版。 */
@Composable
fun rememberTitleFold(
    listState: LazyListState,
    distance: Dp = TITLE_FOLD_DISTANCE
): Float {
    val px = with(LocalDensity.current) { distance.toPx() }
    val reduced = rememberReducedMotion()
    val fold by remember(listState, px) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / px).coerceIn(0f, 1f)
        }
    }
    var snapJob by remember { mutableStateOf<Job?>(null) }
    var snapping by remember { mutableStateOf(false) }
    LaunchedEffect(listState, px) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                if (!snapping) { snapJob?.cancel(); snapJob = null }
                return@collect
            }
            if (snapping) return@collect
            val v = fold
            if (v <= 0.02f || v >= 0.98f) return@collect
            val collapse = v >= 0.5f
            // 滚不动就别吸：animateScrollBy 补不上差值的话会每帧重来一遍
            if (collapse && !listState.canScrollForward) return@collect
            if (!collapse && !listState.canScrollBackward) return@collect
            val cur = if (listState.firstVisibleItemIndex > 0) px
                      else listState.firstVisibleItemScrollOffset.toFloat()
            snapJob?.cancel()
            snapping = true
            snapJob = launch {
                try {
                    // 系统开了「减弱动态效果」就一步到位：吸附本身只是补最后半格，不需要惯性
                    if (reduced) {
                        if (collapse) listState.scrollToItem(0, px.toInt()) else listState.scrollToItem(0, 0)
                    } else {
                        listState.animateScrollBy((if (collapse) px else 0f) - cur, SNAP_SPEC)
                    }
                } finally { snapping = false }
            }
        }
    }
    return fold
}

/** ScrollState 版（设置页那种普通 Column + verticalScroll）。 */
@Composable
fun rememberTitleFold(
    scrollState: ScrollState,
    distance: Dp = TITLE_FOLD_DISTANCE
): Float {
    val px = with(LocalDensity.current) { distance.toPx() }
    val reduced = rememberReducedMotion()
    val fold by remember(scrollState, px) {
        derivedStateOf { (scrollState.value / px).coerceIn(0f, 1f) }
    }
    var snapJob by remember { mutableStateOf<Job?>(null) }
    var snapping by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState, px) {
        snapshotFlow { scrollState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                if (!snapping) { snapJob?.cancel(); snapJob = null }
                return@collect
            }
            if (snapping) return@collect
            val v = fold
            if (v <= 0.02f || v >= 0.98f) return@collect
            val collapse = v >= 0.5f
            if (collapse && !scrollState.canScrollForward) return@collect
            if (!collapse && scrollState.value <= 0) return@collect
            snapJob?.cancel()
            snapping = true
            snapJob = launch {
                try {
                    val target = (if (collapse) px else 0f)
                    if (reduced) scrollState.scrollTo(target.toInt())
                    else scrollState.animateScrollBy(target - scrollState.value, SNAP_SPEC)
                } finally { snapping = false }
            }
        }
    }
    return fold
}
