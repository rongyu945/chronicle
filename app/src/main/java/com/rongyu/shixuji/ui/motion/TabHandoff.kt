package com.rongyu.shixuji.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rongyu.shixuji.ShixuTab

/**
 * 三页切换：**串行纸面递换**（7.7.0 重构，取代 `AnimatedContent` + `togetherWith`）。
 *
 * 为什么不再用 `AnimatedContent`：它的入场与离场是同一个 Transition 的两侧，
 * 从第 0ms 起两侧**同时**参与绘制 —— 于是必然出现「旧页还没走完、新页已经从侧面
 * 冲进来」的叠页；叠页在本 App 上还不只是难看：页面本身是透明的，玻璃卡每帧
 * `recordBackdrop` 采样，两层同时在场时采到的是「两张纸」，玻璃质感会跟着崩。
 * 把 `fadeIn` 往后挪、或者把时长调慢，都只是缩短叠印窗口，不改变「两页同时在
 * 组合树里」这个事实。所以这里换成结构上的串行：
 *
 * 旧页 170ms 完整退场（alpha→0）→ 换 [displayed] → 等一个 Compose frame → 新页 240ms 入场。
 *
 * 状态分工（不能合并成一个 `selected`）：
 *   · 调用方的 selected = 用户意图（底栏立刻跟着它动，点击要马上有反馈）；
 *   · displayed        = 画面里真正被组合的那一页；
 *   · seg/phase        = 此刻那一段动效（含起点快照，见 [TabSegment]），进度只有一条 0↔1。
 * 位移、缩放、alpha 全部由同一个 [progress] 派生，不建第二套计时器。
 *
 * latest-wins：动画协程只有一条（`LaunchedEffect` 以 selected 为 key）。快速连点时旧协程
 * 被取消、[Animatable] 保留当前值，新协程从当前进度继续 —— 所以会「半路折返」，
 * 不会把中间点过的页面挨个播一遍。**不要**在点击回调里 `launch` 独立动画协程。
 *
 * 7.7.1：位移/缩放不再是「固定锚点映射」，而是「从当前画面值出发」的段（[TabSegment]）。
 * 原写法在被打断时会跳 8–17dp（外部复核 §8.3），现在任何时刻切换都连续。
 */
