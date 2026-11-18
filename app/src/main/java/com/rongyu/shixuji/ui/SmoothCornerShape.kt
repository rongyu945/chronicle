package com.rongyu.shixuji.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * **G3 圆角**（曲率导数连续）——7.6.12。
 *
 * 为什么不用 `RoundedCornerShape`：它给的是**圆弧**。直线和圆弧在相切点只做到 G1
 * （位置和切线连续），曲率却从 0 直接跳到 1/r —— 眼睛看到的就是「直边走到某处突然开始弯」，
 * 那条突变就是圆角看起来「硬」的原因。
 *
 * 这里把每个角换成**超椭圆象限**：`|x/S|^n + |y/S|^n = 1`（n≈4）。
 * 它在轴上（也就是与直边相接的那个点）曲率恰好为 0，且曲率随弧长变化连续、导数也连续
 * —— 接缝处直边是 (κ=0, dκ/ds=0)、角也是 (κ=0, dκ/ds=0)，两边严丝合缝，这就是 G3。
 * 视觉上：直边是「慢慢开始弯、弯得越来越快、到 45° 最弯、再对称地回平」，没有断点。
 *
 * 参数：
 * - [radius] 名义圆角（和原来 `RoundedCornerShape(13.dp)` 观感对齐，不是精确半径）；
 * - [smoothing] 角沿边方向的延伸倍数，`S = radius * (1 + smoothing)`；越大越「流」，
 *   0 就退化成接近圆弧；
 * - [exponent] 超椭圆指数 n，越大角越方、过渡越急；4 是 iOS squircle 那一档。
 *
 * 采样 20 段/角就够：13dp 的角在 3x 屏上总长不到 200px，20 段每段 <10px，
 * 而且曲线本身处处光滑，不需要更高密度（也不该用 lineTo 堆到几百点，那是浪费）。
 */
class SmoothCornerShape(
    private val radius: Dp = 13.dp,
    private val smoothing: Float = 0.6f,
    private val exponent: Float = 4f,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Rectangle(Rect(0f, 0f, w, h))

        val r = with(density) { radius.toPx() }
        // S = 角在每条边上的延伸长度；不允许超过短边的一半，否则四个角的曲线会互相吃掉
        val s = (r * (1f + smoothing)).coerceIn(0f, minOf(w, h) / 2f)
        if (s <= 0.5f) return Outline.Rectangle(Rect(0f, 0f, w, h))

        val path = Path()
        // ★ 7.6.15：段数改为按角的像素长度自适应。
        //   原来固定 20 段，13dp/26dp 的角在 2.6x 屏上是 68–88px，每段 3.4–4.4px，
        //   折线的拐点肉眼可见（用户：「直线曲线的连接处还是有很明显的锯齿感」）。
        //   压到每段 ≈1px，曲线就与像素栅格同量级，看不出是折线了。
        val steps = (s / 1.1f).toInt().coerceIn(16, 240)
        // 超椭圆象限：以 (cx,cy) 为角内侧的圆心，sx/sy 决定朝哪个象限鼓出去。
        // θ 从 0 到 π/2：x 方向用 sin^(2/n)，y 方向用 cos^(2/n)。
        // ★ 坑：四个角的**行进方向**不一样，θ 的方向必须跟着反。
        //   右上/左下是从「竖切点」走到「横切点」（θ 0→π/2），
        //   右下/左上反过来（θ π/2→0）。第一版没管方向，每个角都会多出一条对角线，
        //   Path 自交后填充出一块楔形白斑 —— 实机上就是每颗键缺了个角。
        fun corner(cx: Float, cy: Float, sx: Float, sy: Float, reversed: Boolean = false) {
            for (i in 0..steps) {
                val k = if (reversed) steps - i else i
                val t = (Math.PI / 2.0) * k / steps
                val ex = Math.sin(t).pow(2.0 / exponent.toDouble()).toFloat()
                val ey = Math.cos(t).pow(2.0 / exponent.toDouble()).toFloat()
                path.lineTo(cx + sx * s * ex, cy + sy * s * ey)
            }
        }

        path.moveTo(s, 0f)
        path.lineTo(w - s, 0f)
        corner(w - s, s, +1f, -1f)                    // 右上：(w-s,0) → (w,s)
        path.lineTo(w, h - s)
        corner(w - s, h - s, +1f, +1f, reversed = true) // 右下：(w,h-s) → (w-s,h)
        path.lineTo(s, h)
        corner(s, h - s, -1f, +1f)                    // 左下：(s,h) → (0,h-s)
        path.lineTo(0f, s)
        corner(s, s, -1f, -1f, reversed = true)        // 左上：(0,s) → (s,0)
        path.close()

        return Outline.Generic(path)
    }
}

/**
 * 浅浅一层包装，语义更直白：键、卡片这类「压在纸上的一块」统一用这个形状。
 */
fun smoothShape(radius: Dp = 13.dp, smoothing: Float = 0.6f, exponent: Float = 4f): Shape =
    SmoothCornerShape(radius, smoothing, exponent)
