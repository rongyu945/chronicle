package com.rongyu.shixuji.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 跟手的左右翻页容器（周/月/年/日切换用）。
 *
 * 行为：
 * 1. 内容 1:1 跟着手指横向平移——同时把左右相邻的两个周期也并排画在两侧，
 *    所以拖动时能看到隔壁那一周/月/年的内容跟着滑进来，而不是露出一片空白背景。
 * 2. 松手后按「已滑位移 + 甩动速度」决定翻页还是弹回：
 *    - 没到阈值 → 回弹到原位（弹簧）
 *    - 到阈值 → 先把当前页整页滑出屏幕，再把页码交给上层，
 *      平移量在同一帧归零：因为新的一页早就画在旁边了，视觉上完全连续，不会闪。
 * 3. 手势冲突：`draggable` 挂在容器上、内容是子节点，Compose 的规则是子节点先拿到事件，
 *    所以卡片自己的左右滑动（露出操作按钮、拖动排序）依然优先，剩下的才轮到翻页。
 *
 * [onProgress] 会把「已滑了几格」按 -1..+1 的小数持续上报（手指左滑为负）。
 * 日视图的周条要靠它让日期上那枚红圆跟着手指走；传 null 就完全不产生额外开销。
 */
@Composable
fun PeriodSwipe(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onProgress: ((Float) -> Unit)? = null,
    onCommit: (Int) -> Unit,
    page: @Composable (offset: Int) -> Unit
) {
    // 注意用 modifier 而不是 fillMaxSize()：调用处给 weight(1f) 才能只吃「剩下」的高度。
    // 写 fillMaxSize() 会取父容器的整体高度，列表会比屏幕高出一截、底部永远露不全。
    BoxWithConstraints(
        modifier.fillMaxWidth().clipToBounds()
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        // 平移量用普通状态 + animate()，不用 Animatable：
        // 这样「改页码」和「归零」能在同一个协程里同步完成，也能在同一处把进度报出去
        var offset by remember { mutableFloatStateOf(0f) }
        var busy by remember { mutableStateOf(false) }
        val widthDp = with(density) { widthPx.toDp() }

        fun report(v: Float) {
            onProgress?.invoke(if (widthPx > 0f) v / widthPx else 0f)
        }

        val dragState = rememberDraggableState { d ->
            if (!busy) {
                // 只画了左右各一页，拖过一页就不再跟着走，避免露出页外的空白
                offset = (offset + d).coerceIn(-widthPx, widthPx)
                report(offset)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStopped = { velocity ->
                        val settled = offset
                        val forward = settled < 0
                        val pass = abs(settled) > widthPx * 0.25f || abs(velocity) > 800f
                        busy = true
                        scope.launch {
                            // 用「带初速度的临界阻尼弹簧」而不是固定时长的 tween：
                            // tween 不看手速，轻甩和重拖都是同一个节奏，所以手感发飘/生硬；
                            // 弹簧把手速当初始速度，甩得快就顺势滑出、慢慢拖就自然落位。
                            // dampingRatio = 1f（临界阻尼）= 不过冲，避免滑过界露出页面外的背景。
                            if (pass) {
                                animate(
                                    initialValue = settled,
                                    targetValue = if (forward) -widthPx else widthPx,
                                    initialVelocity = velocity,
                                    animationSpec = spring(
                                        dampingRatio = 1f,
                                        stiffness = Spring.StiffnessMedium,
                                        visibilityThreshold = 0.5f
                                    )
                                ) { v, _ -> offset = v; report(v) }
                                onCommit(if (forward) 1 else -1)
                                offset = 0f      // 同帧归位：中心页内容已换成刚才那一页
                                report(0f)       // 同步把进度归零，跟着手指移动的那个指示器才不会跳
                            } else {
                                animate(
                                    initialValue = settled,
                                    targetValue = 0f,
                                    initialVelocity = velocity,
                                    animationSpec = spring(
                                        dampingRatio = 1f,
                                        stiffness = Spring.StiffnessMediumLow,
                                        visibilityThreshold = 0.5f
                                    )
                                ) { v, _ -> offset = v; report(v) }
                            }
                            busy = false
                        }
                    }
                )
        ) {
            for (off in -1..1) {
                Box(
                    Modifier
                        .offset {
                            // 在布局阶段读状态：动画每帧只重排版、不触发重组
                            IntOffset((off * widthPx + offset).roundToInt(), 0)
                        }
                        .width(widthDp)
                        .fillMaxHeight()
                ) { page(off) }
            }
        }
    }
}
