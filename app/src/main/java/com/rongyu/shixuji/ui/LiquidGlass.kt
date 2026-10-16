package com.rongyu.shixuji.ui

/*
 * 液态玻璃（真实折射）—— 机制移植自 Kyant0/AndroidLiquidGlass 的 Backdrop（Apache-2.0）。
 *
 * 三步：
 *   ① 用 GraphicsLayer 把玻璃「身后」的画面录下来（录的是绘制指令，不是截图，很便宜）；
 *   ② 给这一层挂 RenderEffect = AGSL RuntimeShader：按圆角矩形的有符号距离场(SDF)算每个像素
 *      离边缘多远，越靠边采样点越往中心推 —— 这就是折射；外面再套一层模糊；
 *   ③ 把玻璃体画出来，图标/文字画在**它外面**（玻璃层里画就会被一起折射+模糊掉）。
 *
 * ⚠️ 血泪教训（都真的踩过）：
 *   · 录制层必须 **先 drawContent() 再 record()**；反过来内容会整块消失且不报错。
 *   · 玻璃体必须是**独立的一层**，绝不能和图标/文字共享同一个带 RenderEffect 的图层 ——
 *     否则图标会被折射和模糊一起糊掉。
 *   · 图层要 `clip = true` + `compositingStrategy = Offscreen`，否则 RenderEffect 取景失控。
 *   · RenderEffect 只在 API 33+ 可用（AGSL）。低于 33 时 [Modifier.liquidGlass] 直接不动，
 *     调用方保留原来的 glassSurface 外观 —— 这是设计，不是 bug。
 *
 * 为什么不直接加那 6 个库的依赖：它们全要 compileSdk 36/37 + AGP 9 + Kotlin 2.4，还是 KMP 工程；
 * 本工程是 34 + AGP 8.13 + Kotlin 2.0.21 的纯 Android。所以只移植渲染逻辑，不动工具链。
 */

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rongyu.shixuji.theme.isDarkTheme
import kotlin.math.roundToInt

/** AGSL(RuntimeShader) 从 Android 13 / API 33 起可用 */
val supportsLiquidGlass: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * 折射着色器（改写自 Kyant0 的 Lens）。
 *   content          玻璃身后的画面，由 RenderEffect 自动喂进来
 *   size             玻璃块尺寸（px）
 *   cornerRadii      四角半径：左上、右上、右下、左下
 *   refractionHeight 从边缘往内多少 px 开始折射（"玻璃有多厚"）
 *   refractionAmount 最大位移量（越大越像透镜）
 *   depthEffect      1 时叠加朝中心的位移，整块凸起
 */
private const val REFRACTION_SHADER = """
uniform shader content;
uniform float2 size;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius)
                            + depthEffect * normalize(centeredCoord));

    return content.eval(coord + d * grad);
}
"""

/**
 * 背景层：把「玻璃身后的画面」额外录一份存这里，供玻璃采样。
 * 由 [rememberLiquidGlassBackdrop] 创建。
 */
class LiquidGlassBackdropState internal constructor(internal val layer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? by mutableStateOf(null)
}

@Composable
fun rememberLiquidGlassBackdrop(): LiquidGlassBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LiquidGlassBackdropState(layer) }
}

/**
 * 背景玻璃层（由 [Modifier.recordBackdrop] 在根部录好）。
 *
 * ⚠️ 为什么条目不能直接用底栏那个「内容层」：条目本身就在内容层里面，
 * 采样它等于把自己录进自己 → 递归/闪烁。所以条目必须采样一个**只含背景**的独立层。
 */
val LocalLiquidGlassBackdrop = androidx.compose.runtime.compositionLocalOf<LiquidGlassBackdropState?> { null }

/**
 * 把这一层的绘制内容**额外录一份**进背景层。
 *
 * 只"复制"不"替换"：`drawContent()` 先照常画一遍，再录一遍，所以**画面本身完全不受影响**。
 * 直接挂在你原本就有的内容容器上，不需要为它多加一层 Box。
 *
 * ⚠️ 玻璃元素必须写在挂了本 Modifier 的那层**外面**，否则会把自己也录进去 → 递归。
 *
 * ```
 * val backdrop = rememberLiquidGlassBackdrop()
 * Box {
 *     Box(Modifier.fillMaxSize().recordBackdrop(backdrop)) { 页面内容 }
 *     GlassBottomBar(backdrop = backdrop, ...)   // 在外面
 * }
 * ```
 */
