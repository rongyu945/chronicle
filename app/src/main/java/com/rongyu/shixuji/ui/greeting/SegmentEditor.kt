package com.rongyu.shixuji.ui.greeting

import com.rongyu.shixuji.ui.GlassCard
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.data.GreetingEdge
import com.rongyu.shixuji.data.GreetingTimes
import com.rongyu.shixuji.theme.isDarkTheme
import com.rongyu.shixuji.ui.motion.Motion
import com.rongyu.shixuji.ui.pressScale

/*
 * 当前段编辑面板（7.5.0，计划书 §4.5）。
 *
 * 这里是整页的视觉焦点：段名 → 两个大号时间值（带「固定/可调」说明）→ 局部时间尺
 * → 时长与空档 → 预览欢迎页。
 *
 * 换段时的过渡沿用 7.2.7 定下来的「先出后进」：旧内容 130ms 淡出，新内容从 130ms 起
 * 200ms 淡入并带 ±5% 卡片高度上浮。**纸底上两块大号数字绝不能交叉淡变** —— 会糊成
 * 「12:00 至 13:00」压在「00:00 至 07:00」上。
 */

@Composable
internal fun SegmentEditor(
    times: GreetingTimes,
    selected: Int,
    onCommit: (GreetingTimes) -> Unit,
    onEditValue: (GreetingEdge) -> Unit,
    onPreview: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isDarkTheme
    val onInk = MaterialTheme.colorScheme.onSurface
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    val hair = onInk.copy(alpha = if (dark) 0.14f else 0.11f)
    val cardBg = if (dark) onInk.copy(alpha = 0.065f)
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)

    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            // 方向跟随段序号：往后切（早→午→夜）新内容从下方抬起，往前切反之。
            val forward = targetState > initialState
            (fadeIn(Motion.enterDelayed(200, 130)) +
                slideInVertically(Motion.enterDelayed(200, 130)) { h ->
                    if (forward) h / 20 else -h / 20
                })
                .togetherWith(fadeOut(Motion.exit(130)))
        },
        label = "segPanel"
    ) { i ->
        val cur = times.segments()[i]
        // 拖动中的端点值只活在卡片内部：**不上抛 times**。拖动期间页级状态一个都不变，
        // 只有这两颗 IntState 的读方（两个时间数字 + 时间尺绘制）失效。
        val startV = remember(cur.start) { mutableIntStateOf(cur.start) }
        val endV = remember(cur.end) { mutableIntStateOf(cur.end) }
        val axis = remember(cur.kind, cur.start, cur.end) { axisOf(cur) }

        // 2026-11-11：换成设置页同一套液态玻璃，内距 12/10/9、时间值 27→23sp、
        // 时间尺 76→62dp、预览按钮 38→34dp —— 整页从约 812dp 收到约 742dp，一屏装完。
        GlassCard {
        Column(
            Modifier.fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 9.dp)
        ) {
            // ── 段名 + 主题色点 + 副标 ────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(cur.color)
                )
                Text(
                    cur.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = onInk,
                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                )
                Text(cur.sub, fontSize = 9.5.sp, color = ink2)
            }

            Spacer(Modifier.height(9.dp))

            // ── 两个大号时间值 ────────────────────────────────
            // 三行网格（标签 / 数值+至 / 说明）：三个部分必须各自对齐到同一列。
            // 早期版本把「标签+数值+说明」装进一个 Column 再在 Row 里 Bottom 对齐，
            // 「至」就被挤到说明那一行去了（截图里像「可调整 至 可调整」三个标签并排）。
            EdgeValues(
                startValue = { startV.intValue },
                endValue = { endV.intValue },
                startNote = if (cur.startEditable) "可调整" else "固定起点 · 不可调整",
                endNote = if (cur.endEditable) "可调整" else "固定终点 · 不可调整",
                startEditable = cur.startEditable,
                endEditable = cur.endEditable,
                onInk = onInk, ink2 = ink2,
                onEditStart = { onEditValue(cur.startEdge) },
                onEditEnd = { onEditValue(cur.endEdge) },
            )

            Spacer(Modifier.height(4.dp))

            // ── 局部时间尺 ────────────────────────────────────
            ZoomTimeRuler(
                base = times,
                seg = cur,
                axis = axis,
                startV = startV,
                endV = endV,
                onCommit = onCommit
            )

            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "时长 ${durationText(cur.duration)}", fontSize = 10.sp, color = ink2
                )
                Text("  ·  ", fontSize = 10.sp, color = ink2.copy(alpha = 0.6f))
                Text(
                    // 7.6.1：首尾两段也可能有空档了（早安之前、晚安之后），三段统一「前 / 后」
                    gapText(times.gapBefore(cur.kind)) + " / " + gapText(times.gapAfter(cur.kind)),
                    fontSize = 10.5.sp, color = ink2
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── 预览欢迎页 ────────────────────────────────────
            val press = remember { MutableInteractionSource() }
            Row(
                Modifier.fillMaxWidth().height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (dark) onInk.copy(alpha = 0.07f) else onInk.copy(alpha = 0.035f))
                    .border(1.dp, hair, RoundedCornerShape(17.dp))
                    .pressScale(press, Motion.PRESS_PILL)
                    .clickable(
                        interactionSource = press, indication = null, role = Role.Button
                    ) { onPreview(cur.kind) }
                    .semantics { contentDescription = "预览${cur.title}欢迎页，不会更改设置" },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "预览${cur.greeting}欢迎页", fontSize = 11.5.sp, color = onInk
                )
                Text("  不会更改设置", fontSize = 9.sp, color = ink2)
            }
        }
        }
    }
}

