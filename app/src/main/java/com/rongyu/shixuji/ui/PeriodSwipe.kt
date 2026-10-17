package com.rongyu.shixuji.ui

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// 程序化翻页的三种走法
private const val MODE_NONE = 0

/** 走一页：相邻那一页就是目标内容，动画结束时才提交 —— 和手指滑一页是同一条路径。 */
private const val MODE_STEP = 1

/** 内容**已经**换成目标了：把上一页摆回屏幕中央，再滑回原位一页。 */
private const val MODE_ENTER = 2

/** 跳得很远：没有中间页可画，滑一小段 + 淡出 → 悄悄换内容 → 从另一侧滑回来。 */
private const val MODE_JUMP = 3

/**
 * 程序化翻页控制器 —— 让「点按钮换一天」和「手指滑一天」是**同一套动画**。
 *
 * 用户 2026-10-16：「点击切换按钮 / 「今」的时候是直接硬切过来的，没有移动的动画，不舒服」。
 * 硬切的根源：切天原来只是一次 `shownDayMs = 新值` 的状态赋值 —— 内容换了，谁也没让它动。
 *
 * 2026-10-16 第二版（用户：「跳转动画做的一点都不好，自己看看自己的逻辑」）：
 * 第一版只有 [enterFrom]，它把容器先挪到 `+dir*一页` 再动画到 `-dir*一页` ——
 * **一共走了两页路，中途会经过三个不同的日期**（前一页、目标、目标的后一页），
 * 目标那天只在中间一闪而过，看起来就是"嗖地穿过去"。现在拆成三种走法：
 *
 * - [step]  差正好一页（一天 / 一周）：和手势同一条路径。相邻页画的就是目标内容，
 *           所以是把那一页**滑到位**、动画结束时才提交，全程连续；
 * - [enter] 内容已经换好了（比如跨周时周条要补一页）：把上一页摆回屏幕中央再滑回原位，
 *           只走一页路；
 * - [jump]  差得更远：没有中间页可画，滑一页就是撒谎（会先出现一个"从没去过的那天"），
 *           所以滑一小段 + 淡出 → 内容悄悄换掉 → 从另一侧滑回来。
 *
 * @param dir +1 = 往后（内容从右边进），-1 = 往前
 */
@Stable
class PeriodSwipeController {
    internal var seq by mutableIntStateOf(0)
    internal var dir by mutableIntStateOf(0)
    internal var mode by mutableIntStateOf(MODE_NONE)
    internal var swap: (() -> Unit)? = null

    /**
     * 已经消费到第几个请求。**故意用普通字段、不用状态**：
     * 在 LaunchedEffect 里写状态会让 key 变 → Compose 立刻把正在跑的那个协程取消掉，
     * 动画还没开始就被掐死（症状：点按钮/点周条毫无反应，但手势翻页仍然正常）。
     */
    internal var consumed = 0


    /** 走一页（目标就在隔壁那一页）。提交由容器的 onCommit 完成。 */
    fun step(direction: Int) {
        dir = direction; mode = MODE_STEP; swap = null; seq++
    }

    /** 内容已经换成目标了，从 [direction] 方向滑一页进来。 */
    fun enter(direction: Int) {
        dir = direction; mode = MODE_ENTER; swap = null; seq++
    }

    /**
     * 跳很远（差不止一页）。[swap] 会在画面淡到看不见的那一刻被调用 —— 换日期写在这里面，
     * 用户只会看到「旧的淡出、新的淡入」，不会看到中间任何一天。
     */
    fun jump(direction: Int, swap: () -> Unit) {
        dir = direction; mode = MODE_JUMP; this.swap = swap; seq++
    }
}

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
 * 4. 程序化翻页（按钮 / 跨周 / 今）走 [PeriodSwipeController]，
 *    **和平滑手势复用同一条弹簧**，所以两种翻页的手感一致。
 *
 * [onProgress] 会把「已滑了几格」按 -1..+1 的小数持续上报（手指左滑为负）。
 * 日视图的周条要靠它让日期上那枚红圆跟着手指走；传 null 就完全不产生额外开销。
 */
