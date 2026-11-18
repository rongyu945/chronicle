package com.rongyu.shixuji.ui

import androidx.compose.runtime.Immutable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * 连续曲率圆角（squircle），外观对齐 iOS 的圆角观感。
 *
 * 和 [RoundedCornerShape] 的区别：正圆弧在「直边 → 圆弧」的接点曲率是突变的
 * （0 瞬间跳到 1/r），所以大半径时角上能看出一道折；squircle 的角是超椭圆
 * |x/a|ⁿ + |y/b|ⁿ = 1，曲率从 0 连续增到峰值，没有突变点，角显得更「鼓」、过渡更顺。
 * n = 5 是 iOS 圆角的近似值（n 越大越接近方形，n = 2 退化为正圆弧）。
 *
 * 实现方式：四角按参数方程采样后连成 Path，每角 [SEGMENTS] 段。24dp 圆弧上
 * 采 24 段时折线与真实曲线的最大偏差 < 0.03px，低于一个物理像素，看不出折线。
 * 之所以不用贝塞尔逼近，是因为超椭圆的控制点系数要按 n 解方程，采样反而更直白可靠。
 *
 * 为什么不用 miuix-squircle 库：该库 aar-metadata 要求 minCompileSdk = 37、
 * 元数据由 Kotlin 2.4 编译，本项目 compileSdk 34 + Kotlin 2.0.21，一加依赖
 * checkDebugAarMetadata 就报 29 条错误。这里零依赖自实现，minSdk 31 无障碍。
 */
@Immutable
class SquircleShape(
    val cornerRadius: Dp,
    private val exponent: Float = DEFAULT_EXPONENT,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))

        val raw = with(density) { cornerRadius.toPx() }
        val half = min(size.width, size.height) / 2f
        if (raw <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        // 半径吃到半高 = 胶囊（或正方形时是正圆）。
        // 这里绝对不能走超椭圆：超椭圆在 r = 半高时两端会退化成「方一点半椭圆」，
        // 和真正的半圆端差得很明显 —— 胶囊按钮看上去像被啃掉一块（用户实测：
        // SplashScreen 的「开启时序集」、账目页的常用记账胶囊都碎了）。
        // 圆角矩形在 r = 半高时恰好是两个正圆端，才是胶囊该有的样子。
        if (raw >= half - 0.01f) {
            return Outline.Rounded(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(half, half)))
        }
        return Outline.Generic(buildPath(size.width, size.height, raw, exponent))
    }

    internal fun exponentValue(): Float = exponent

    override fun equals(other: Any?): Boolean =
        other is SquircleShape && other.cornerRadius == cornerRadius && other.exponent == exponent

    override fun hashCode(): Int = 31 * cornerRadius.hashCode() + exponent.hashCode()

    override fun toString(): String = "SquircleShape(cornerRadius=$cornerRadius, exponent=$exponent)"
}

private const val DEFAULT_EXPONENT = 5f
private const val SEGMENTS = 24
private const val PI_F = 3.1415927f

/**
 * Path 缓存。createOutline 每次绘制/布局都会调，滚动时每帧都可能重算；
 * 同尺寸同半径的结果完全一样，缓存下来省掉 96 次三角函数。
 * key 里带上 density —— 同一个逻辑尺寸在不同 density 下 px 半径不同。
 * 上限 64 条，超过就整体清空（条目尺寸种类很有限，清空极少发生）。
 */
private data class PathKey(val w: Float, val h: Float, val r: Float, val n: Float)

private val pathCache = LinkedHashMap<PathKey, Path>()

