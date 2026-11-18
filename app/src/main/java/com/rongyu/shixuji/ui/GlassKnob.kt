package com.rongyu.shixuji.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * 统一的「玻璃圆钮」画法（开关滑块、滑块滑块、分段控件药丸都用它）。
 *
 * 2026-11-10 用户反馈两处问题，根因是同一个：
 *   ①「使用宋体的开关那个白色的圆都不是圆的了」——旧代码把顶部高光当成一个
 *      **半径 0.8r、圆心在 c.y-0.30r 的独立圆**画上去，它有一部分跑到滑块圆外面
 *      （向上 1.1r），和白色卡底融成一片，剪影就不再是圆；
 *   ②「撤回停留时间的圆形滑块白色高光让它看上去像缺了一角」——高光画在白瓷底之后、
 *      但在**主色描边之前**，结果 0.85 的白把上半个描边环糊掉了，看着像环缺一段。
 *
 * 现在的次序是「影 → 体 → 底缘内阴影 →（裁剪到圆内）高光 → 描边」，
 * 高光被 clipPath 关在圆里，描边永远最后画 —— 两处症状从结构上不会再出现。
 *
 * @param center 圆心
 * @param radius 半径（不含描边）
 * @param dark   深色主题
 * @param rimColor 描边颜色；null = 不画描边
 * @param rimWidth 描边宽度（px）
 * @param shadowScale 投影强度，1f = 默认
 */
internal fun DrawScope.drawGlassKnob(
    center: Offset,
    radius: Float,
    dark: Boolean,
    rimColor: Color?,
    rimWidth: Float,
    shadowScale: Float = 1f,
) {
    val r = radius
    val circle = Path().apply {
        addOval(Rect(left = center.x - r, top = center.y - r, right = center.x + r, bottom = center.y + r))
    }

    // ① 影：两圈低透明度、略向下、略大 —— 只为了「浮起来 1mm」，不要变成厚塑料
    if (shadowScale > 0f) {
        drawCircle(
            color = Color.Black.copy(alpha = (if (dark) 0.26f else 0.10f) * shadowScale),
            radius = r * 1.06f,
            center = Offset(center.x, center.y + r * 0.10f)
        )
        drawCircle(
            color = Color.Black.copy(alpha = (if (dark) 0.18f else 0.06f) * shadowScale),
            radius = r * 1.02f,
            center = Offset(center.x, center.y + r * 0.18f)
        )
    }

    // ② 体：上亮下柔，不是一块死白（死白会把后面的高光吃掉，钮就没有体积）
    if (dark) {
        drawCircle(Color(0xFFF2EFEA), radius = r, center = center)
        drawCircle(
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.26f), Color.Transparent),
                startY = center.y - r, endY = center.y + r * 0.3f
            ),
            radius = r, center = center
        )
    } else {
        drawCircle(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFFF3EFE8)),
                startY = center.y - r, endY = center.y + r
            ),
            radius = r, center = center
        )
    }

    // ③ 底缘内阴影：让下缘看起来比上缘厚（贴边，不出圆）
    clipPath(circle) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.16f else 0.07f)),
                startY = center.y + r * 0.25f, endY = center.y + r
            ),
            topLeft = Offset(center.x - r, center.y - r),
            size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f)
        )
    }

    // ④ 顶部镜面：**裁到圆里**，所以它只会在钮的表面上，不会溢出去污染剪影
    clipPath(circle) {
        drawCircle(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (dark) 0.42f else 0.62f),
                    Color.Transparent
                ),
                startY = center.y - r * 0.92f,
                endY = center.y + r * 0.10f
            ),
            radius = r * 0.92f,
            center = Offset(center.x, center.y - r * 0.34f)
        )
    }

    // ⑤ 描边最后画：白瓷钮上是一圈极淡亮边（亮在光里），滑块钮上是主色细环
    if (rimColor != null && rimWidth > 0f) {
        drawCircle(
            color = rimColor,
            radius = r - rimWidth / 2f,
            center = center,
            style = Stroke(rimWidth)
        )
    }
}