@Composable
fun SequentialTabContent(
    selected: ShixuTab,
    orderedTabs: List<ShixuTab>,
    stateHolder: SaveableStateHolder,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (ShixuTab) -> Unit
) {
    // 首帧直接显示真实目标：冷启动 / 进程恢复不许播一次「入场」。
    var displayed by remember { mutableStateOf(selected) }
    var phase by remember { mutableStateOf(TabPhase.Idle) }
    var dir by remember { mutableIntStateOf(1) }
    // 1 = 完整可见，0 = 完全不可见。退场 1→0，入场 0→1。
    val progress = remember { Animatable(1f) }

    val density = LocalDensity.current
    val exitPx = with(density) { Motion.TAB_EXIT_DP.dp.toPx() }
    val enterPx = with(density) { Motion.TAB_ENTER_DP.dp.toPx() }

    // 画面此刻真正在用的那一段。位移/缩放都从它的当前值出发 → 被打断也不跳（见 TabSegment 注释）。
    // 只在 graphicsLayer 的 lambda 里读，所以改动只重画图层、不触发重组。
    var seg by remember { mutableStateOf(TabSegment.idle(1, exitPx, enterPx)) }

    /** 以「此刻屏幕上的值」为起点开一段新动效。 */
    fun begin(next: TabPhase): TabSegment {
        val p = progress.value.coerceIn(0f, 1f)
        return TabSegment(
            phase = next,
            fromProgress = p,
            fromTx = seg.txAt(p),
            fromScale = seg.scaleAt(p),
            dir = dir,
            exitPx = exitPx,
            enterPx = enterPx,
        )
    }

    LaunchedEffect(selected, reducedMotion) {
        if (reducedMotion) {
            // 减弱动效：直接落位，不留半透明/半位移的中间态。
            displayed = selected
            progress.snapTo(1f)
            seg = TabSegment.idle(dir, exitPx, enterPx)
            phase = TabPhase.Idle
            return@LaunchedEffect
        }

        if (selected == displayed) {
            // 退场途中又点回当前页：从**此刻的位移/缩放**收回可见。
            // 用干净入场的初值会把画面往回拽一截（7.7.0 在 p=0.2 时跳 16dp）。
            if (progress.value < 1f) {
                seg = begin(TabPhase.Entering)
                phase = TabPhase.Entering
                MotionDebug.log("tab_back", displayed.label)
                progress.animateTo(1f, Motion.tabEnter())
            }
            seg = TabSegment.idle(dir, exitPx, enterPx)
            phase = TabPhase.Idle
            return@LaunchedEffect
        }

        // 方向按用户可自定义的底栏顺序算，不写死枚举下标。
        val from = orderedTabs.indexOf(displayed)
        val to = orderedTabs.indexOf(selected)
        dir = if (from < 0 || to < 0 || to >= from) 1 else -1

        // ① 旧页让位（此时 alpha 仍为 1，只有位移与极轻的收束）。
        //    若上一段还没走完（连点），起点继承它此刻的位置 → 不跳。
        seg = begin(TabPhase.Exiting)
        phase = TabPhase.Exiting
        // 记 p 和 tx：p<1 表示这次是**打断**（上一段没走完），tx 是新段的起点位移 ——
        // 复核报告 §8.3 的验据就落在这两个数上（旧写法在同一 p 下 tx 会突变成理想锚点值）。
        MotionDebug.log(
            "tab_exit",
            "${displayed.label} → ${selected.label} dir=$dir p=${"%.3f".format(progress.value)} tx=${"%.1f".format(seg.fromTx)}"
        )
        if (progress.value > 0.001f) progress.animateTo(0f, Motion.tabExit())
        MotionDebug.log("tab_exit_end", displayed.label)

        // ② 隐藏交接：旧页已经 alpha=0，换内容，且让这一帧真的画出去
        displayed = selected
        MotionDebug.log("tab_swap", displayed.label)
        withFrameNanos { }

        // ③ 新页独立入场（干净起步，与方案初值一致）
        seg = TabSegment.enter(dir, exitPx, enterPx)
        phase = TabPhase.Entering
        progress.animateTo(1f, Motion.tabEnter())
        seg = TabSegment.idle(dir, exitPx, enterPx)
        phase = TabPhase.Idle
        MotionDebug.log("tab_enter_end", displayed.label)
    }

    val busy = phase != TabPhase.Idle

    Box(
        modifier = modifier
            .fillMaxSize()
            // 转场期间位移/缩放不能画到页面外（否则会压到状态栏下沿那条边）
            .clipToBounds()
            // 转场期间屏蔽内容区触控：alpha=0 不等于不可点，退场中的旧页仍然参与 hit test。
            // 用 Initial pass，赶在页面内部 clickable / draggable 之前把事件吃掉；
            // 底栏在调用方那一层、不在这个容器里，所以点击仍能继续改最新目标。
            .pointerInput(busy) {
                if (busy) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            e.changes.forEach { it.consume() }
                        }
                    }
                }
            }
    ) {
        stateHolder.SaveableStateProvider(displayed.name) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 全部在这里读：graphicsLayer 的 lambda 是绘制阶段执行的，
                        // 进度变化只重画图层，不触发重组。
                        val p = progress.value.coerceIn(0f, 1f)
                        val idle = seg.phase == TabPhase.Idle
                        alpha = if (idle) 1f else p
                        translationX = seg.txAt(p)
                        val s = seg.scaleAt(p)
                        scaleX = s
                        scaleY = s
                    }
                    .semantics { if (busy) invisibleToUser() }
            ) {
                content(displayed)
            }
        }
    }
}