private fun buildPath(w: Float, h: Float, r: Float, n: Float): Path {
    // ⚠️ key 必须逐字段比。早先用 (w.bits shl 40) xor (h.bits shl 20) xor ... 打包成一个
    // Long，32 位的 float bits 左移 40/20 位会互相覆盖，不同尺寸极易撞成同一个 key ——
    // 撞上就复用了别的尺寸的 Path，画出来是「被斜切掉一角」的怪形状
    // （用户实测：启动页「开启时序集」按钮、账目页常用记账胶囊都碎成这样）。
    val key = PathKey(w, h, r, n)
    pathCache[key]?.let { return it }

    val path = Path()
    val steps = SEGMENTS

    // 每角：角心 (cx, cy)，参数 θ 从 a0 扫到 a0 + 90°，取超椭圆上的点
    fun corner(cx: Float, cy: Float, a0: Float) {
        for (i in 0..steps) {
            val t = a0 + (PI_F / 2f) * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = cx + r * abs(c).pow(2f / n) * c.sign
            val y = cy + r * abs(s).pow(2f / n) * s.sign
            if (i == 0 && cx == w - r && cy == r) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    // 右上 → 右下 → 左下 → 左上（顺时针），θ 起点分别是 -90° / 0° / 90° / 180°
    corner(w - r, r, -PI_F / 2f)
    corner(w - r, h - r, 0f)
    corner(r, h - r, PI_F / 2f)
    corner(r, r, PI_F)
    path.close()

    if (pathCache.size > 64) pathCache.clear()
    pathCache[key] = path
    return path
}

/**
 * 取这个 squircle 在某尺寸下的 Path。
 *
 * 为什么需要它：glassSurface / paperSurface 这类组件的「卡身」是用 drawRoundRect 画的，
 * clip(shape) 只负责裁边。而超椭圆是**包住**同半径圆角矩形的（角更鼓、更靠外），
 * 所以裁剪后剩下的还是那个圆角矩形 —— 光换个 shape 传进去等于没换。
 * 卡身必须自己按 squircle 的路径画，裁剪才不是白做。
 */
fun SquircleShape.toPath(size: Size, density: Density): Path {
    val raw = with(density) { cornerRadius.toPx() }
    val half = min(size.width, size.height) / 2f
    // 与 createOutline 保持一致：半径吃满半高时要的是胶囊，不是「方一点的椭圆」
    if (raw >= half - 0.01f) {
        return Path().apply {
            addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(half, half)))
        }
    }
    return buildPath(size.width, size.height, raw, exponentValue())
}

/** 常用尺寸的快捷构造。 */
fun squircle(radius: Dp, exponent: Float = DEFAULT_EXPONENT): Shape = SquircleShape(radius, exponent)

/**
 * 胶囊形状 —— 两端是**超椭圆端头**，不是正圆端头。
 *
 * 为什么不用 `RoundedCornerShape(percent = 50)`：那种胶囊两端是正圆，圆和直边的接点
 * 曲率从 0 突变到 1/r。半径一两百 px 的大胶囊看不出来，但 26~36dp 高的按钮、芯片上
 * 就是肉眼可见的一道硬拐 —— 用户 2026-11-08：「保存和取消的胶囊直线和弧线连接的地方
 * 非常生硬」。这是**全 App 所有胶囊的通病**（弹窗按钮、芯片、排序页保存键…），不是个别。
 *
 * 超椭圆的数学性质正好治这个：n > 2 时端头与直边的接点处曲率为 0，曲率沿端头连续增大，
 * 没有突变点（详见 [SquircleShape] 的类注释）。[CAPSULE_EXPONENT] = 2.2 只比正圆「鼓」
 * 一点点 —— 看上去仍是一条圆头胶囊，但那道硬拐被抹平了。n = 2 就是正圆，n 越大越方。
 *
 * 注意：本类**不做** `r >= 半高` 的回退（[SquircleShape] 里那个回退是为了防止
 * 大半径胶囊退化成方头，这里天生就是吃满半高）。宽 = 高时退回正圆。
 */
@Immutable
class CapsuleShape(private val exponent: Float = CAPSULE_EXPONENT) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        val r = min(size.width, size.height) / 2f
        if (abs(size.width - size.height) < 0.5f) {
            return Outline.Rounded(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(r, r)))
        }
        return Outline.Generic(buildCapsulePath(size.width, size.height, r, exponent))
    }

    internal fun exponentValue(): Float = exponent

    override fun equals(other: Any?): Boolean =
        other is CapsuleShape && other.exponent == exponent

    override fun hashCode(): Int = 17 * 31 + exponent.hashCode()

    override fun toString(): String = "CapsuleShape(exponent=$exponent)"
}

/** 胶囊的端头指数：2 = 正圆（接点曲率突变），越大越接近方头。2.2 是肉眼仍圆、接点已平的值。 */
const val CAPSULE_EXPONENT = 2.2f

/** 常用写法：`shape = capsule()` */
fun capsule(exponent: Float = CAPSULE_EXPONENT): Shape = CapsuleShape(exponent)

private val capsuleCache = LinkedHashMap<PathKey, Path>()

