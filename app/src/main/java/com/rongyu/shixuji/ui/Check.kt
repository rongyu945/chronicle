package com.rongyu.shixuji.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 圆环勾选框（5.8.0 起全 App 统一）。
 *
 * 为什么不用 M3 的 [androidx.compose.material3.Checkbox]：它是圆角方块，是页面上
 * 最后一个方角形状，和已经胶囊化的标签、分段控件不同族。这里换成圆环——
 * 未完成 = 一圈细环（颜色跟优先级走），完成 = 整颗实心 + 白勾。
 * 标题的「划掉 + 转灰」由调用方处理（列表里本来就有）。
 *
 * @param tint 环色，通常传 `priorityColor(priority, dark)`；循环待办/整理页传主色。
 * @param touch 触摸区边长（默认 30dp，与旧 Checkbox 交互区一致，不改变条目行高）。
 */
@Composable
fun ShixuCheck(
    checked: Boolean,
    onToggle: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    touch: Dp = 30.dp,
    /** 触摸区高度（默认与宽度一致）。列表行里可以给得比宽度高 —— 行本身比框高，白给的手感 */
    touchHeight: Dp = touch,
    box: Dp = 20.dp,
    /** 读屏软件在点击时念的动词（如「标记完成」）。只加标签，不夺走 toggleable 的动作。 */
    contentDescription: String? = null
) {
    // 无优先级条目的 priorityColor 是 Transparent（列表里那条 3dp 色条本来就不画），
    // 但勾选框不能跟着消失 —— 退回中性苍色，保证「这条还能勾」这件事看得见。
    val ring = if (tint.alpha == 0f) {
        if (dark) Color(0xFF8E9299) else Color(0xFF75878A)
    } else tint
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .width(touch)
            .height(touchHeight)
            // 用 toggleable 而不是 clickable：读屏软件能念出「已勾选 / 未勾选」，
            // 整行点击也走同一个语义角色（原来只有 clickable(role=Checkbox)，
            // 开关状态对 TalkBack 是透明的一一用户只能靠听标题猜）。
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onValueChange = {
                    // 和切 Tab、切日期的触感同一档（TextHandleMove），不做成重震动
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                }
            )
            // action 传 null：只挂「点击标签」，真正的动作仍归 toggleable，读屏念
            // 「标记完成，未勾选，复选框」这样的组合，而不是把整行文字盖掉。
            .then(
                if (contentDescription == null) Modifier
                else Modifier.semantics { onClick(label = contentDescription, action = null) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(box)) {
            val r = size.minDimension / 2f
            if (checked) {
                drawCircle(ring, radius = r)
                val p = Path().apply {
                    moveTo(size.width * 0.27f, size.height * 0.52f)
                    lineTo(size.width * 0.43f, size.height * 0.69f)
                    lineTo(size.width * 0.75f, size.height * 0.33f)
                }
                drawPath(
                    p, Color.White,
                    style = Stroke(
                        width = size.width * 0.13f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            } else {
                val sw = 1.8.dp.toPx()
                // 极淡底 + 环：白卡上不选也看得见边界，又不至于和色条抢眼
                drawCircle(ring.copy(alpha = if (dark) 0.10f else 0.08f), radius = r - sw)
                // 深色 0.62 → 0.40：原来这一圈环比标题还亮，21 条一屏时最扎眼（用户 2026-11-05）
                drawCircle(
                    ring.copy(alpha = if (dark) 0.40f else 0.55f),
                    radius = r - sw / 2,
                    style = Stroke(width = sw)
                )
            }
        }
    }
}
