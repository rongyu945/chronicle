package com.rongyu.shixuji.ui.greeting

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.data.GreetingEdge
import com.rongyu.shixuji.data.GreetingTimes
import com.rongyu.shixuji.theme.isDarkTheme
import com.rongyu.shixuji.ui.drawGlassKnob
import com.rongyu.shixuji.ui.motion.Motion
import com.rongyu.shixuji.ui.motion.MotionDebug
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 局部时间尺（7.5.0 重做，替代 7.3.1 的 ZoomAxis）。
 *
 * 三件事没变（它们是 7.2.9–7.3.1 反复踩坑才定下来的地基）：
 *  ① 量程**写死**，拖动时刻度与整点数字一动不动（用户明确要「刻度定死」）；
 *  ② **整轴一个手势处理器**，按下按「离哪颗钮近」决定拖谁（每颗钮各套 Box 会让贴边的钮
 *     拖不动，也会因为 pointerInput key 变化把拖动打断）；
 *  ③ 拖动中的值只写 [startV] / [endV] 两个 IntState，**不上抛页面状态**，松手才提交。
 *
 * 这一版新增的四件事（计划书 §5.4–§5.8）：
 *  · 拖动中在端点上方浮出一个**时间浮签**（不遮刻度，贴边时自动夹在轨道内）；
 *  · 撞到合法边界时**手柄压缩 3%** + 轴下换成解释文案（「午间至少保留 5 分钟」），
 *    不再像卡死；
 *  · 触觉**节流**：抓取一次、每跨过 30 分钟长刻度一次、撞边界一次，其余不震；
 *  · 松手后手柄 180ms 回到常态（grab 进度从 1 收到 0），时间浮签 180ms 淡出。
 */

/** 放大轴的固定量程：早安 00–08、午安 11–14、晚安 20–24（超出时把当前值并进来）。 */
internal fun axisOf(seg: GreetingSegment): IntRange {
    val fixed = when (seg.kind) {
        GreetingTimes.GREETING_MORNING -> 0..480
        GreetingTimes.GREETING_NOON -> 660..840
        else -> 1200..1440
    }
    val lo = minOf(fixed.first, seg.start)
    val hi = maxOf(fixed.last, seg.end)
    return lo.coerceIn(0, GreetingTimes.DAY - 60)..hi.coerceIn(60, GreetingTimes.DAY)
}