/**
 * 胶囊路径：上直边 → 右端半超椭圆 → 下直边 → 左端半超椭圆。
 *
 * 与 [buildPath] 分开写，是因为那种「四角 + moveTo」的拼法在半高处两个角心会**重合**
 * （h = 2r 时 cy 相同），第二个角的第一个点又触发一次 moveTo，形状会断成两条子路径，
 * 描边时接缝处露出一条横线。
 */
private fun buildCapsulePath(w: Float, h: Float, r: Float, n: Float): Path {
    val key = PathKey(w, h, r, n)
    capsuleCache[key]?.let { return it }

    val path = Path()
    val steps = SEGMENTS
    val half = PI_F / 2f

    fun arc(cx: Float, cy: Float, from: Float) {
        for (i in 0..steps) {
            val t = from + (PI_F) * i / steps
            val c = cos(t)
            val s = sin(t)
            path.lineTo(
                cx + r * abs(c).pow(2f / n) * c.sign,
                cy + r * abs(s).pow(2f / n) * s.sign,
            )
        }
    }

    path.moveTo(r, 0f)
    path.lineTo(w - r, 0f)          // 上直边
    arc(w - r, r, -half)            // 右端：-90° → +90°
    path.lineTo(r, h)               // 下直边
    arc(r, r, half)                 // 左端：+90° → +270°
    path.close()

    if (capsuleCache.size > 64) capsuleCache.clear()
    capsuleCache[key] = path
    return path
}

/** 取胶囊在当前尺寸下的 Path（画卡身/描边用，见 [SquircleShape.toPath] 的说明）。 */
fun CapsuleShape.toPath(size: Size, density: Density): Path {
    val r = min(size.width, size.height) / 2f
    if (abs(size.width - size.height) < 0.5f) {
        return Path().apply {
            addOval(Rect(size.width / 2f - r, size.height / 2f - r, size.width / 2f + r, size.height / 2f + r))
        }
    }
    return buildCapsulePath(size.width, size.height, r, exponentValue())
}

/**
 * 画一块「满尺寸的卡身/描边」，形状由 shape 决定。
 *
 * 圆角矩形走 drawRoundRect（GPU 直接画，便宜）；squircle 走 drawPath。
 * 只用于铺满整块区域的调用 —— 像顶部那条 1px 受光线、内缩的局部条带，
 * 它们的形状本身就是矩形，不该跟着变。
 */
/**
 * 取这个 shape 的「名义角半径」（px）。画描边/内缩这类只能给半径的地方用它。
 * 超椭圆没有真正的圆弧半径，cornerRadius 就是它的构造半径 —— 描边 1px 的场合够用。
 */
internal fun Shape.cornerPx(size: Size, density: Density): Float = when (this) {
    is RoundedCornerShape -> topStart.toPx(size, density)
    is SquircleShape -> with(density) { cornerRadius.toPx() }
    is CapsuleShape -> min(size.width, size.height) / 2f
    else -> 0f
}

internal fun DrawScope.drawCardShape(
    shape: Shape,
    size: Size,
    corner: CornerRadius,
    /** 四周内缩（px）。描边要内缩半个线宽才不会糊到形状外面 */
    inset: Float = 0f,
    brush: Brush? = null,
    color: Color = Color.Unspecified,
    style: DrawStyle = Fill,
) {
    val inner = Size((size.width - inset * 2f).coerceAtLeast(0f), (size.height - inset * 2f).coerceAtLeast(0f))
    val path = when (val s = shape) {
        is SquircleShape -> s.toPath(inner, this@drawCardShape)
        is CapsuleShape -> s.toPath(inner, this@drawCardShape)
        else -> null
    }?.let {
        Path().apply {
            addPath(it)
            if (inset != 0f) translate(Offset(inset, inset))
        }
    }
    if (path != null) {
        if (brush != null) drawPath(path, brush, style = style)
        else drawPath(path, color, style = style)
    } else {
        val c = CornerRadius((corner.x - inset).coerceAtLeast(0f), (corner.y - inset).coerceAtLeast(0f))
        if (brush != null) drawRoundRect(brush, topLeft = Offset(inset, inset), size = inner, cornerRadius = c, style = style)
        else drawRoundRect(color, topLeft = Offset(inset, inset), size = inner, cornerRadius = c, style = style)
    }
}
