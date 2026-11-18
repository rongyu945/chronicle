package com.rongyu.shixuji.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 方向箭头（‹ / ›），**用 Path 画，不用字符**。
 *
 * 为什么不用 `Text("›")`：这类符号的墨迹在字体度量里偏基线一侧，排版框（ascent/descent
 * 撑出来的行高）却是上下对称的 —— 「把字放进格子里居中」得到的是排版框居中，字形看起来
 * 总是**偏下**。本项目内置 songti_sc 的度量为 ascent 1.06em / descent 0.435em，偏差尤其
 * 明显；之前靠在字形上补 `offset(y = -2.4.dp)` 硬掰，改字号或换字体就又不准了。
 *
 * 几何（2026-11-01 用户指定的图标规格，照 Material 的 chevron 观感）：
 *  - 两段等长、**夹角 90°**：折线总高 h，横向进深 dx = h/2，于是每段与竖直方向成 45°；
 *  - **无填充、只描边**；端点 `StrokeCap.Butt`（平头），拐点 `StrokeJoin.Miter`（尖角）；
 *  - 外形水平/竖直都居中；画布是正方形（[size] 见方），线宽再大也不会被裁到
 *    —— 竖直方向留 (1-0.66)/2 的余量，横向 miter 外扩约 0.71×stroke 也吃得下。
 */
enum class ChevronDir { Left, Right }

@Composable
fun Chevron(
    dir: ChevronDir,
    modifier: Modifier = Modifier,
    size: Dp = 13.dp,
    stroke: Dp = 1.2.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier.size(size)) {
        val s = minOf(this.size.width, this.size.height)
        val strokePx = stroke.toPx().coerceAtLeast(1f)
        val cy = s / 2f
        // 折线总高占画布 66%，横向进深 = 半高 → 两段各 45°，拐点夹角正好 90°
        val h = s * 0.66f
        val dx = h / 2f
        val x0 = (s - dx) / 2f                     // 形状水平居中
        val tipX = if (dir == ChevronDir.Left) x0 else x0 + dx
        val armX = if (dir == ChevronDir.Left) x0 + dx else x0
        val path = Path().apply {
            moveTo(armX, cy - h / 2f)
            lineTo(tipX, cy)
            lineTo(armX, cy + h / 2f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokePx,
                cap = StrokeCap.Butt,
                join = StrokeJoin.Miter
            )
        )
    }
}

/**
 * 可点的箭头按钮：外面一个 [box] 见方的点击区（够手指点），里面居中的 [chevron] 大小折线。
 * 竖直/水平居中由画布保证，不需要任何补偿偏移。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChevronButton(
    dir: ChevronDir,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    box: Dp = 40.dp,
    chevron: Dp = 13.dp,
    stroke: Dp = 1.2.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    corner: Dp = 8.dp,
    // 折线在点击区里默认居中；点击区往往比折线宽得多（4–5dp 的墨迹放进 25dp 的格子），
    // 想让它贴近旁边的文字时传正值（向右）/负值（向左）把墨迹挪过去，点击区不变。
    shiftX: Dp = 0.dp,
    // 2026-11-07 用户：「左边的热区再往左去一点」。
    // 数值只是往**左**扩命中区，墨迹与右边界都不动（整体先左移 growStart，再把墨迹靠右对齐）。
    // 不能靠加大 box 实现：box 变大是左右同时扩，右边会盖到日期文字，正是要避免的误触。
    growStart: Dp = 0.dp,
    contentDescription: String? = null,
) {
    // 2026-11-07 用户：「严格缩小这两个箭头的热区范围」。
    // 默认 Modifier.clickable 外面套着 Material3 的 minimumInteractiveComponentSize，
    // 会把 25dp 的格子自动撑到 48dp —— uiautomator 实测 126×123px。后果是：
    //   ① 左右两个热区中间只剩 12.6dp，「7日」正好落在这条缝里，点日期被误判成翻页；
    //   ② 左箭头热区左边缘 x=192，而「11月」文字右边缘 x=235，热区盖住「月」字右半 43px。
    // 把最小交互尺寸置空后，热区严格等于 box（视觉多大就能点多大）。
    // 代价：触摸目标低于 M3 建议的 48dp，无障碍合规性下降 —— 视觉精度优先，这里接受。
    // 与 StepperArrow 同一个坑：pointerInput 只按 enabled 作 key，手势检测器不会重建，
    // 闭包里直接调 onClick 就永远调到**第一次**那个（捕获的是旧状态 —— 日期步进器上
    // 表现为「只能改一天」，2026-11-18 实测复现）。用 rememberUpdatedState 兜住。
    val latestOnClick by rememberUpdatedState(onClick)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Box(
            modifier
                .offset(x = -growStart)
                .size(width = box + growStart, height = box)
                .clip(RoundedCornerShape(corner))
                // 关键：不能再用 Modifier.clickable。它的触摸区会被 Compose 的
                // TouchBoundsExpansion 撑到系统最小触摸目标 48dp（126px），
                // 这一层是 View 级别的，CompositionLocalProvider 里的
                // LocalMinimumInteractiveComponentSize 管不到它（实测置空后 dump 仍是 126×123）。
                // 换成 pointerInput 自绘命中区，热区就严格等于 .size(box)。
                .pointerInput(enabled) {
                    if (enabled) detectTapGestures(onTap = { latestOnClick() })
                }
                .semantics {
                    if (contentDescription != null) this.contentDescription = contentDescription
                    role = Role.Button
                    if (enabled) onClick { latestOnClick(); true }
                },
            contentAlignment = if (growStart > 0.dp) Alignment.CenterEnd else Alignment.Center
        ) {
            Chevron(
                dir = dir,
                modifier = if (shiftX == 0.dp) Modifier else Modifier.offset(x = shiftX),
                size = chevron,
                stroke = stroke,
                color = if (enabled) color else MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

/** 极简版：没有底色/水波纹的箭头（列表右侧那种装饰性 chevron 用） */
@Composable
fun ChevronPlain(
    dir: ChevronDir,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    stroke: Dp = 1.2.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val clickableModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier.background(Color.Transparent)
    // 点击区做成 32dp，箭头画在正中
    Box(modifier.then(clickableModifier).size(32.dp), contentAlignment = Alignment.Center) {
        Chevron(dir = dir, size = size, stroke = stroke, color = color)
    }
}