/**
 * 开始 / 至 / 结束 的三行网格。
 *
 * 为什么要三行而不是「一个 Column 装标签+数值+说明」：
 * 后者在 Row 里只能整体 Bottom 对齐，「至」就会落到说明那一行，看上去像
 * 「可调整 至 可调整」三个并列标签（7.5.0 第一版就是这样，截图一眼就能看出别扭）。
 * 拆成三行后：标签行、数值行（用 alignByBaseline 让「至」与两个时间共用基线）、
 * 说明行各自横向对齐，字体缩放时也只会整行变高，不会互相挤错位。
 */
@Composable
private fun EdgeValues(
    startValue: () -> Int,
    endValue: () -> Int,
    startNote: String,
    endNote: String,
    startEditable: Boolean,
    endEditable: Boolean,
    onInk: androidx.compose.ui.graphics.Color,
    ink2: androidx.compose.ui.graphics.Color,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // 三行都用「两端顶格 + 中间一个 至」的排法：
        //  · 值用 fill=false 量自身宽度，虚线下划线才贴着数字；
        //  · 两个 weight 相等的 Spacer 把「至」精确推到卡片中线上；
        //  · alignByBaseline 让 12:00 / 至 / 13:00 共用一条基线。
        // 反过来（值用 weight(1f) 填满槽位）下划线会拉长成两段长横线，看着像分隔符。
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("开始", fontSize = 9.sp, color = ink2)
            Text("结束", fontSize = 9.sp, color = ink2)
        }
        Row(Modifier.fillMaxWidth()) {
            EdgeTime(
                value = startValue, editable = startEditable, onInk = onInk, ink2 = ink2,
                label = "开始", onClick = onEditStart,
                modifier = Modifier.alignByBaseline()
            )
            Spacer(Modifier.weight(1f))
            Text(
                "至", fontSize = 10.5.sp, color = ink2,
                modifier = Modifier.padding(horizontal = 10.dp).alignByBaseline()
            )
            Spacer(Modifier.weight(1f))
            EdgeTime(
                value = endValue, editable = endEditable, onInk = onInk, ink2 = ink2,
                label = "结束", onClick = onEditEnd,
                modifier = Modifier.alignByBaseline()
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                startNote, fontSize = 8.5.sp,
                color = if (startEditable) ink2 else ink2.copy(alpha = 0.8f)
            )
            Text(
                endNote, fontSize = 8.5.sp,
                color = if (endEditable) ink2 else ink2.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * 一颗大号时间值（23sp 等宽；7.5.6 从 27sp 收紧）。
 *
 * 值用 lambda 取：拖动时这次读取发生在**本组件自己的作用域**里，只有这颗数字重组，
 * 不会把整张卡连带重组一遍。传 `Int` 的话读取发生在父作用域，父级必重组。
 */
@Composable
private fun EdgeTime(
    value: () -> Int,
    editable: Boolean,
    onInk: androidx.compose.ui.graphics.Color,
    ink2: androidx.compose.ui.graphics.Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val text = fmtMin(value())
    Text(
        text, fontSize = 23.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
        color = if (editable) onInk else onInk.copy(alpha = 0.82f),
        modifier = modifier
            .then(
                if (editable) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                        .semantics { contentDescription = "$label 时间 $text，点击精确修改" }
                } else Modifier
            )
            .drawBehind {
                if (!editable) return@drawBehind
                // 可点的那一侧画一条 1dp 虚线（3/2.5），像稿纸上的可改写处
                val y = size.height - 1.dp.toPx()
                var x = 0f
                val dash = 3.dp.toPx()
                val gap = 2.5.dp.toPx()
                while (x < size.width) {
                    drawLine(
                        ink2.copy(alpha = 0.45f),
                        Offset(x, y), Offset(minOf(x + dash, size.width), y), 1.dp.toPx()
                    )
                    x += dash + gap
                }
            }
    )
}
