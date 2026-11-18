package com.rongyu.shixuji.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 放大镜（账目搜索入口）。
 *
 * 与 LoopIcon 同一个理由：不用 emoji、不用图片资源，直接在 Canvas 上画 —— 任意尺寸都清晰，
 * 颜色跟随主题，粗细微调只改一个数。
 *
 * 形状＝一个圆 + 一条 45° 手柄；按 24×24 网格给坐标，圆心上偏左，手柄落在右下，
 * 这样整个图形在方框里视觉居中（圆本身占左上方，右侧留给手柄）。
 */
@Composable
fun SearchGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    stroke: Dp = 1.7.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val s = this.size.minDimension / 24f      // 网格单位 → 像素
        val w = stroke.toPx()
        val center = Offset(10.2f * s, 10.2f * s)
        val r = 6.4f * s
        drawCircle(color, radius = r, center = center, style = Stroke(width = w, cap = StrokeCap.Round))
        // 手柄从圆的右下沿 45° 伸出（起点正好落在圆周上，免得看着断开）
        val k = 0.7071f
        drawLine(
            color = color,
            start = Offset(center.x + r * k, center.y + r * k),
            end = Offset(21.6f * s, 21.6f * s),
            strokeWidth = w,
            cap = StrokeCap.Round,
        )
    }
}