@Composable
fun PeriodSwipe(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    controller: PeriodSwipeController? = null,
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
        // 跳转模式要整层淡入淡出（画的时候读，不触发重组）
        var layerAlpha by remember { mutableFloatStateOf(1f) }
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

        // 程序化翻页（按钮/跨周/今）：和手势同一套平移，只是由代码发起。
        // 手势动画进行中（busy）先等它跑完，绝不丢请求 —— 「点快了没反应」比慢一点更难受。
        // 请求一到就立刻把 seq 消费掉：不然离开又回到这一层视图时，LaunchedEffect 会重放同一次请求。
        if (controller != null) {
            LaunchedEffect(controller.seq) {
                // 已经消费过就跳过：离开又回到这一层视图时 effect 会重建、重放同一个 key
                if (controller.seq == controller.consumed) return@LaunchedEffect
                controller.consumed = controller.seq
                val reqDir = controller.dir
                val reqMode = controller.mode
                val reqSwap = controller.swap
                controller.swap = null
                if (reqDir == 0 || reqMode == MODE_NONE || widthPx <= 0f) return@LaunchedEffect
                while (busy) delay(16)
                busy = true
                // 这条弹簧只给**手势松手**用（甩动速度要接进动画，所以必须带初速度）。
                // 程序化翻页不能用它 —— 理由见下面的 pageTurn。
                val glide = spring<Float>(
                    dampingRatio = 1f,
                    stiffness = Spring.StiffnessMedium,
                    visibilityThreshold = 0.5f
                )
                try {
                // 程序化翻页**不用弹簧**：弹簧在 t=0 就把速度顶到最大值（位移从 0 起跳到 1080px，
                // 初速度 = 1080·ω），视觉上是"啪地一弹 + 后面爬一段小尾巴"，正是用户说的不自然。
                // 用带缓入缓出的 tween：从静止加速、中段最快、末端平滑落位，像翻页。
                val pageTurn = tween<Float>(330, easing = FastOutSlowInEasing)
                when (reqMode) {
                    MODE_STEP -> {
                        // 相邻那一页画的就是目标内容：滑到位，然后在**动画结束那一帧**提交。
                        // 提交后立刻把平移量归零 —— 同一帧里"中心页"已经换成刚滑进来的那一页，
                        // 所以视觉上完全连续，一帧都不会闪。
                        animate(offset, -reqDir * widthPx, animationSpec = pageTurn) { v, _ ->
                            offset = v; report(v)
                        }
                        onCommit(reqDir)
                        offset = 0f
                        report(0f)
                    }
                    MODE_ENTER -> {
                        // 内容已经是目标了：把**上一页**摆到屏幕中央（正是用户刚看的那一页），
                        // 再滑回原位 —— 只走一页路，不是从另一头穿过来。
                        // 时长必须和 MODE_STEP 一样：跨周时周条走 ENTER、日列表走 STEP，
                        // 两者不同步就会一个先到一个后到，看着像两截。
                        offset = reqDir * widthPx
                        report(offset)
                        animate(offset, 0f, animationSpec = pageTurn) { v, _ ->
                            offset = v; report(v)
                        }
                    }
                    MODE_JUMP -> {
                        // 滑一小段 + 淡出（此刻画面里还是旧内容）→ 悄悄换 → 从另一侧滑回来。
                        // 位移只用 28dp：它表达的是"方向"，不是在假装滑了一页。
                        val shift = with(density) { 28.dp.toPx() }
                        animate(0f, 1f, animationSpec = tween(150, easing = FastOutLinearInEasing)) { t, _ ->
                            offset = -reqDir * shift * t
                            layerAlpha = 1f - t
                        }
                        offset = 0f
                        layerAlpha = 0f
                        reqSwap?.invoke()
                        withFrameNanos { }   // 让换好的内容先落一帧（此刻 alpha = 0，看不见）
                        animate(0f, 1f, animationSpec = tween(220, easing = LinearOutSlowInEasing)) { t, _ ->
                            offset = reqDir * shift * (1f - t)
                            layerAlpha = t
                        }
                        offset = 0f
                        layerAlpha = 1f
                        report(0f)
                    }
                }
                } finally {
                    // 被新请求取消时也要松开闸门，否则后面每次翻页都会卡在 busy 里空转
                    busy = false
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = layerAlpha }
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