@Composable
internal fun ZoomTimeRuler(
    base: GreetingTimes,
    seg: GreetingSegment,
    axis: IntRange,
    startV: MutableIntState,
    endV: MutableIntState,
    onCommit: (GreetingTimes) -> Unit,
    modifier: Modifier = Modifier,
) {
    val span = (axis.last - axis.first).coerceAtLeast(1).toFloat()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val dark = isDarkTheme
    val onInk = MaterialTheme.colorScheme.onSurface
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = onInk.copy(alpha = if (dark) 0.22f else 0.15f)
    val tickColor = onInk.copy(alpha = if (dark) 0.26f else 0.15f)
    val badgeBg = if (dark) Color(0xFFF0EDE6) else Color(0xFF3A362F)
    val badgeInk = if (dark) Color(0xFF23211C) else Color(0xFFF7F5EF)

    // 7.5.6：可见钮 20 → 18dp（热区仍是 44dp，手感不变；卡片矮了 14dp，钮跟着收一档）
    val knobPx = with(density) { 18.dp.toPx() }
    // 计划书 §5.3：可见钮 19–21dp，热区 44–48dp —— 视觉不放大，靠透明命中层放大热区。
    val grabPx = with(density) { 44.dp.toPx() }

    // 手势协程里要读「当前段/量程/基准」，但都不该进 pointerInput 的 key。
    val liveSeg by rememberUpdatedState(seg)
    val liveAxis by rememberUpdatedState(axis)
    val liveBase by rememberUpdatedState(base)

    // 整点数字：只跟量程有关，量程恒定 ⇒ 一辈子只量一次。
    val measurer = rememberTextMeasurer()
    val hourMarks = remember(axis, ink2) {
        val style = TextStyle(fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, color = ink2)
        buildList {
            var m = ((axis.first + 59) / 60) * 60
            while (m <= axis.last) {
                add(m to measurer.measure(AnnotatedString((m / 60).toString()), style))
                m += 60
            }
        }
    }
    val badgeStyle = remember(dark) {
        TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = badgeInk)
    }
    // 浮签文字按分钟缓存：拖动时每帧都在 draw 里取，逐帧 measure 等于每帧排一次版。
    val badgeCache = remember(dark) { mutableMapOf<Int, androidx.compose.ui.text.TextLayoutResult>() }

    // 0 = 没在拖，1 = 拖起点，2 = 拖终点
    var held by remember(seg.kind) { mutableIntStateOf(0) }
    // 撞到合法边界时的解释文案（拖动结束或离开边界就清空）
    var limitText by remember(seg.kind) { mutableStateOf<String?>(null) }

    val grabS by animateFloatAsState(if (held == 1) 1f else 0f, Motion.TouchSpring, label = "grabS")
    val grabE by animateFloatAsState(if (held == 2) 1f else 0f, Motion.TouchSpring, label = "grabE")
    // 撞边界的手柄压缩：一次性 1 → 0 的脉冲（3%），不做循环抖动。
    val limitPulse by animateFloatAsState(if (limitText != null) 1f else 0f, Motion.TouchSpring, label = "limitP")

    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(62.dp)) {
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            // 轴两端内缩 10dp：端点的整点数字与圆钮都能完整落在卡片里。
            val padPx = with(density) { 10.dp.toPx() }
            val axisPx = (widthPx - padPx * 2f).coerceAtLeast(1f)
            val cy = with(density) { 27.dp.toPx() }

            Canvas(
                Modifier.fillMaxSize()
                    .semantics {
                        contentDescription =
                            "${seg.title}时间尺。起点 ${fmtMin(startV.intValue)}，" +
                                "终点 ${fmtMin(endV.intValue)}。横向拖动圆钮可调整。"
                    }
                    .pointerInput(seg.kind, axis) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val cur = liveSeg
                            val ax = liveAxis
                            val s0 = (ax.last - ax.first).coerceAtLeast(1)
                            fun xAt(v: Int) =
                                padPx + (((v - ax.first) / s0.toFloat()) * axisPx).coerceIn(0f, axisPx)

                            val dS = abs(down.position.x - xAt(startV.intValue))
                            val dE = abs(down.position.x - xAt(endV.intValue))
                            val movS = cur.startEditable
                            val movE = cur.endEditable
                            // 离可拖的钮都远：这不是在拖端点，交回给页面（竖滑滚动）
                            if (minOf(
                                    if (movS) dS else Float.MAX_VALUE,
                                    if (movE) dE else Float.MAX_VALUE
                                ) > grabPx
                            ) return@awaitEachGesture
                            val isStart = movS && (!movE || dS <= dE)
                            val edge = if (isStart) cur.startEdge else cur.endEdge

                            // 等横向 touch slop：纵向为主的划动在这里放弃，让父级滚动接手
                            awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            } ?: return@awaitEachGesture

                            // 整个手势期间冻结：起点值 + 量程 + 合法区间
                            val from = if (isStart) startV.intValue else endV.intValue
                            val range = liveBase.rangeOf(edge)
                            val lo = maxOf(range.first, ax.first)
                            val hi = minOf(range.last, ax.last)
                            var acc = 0f
                            var last = from
                            var lastTick = from / 30
                            var lastHapticAt = 0L
                            held = if (isStart) 1 else 2
                            MotionDebug.log("greet", "抓${if (isStart) "起点" else "终点"}")
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                            horizontalDrag(down.id) { change ->
                                // ⚠️ 必须先取位移再 consume：positionChange() 对已消费的 change 恒返 0。
                                acc += change.positionChangeIgnoreConsumed().x
                                change.consume()
                                val vf = from + acc / axisPx * s0
                                val raw = GreetingTimes.snapToStep(vf.roundToInt())
                                val v = raw.coerceIn(lo, hi)
                                val now = System.currentTimeMillis()

                                if (v != last) {
                                    if (isStart) startV.intValue = v else endV.intValue = v
                                    last = v
                                    // 撞到边界：换成解释文案 + 一次轻反馈（80ms 节流）
                                    if (raw != v) {
                                        limitText = limitNote(edge, v == lo)
                                        if (now - lastHapticAt > 80) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            lastHapticAt = now
                                        }
                                    } else if (limitText != null) {
                                        limitText = null
                                    }
                                    // 跨过 30 分钟长刻度：一次更轻的反馈（同一节流窗口）
                                    val tick = v / 30
                                    if (tick != lastTick) {
                                        lastTick = tick
                                        if (limitText == null && now - lastHapticAt > 80) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            lastHapticAt = now
                                        }
                                    }
                                } else if (raw != v && limitText == null) {
                                    // 已经顶在边界上还继续拖：文案要出现，但不重复震
                                    limitText = limitNote(edge, v == lo)
                                }
                            }
                            held = 0
                            limitText = null
                            // 已经动过就提交（拖到一半被页面抢走手势也算数，
                            // 不能让画面上的值与库里存的值对不上）。
                            if (last != from) onCommit(liveBase.updateEdge(edge, last))
                        }
                    }
            ) {
                val w = size.width
                fun xOf(v: Int) = padPx + ((v - axis.first) / span).coerceIn(0f, 1f) * (w - padPx * 2f)

                drawLine(lineColor, Offset(0f, cy), Offset(w, cy), 1.dp.toPx())

                // 固定刻度：半点短、整点长，整点再标数字。位置只由量程决定 ⇒ 拖动时不动。
                var m = ((axis.first + 29) / 30) * 30
                while (m <= axis.last) {
                    val px = xOf(m)
                    val long = m % 60 == 0
                    drawLine(
                        tickColor,
                        Offset(px, cy + 5.dp.toPx()),
                        Offset(px, cy + (if (long) 9.dp.toPx() else 7.dp.toPx())),
                        1.dp.toPx()
                    )
                    m += 30
                }
                hourMarks.forEach { (min, tl) ->
                    drawText(tl, topLeft = Offset(xOf(min) - tl.size.width / 2f, cy + 11.dp.toPx()))
                }

                // 彩色段：跟着拖动实时伸缩（读 IntState，只在绘制阶段失效）
                val x0 = xOf(startV.intValue)
                val x1 = xOf(endV.intValue)
                drawRoundRect(
                    color = seg.color.copy(alpha = 0.88f),
                    topLeft = Offset(x0, cy - 3.5.dp.toPx()),
                    size = Size(maxOf(0f, x1 - x0), 7.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )

                // 两颗钮画在同一张画布上：位置与手势判定口径完全一致（都 coerce 到轴内）。
                listOf(true, false).forEach { isStart ->
                    val cx = if (isStart) xOf(startV.intValue) else xOf(endV.intValue)
                    val movable = if (isStart) seg.startEditable else seg.endEditable
                    if (movable) {
                        val grab = if (isStart) grabS else grabE
                        // 抓住：放大 10% + 上浮 2.5dp；撞边界再压 3%（只压不弹）
                        val r = knobPx / 2f * (1f + 0.10f * grab) * (1f - 0.03f * limitPulse)
                        // 8.0.0：时间尺的签名 = 「抓住光晕」——捏住的那颗钮除了放大 10%，
                        // 身下还会亮起一圈同时段色的柔光，放手散掉。别的滑块没有这个。
                        if (grab > 0.01f) {
                            // 径向渐变而不是两个实心圆 —— 实心圆叠出来是一圈硬边「环」，像选框。
                            val glowC = Offset(cx, cy - 2.5.dp.toPx() * grab)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        seg.color.copy(alpha = 0.30f * grab),
                                        seg.color.copy(alpha = 0.12f * grab),
                                        Color.Transparent
                                    ),
                                    center = glowC, radius = r * 2.1f
                                ),
                                radius = r * 2.1f, center = glowC
                            )
                        }
                        drawGlassKnob(
                            center = Offset(cx, cy - 2.5.dp.toPx() * grab),
                            radius = r,
                            dark = dark,
                            rimColor = seg.color,
                            rimWidth = 2.dp.toPx(),
                            shadowScale = 1f + grab * 0.7f
                        )
                    } else {
                        // 钉死的边界：小实心点 + 外环弱描边，明确「这是刻度不是钮」
                        drawCircle(seg.color.copy(alpha = 0.90f), radius = 3.dp.toPx(), center = Offset(cx, cy))
                        drawCircle(
                            seg.color.copy(alpha = 0.30f), radius = 6.dp.toPx(), center = Offset(cx, cy),
                            style = Stroke(1.dp.toPx())
                        )
                    }
                }

                // 时间浮签：只在拖动中出现，浮在端点上方；贴边时夹进轨道内不越界。
                val which = held
                if (which != 0) {
                    val v = if (which == 1) startV.intValue else endV.intValue
                    val cx = xOf(v)
                    val tl = badgeCache.getOrPut(v) {
                        measurer.measure(AnnotatedString(fmtMin(v)), badgeStyle)
                    }
                    val bw = tl.size.width + 14.dp.toPx()
                    val bh = 20.dp.toPx()
                    val bx = (cx - bw / 2f).coerceIn(0f, (w - bw).coerceAtLeast(0f))
                    val by = cy - 26.dp.toPx()
                    drawRoundRect(
                        color = badgeBg, topLeft = Offset(bx, by),
                        size = Size(bw, bh), cornerRadius = CornerRadius(bh / 2f)
                    )
                    drawText(
                        tl, topLeft = Offset(bx + (bw - tl.size.width) / 2f, by + (bh - tl.size.height) / 2f)
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                limitText ?: "拖端点快速改 · 点数字精确改",
                fontSize = 10.sp,
                color = if (limitText != null) seg.color else ink2
            )
        }
    }
}
