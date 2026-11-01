package com.rongyu.shixuji.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 循环待办的图标（用户 2026-11-01 选定的「闭环双箭头」，候选 C）。
 *
 * 原来这里是 `Text("🔁")` —— emoji 是彩色的、跟这套墨线界面不搭，而且各机型字形不一样大。
 * 现在改成**画出来的路径**（不用图片资源：不占包体、任意尺寸都清晰、颜色跟随主题）。
 *
 * 形状（24×24 网格，与设计稿同一套坐标）：
 * ```
 *   ┌上横线，右端实心三角朝右────┐
 *   │                       右半圆（上→下）
 *   │                        │
 *   └左半圆（下→上）──左端实心三角朝左
 * ```
 * 两根横线 + 左右两个半圆接成闭环，两个实心三角一进一出 —— 读起来是「来回循环」。
 *
 * 描边与三角**按网格等比缩放**（1.5/24 × 边长）：这样 14dp 和 20dp 的画法完全一致，
 * 不会出现大尺寸太细、小尺寸太糊。
 */
@Composable
fun LoopIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val s = this.size.minDimension / 24f      // 网格单位 → 像素
        val w = 1.65f * s                         // 1.65 网格单位的描边（小尺寸下太细会看不清）
        val lineStyle = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // 闭环：右半圆 → 下横线，左半圆 → 上横线（两个半圆各 180°，与设计稿同一中心/半径）
        val loop = Path().apply {
            moveTo(17f * s, 7f * s)
            arcTo(Rect(12f * s, 7f * s, 22f * s, 17f * s), -90f, 180f, false)   // 右半圆：上→下
            lineTo(11.2f * s, 17f * s)                                          // 下横线（到三角底）
            moveTo(7f * s, 17f * s)
            arcTo(Rect(2f * s, 7f * s, 12f * s, 17f * s), 90f, 180f, false)     // 左半圆：下→上
            lineTo(12.8f * s, 7f * s)                                           // 上横线（到三角底）
        }
        drawPath(loop, color = color, style = lineStyle)

        // 两个实心三角（箭头）：右端朝右、左端朝左
        val arrowTop = Path().apply {
            // 尖端正好落在半圆的起点上（否则箭头与弧之间看着有个豁口）
            moveTo(17f * s, 7f * s)
            lineTo(12.8f * s, 4.65f * s)
            lineTo(12.8f * s, 9.35f * s)
            close()
        }
        val arrowBottom = Path().apply {
            moveTo(7f * s, 17f * s)
            lineTo(11.2f * s, 14.65f * s)
            lineTo(11.2f * s, 19.35f * s)
            close()
        }
        drawPath(arrowTop, color = color)
        drawPath(arrowBottom, color = color)
    }
}