fun Modifier.recordBackdrop(state: LiquidGlassBackdropState): Modifier = this
    .onGloballyPositioned { if (it.isAttached) state.coordinates = it }
    .drawWithContent {
        // 先画，再录。顺序反了内容会整块消失，且不报任何错。
        drawContent()
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        if (w > 0 && h > 0) {
            state.layer.record(IntSize(w, h)) { this@drawWithContent.drawContent() }
        }
    }

/**
 * 液态玻璃体（只管"玻璃"本身，不含内容）。
 *
 * **必须作为一个独立的 Box 用，图标/文字写在这个 Box 的父容器里、它之后**：
 * ```
 * Box(Modifier.size(w, h)) {
 *     Box(Modifier.matchParentSize().liquidGlass(backdrop, shape))   // 玻璃在下面
 *     Row { Icon(...); Text(...) }                                   // 内容在上面，清晰
 * }
 * ```
 * 实现要点：**不手工建图层、不手工 drawLayer**，把 RenderEffect 交给 `Modifier.graphicsLayer`
 * 去管 —— 条件满足时 Compose 会自动把这一层渲到离屏缓冲、套上 RenderEffect 再合成回来。
 *
 * @param backdrop         背景层（[rememberLiquidGlassBackdrop] 创建）
 * @param shape            玻璃形状（折射目前只支持圆角矩形）
 * @param refractionHeight 边缘折射带厚度
 * @param refractionAmount 最大折射位移
 * @param blurRadius       折射前先做的模糊半径
 * @param depthEffect      叠加"整块凸起"的位移
 * @param tint             玻璃表面的薄着色
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: LiquidGlassBackdropState,
    shape: Shape,
    refractionHeight: Dp = 20.dp,
    refractionAmount: Dp = 22.dp,
    blurRadius: Dp = 10.dp,
    depthEffect: Boolean = true,
    tint: Color = Color.White.copy(alpha = 0.10f),
    /** 描边内缩（条目用 2dp，和原 glassSurface(strokeInset=2.dp) 对齐；底栏保持 0） */
    strokeInset: Dp = 0.dp,
    /**
     * 顶部那条横向高光。宽扁的胶囊/卡片上很好看，但**圆钮不能用**：
     * 它是一条横贯 88% 宽度的直线，在 44dp 的圆上会从圆的上半部戳出去，
     * 看起来就是「加号上面有一条很细的白线」（用户 2026-10-15 反馈）。
     */
    topHighlight: Boolean = true,
): Modifier {
    if (!supportsLiquidGlass) return this

    val density = LocalDensity.current
    val dark = isDarkTheme
    val bgLayer = backdrop.layer
    val glassShape = shape

    var myCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var mySize by remember { mutableStateOf(IntSize.Zero) }
    val offset = remember { mutableStateOf(Offset.Zero) }
    val cornerRadii = remember { mutableStateOf(FloatArray(4)) }

    val rhPx = with(density) { refractionHeight.toPx() }
    val raPx = with(density) { refractionAmount.toPx() }
    val blurPx = with(density) { blurRadius.toPx() }

    // ⚠️ RenderEffect 必须在**组合期**算好。
    // 之前是放到 draw 阶段里 `effect.value = ...`、graphicsLayer 再读 —— 那是无效的：
    // 在 graphicsLayer 的 lambda 里读快照不构成依赖，读到的永远是旧值(null)，
    // 结果就是「既不模糊也不折射，只剩一层透明」。
    // key 里带上 offset/尺寸：背景滚动导致偏移变化时会重组并换上新的 shader。
    val glassEffect = remember(mySize, offset.value, cornerRadii.value[0], rhPx, raPx, blurPx, depthEffect) {
        if (mySize.width > 0 && mySize.height > 0) {
            val shader = RuntimeShader(REFRACTION_SHADER)
            shader.setFloatUniform("size", mySize.width.toFloat(), mySize.height.toFloat())
            shader.setFloatUniform(
                "cornerRadii",
                cornerRadii.value[0], cornerRadii.value[1], cornerRadii.value[2], cornerRadii.value[3]
            )
            shader.setFloatUniform("refractionHeight", rhPx)
            shader.setFloatUniform("refractionAmount", -raPx)
            shader.setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)

            val refract = android.graphics.RenderEffect
                .createRuntimeShaderEffect(shader, "content")
            // createChainEffect(外层, 内层)：先模糊，再折射
            val blur = android.graphics.RenderEffect.createBlurEffect(
                blurPx.coerceAtLeast(0.1f), blurPx.coerceAtLeast(0.1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            android.graphics.RenderEffect.createChainEffect(refract, blur).asComposeRenderEffect()
        } else null
    }

    return this
        .onGloballyPositioned { coords ->
            if (!coords.isAttached) return@onGloballyPositioned
            myCoords = coords
            mySize = coords.size
            backdrop.coordinates?.let { bg ->
                offset.value = offsetOf(bg, coords)
                cornerRadii.value = cornerRadiiOf(
                    shape, Size(coords.size.width.toFloat(), coords.size.height.toFloat()), density
                )
            }
        }
        // ── ① 最外层：描边与高光 ─────────────────────────────────────────────
        // 必须画在 drawContent() **之后**：这样它在玻璃层之上、且在 graphicsLayer 之外，
        // 既不会被 clip 裁掉，也不会被折射/模糊糊掉。
        // （之前把描边写在 graphicsLayer 里面 → 整条"镶边"被裁没了，就是"没有框"的原因。）
        .drawWithContent {
            drawContent()
            val cr = cornerRadiusOf(shape, Size(size.width, size.height), density)
            val line = 1.dp.toPx()
            val si = with(density) { strokeInset.toPx() }

            // 顶部一条高光（中间最亮、两头淡出）= 玻璃上缘的镜面反射
            if (topHighlight) drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = if (dark) 0.20f else 0.85f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(size.width * 0.06f, si),
                size = Size(size.width * 0.88f, line),
                cornerRadius = CornerRadius(line / 2, line / 2)
            )
            // 整圈 1px 淡描边
            drawRoundRect(
                color = if (dark) Color.White.copy(alpha = 0.14f)
                else Color(0xFF5A4B96).copy(alpha = 0.16f),
                topLeft = Offset(si, si),
                size = Size(
                    (size.width - si * 2f).coerceAtLeast(0f),
                    (size.height - si * 2f).coerceAtLeast(0f)
                ),
                cornerRadius = CornerRadius(
                    (cr.x - si).coerceAtLeast(0f), (cr.y - si).coerceAtLeast(0f)
                ),
                style = Stroke(line)
            )
            // 底栏那条"镶边"：一圈更亮的白环（与 glassSurface(ring=true) 取值一致；条目不要）
            if (strokeInset == 0.dp) {
                drawRoundRect(
                    color = Color.White.copy(alpha = if (dark) 0.10f else 0.45f),
                    cornerRadius = cr,
                    style = Stroke(line)
                )
            }
        }
        // ── ② 折射层 ───────────────────────────────────────────────────────
        // renderEffect 只作用于**它之后（内层）**的绘制，所以「背景采样 + 体色」必须写在它下面。
        .graphicsLayer {
            renderEffect = glassEffect
            clip = true
            this.shape = glassShape
            compositingStrategy = CompositingStrategy.Offscreen
        }
        // ── ③ 最内层：玻璃体的内容（会被折射 + 模糊）────────────────────────
        .drawWithContent {
            val off = offset.value
            // 把背景层里"玻璃身后那一块"画进本层的离屏缓冲。
            // RenderEffect 的采样坐标系就是这个缓冲 → 整体平移，
            // 让玻璃左上角正好对准背景层的对应位置。
            translate(-off.x, -off.y) { drawLayer(bgLayer) }
            // 玻璃的"体色"：一层很薄的乳白，铺在采样之上
            drawRoundRect(
                color = tint,
                cornerRadius = cornerRadiusOf(shape, Size(size.width, size.height), density)
            )
            drawContent()
        }
}

private fun offsetOf(bg: LayoutCoordinates, my: LayoutCoordinates): Offset = try {
    bg.localPositionOf(my)
} catch (_: Exception) {
    // 外层有旋转/缩放时 localPositionOf 可能抛异常，退回根坐标近似
    my.positionInRoot() - bg.positionInRoot()
}

private fun cornerRadiusOf(shape: Shape, size: Size, density: Density): CornerRadius {
    val r = when (shape) {
        is RoundedCornerShape -> shape.topStart.toPx(size, density)
        else -> 0f
    }
    return CornerRadius(r.coerceAtMost(size.minDimension / 2f))
}

private fun cornerRadiiOf(shape: Shape, size: Size, density: Density): FloatArray {
    val r = cornerRadiusOf(shape, size, density).x
    return floatArrayOf(r, r, r, r)
}
