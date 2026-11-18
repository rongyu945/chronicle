package com.rongyu.shixuji.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * 抗锯齿绘制小工具（2026-11-12，7.6.16 新增）。
 *
 * 为什么需要：`Modifier.clip(shape)` 走的是 Skia 的**二值裁剪**，曲线边界不做抗锯齿。
 * 圆角小的时候（RoundedCornerShape）还算能看，一旦换成超椭圆 / 任意 Path 形状，
 * 斜段就被裁成一级级的阶梯 —— 这就是用户看到的「直线曲线的连接处有锯齿」。
 * 想真正平滑，唯一的办法是**自己 drawPath**：填充和描边在 Skia 里都是抗锯齿的。
 * 描边不用管：`Modifier.border` 本来就用 Stroke 画 Path，所以它一直是抗锯齿的。
 */

/** 把任意 Shape 摊平成 Path（Outline 的四种形态全覆盖）。 */
fun Shape.toAaPath(size: Size, density: Density): Path =
    when (val o = createOutline(size, LayoutDirection.Ltr, density)) {
        is Outline.Generic -> o.path
        is Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(o.rect) }
        else -> Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
    }

/** 用抗锯齿路径填充一个形状（用来替代 `clip(shape).background(color)`）。 */
fun Modifier.aaFill(shape: Shape, color: Color): Modifier = drawBehind {
    drawPath(shape.toAaPath(size, this), color)
}

/** 用抗锯齿路径描边（替代需要 clip 的 border；描边比 border 宽半个线宽）。
 *  stroke 以路径为中心画，所以比 `Modifier.border` 向外多半条线宽；1dp 键盘键可忽略。 */
fun Modifier.aaStroke(shape: Shape, color: Color, width: Dp): Modifier = drawBehind {
    drawPath(shape.toAaPath(size, this), color, style = Stroke(width.toPx()))
}
