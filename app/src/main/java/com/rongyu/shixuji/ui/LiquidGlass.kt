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
 * 描边 / 高光的画法。
 *  · [LEGACY]   —— 原来那套：中间 0.85 白的一条直杠 + 整圈 1px 淡描边 + 白环
 *  · [SPECULAR] —— 沿轮廓走的镜面渐变（左上亮、右下淡）+ 顶缘反光 + 底缘反光，
 *                  接近 Snapzy 在 macOS 26 上 `.glassEffect` 自带的那圈折射边
 */
enum class GlassRim {
    /** 旧版：顶部一条 0.85 白直杠 + 整圈 1px 淡描边 */
    LEGACY,
    /** 镜面：沿轮廓渐变的 1px 高光 + 极窄顶缘反光 */
    SPECULAR,
    /**
     * 不画任何描边 —— 边缘光完全交给着色器的**面级高光**。
     *
     * 2026-11-06（用户：「边缘的高光和中间的透明之间的过渡非常生硬」）：
     * 任何 `Stroke(1px)` 都是一条**硬线**：线内是纯色、线外是背景，中间没有过渡，
     * 所以看起来像贴了一圈塑料边。真玻璃的边缘反光是**向内衰减的光带**（十几 dp），
     * 这种渐变只有着色器里按 SDF 距离算才做得出来，画不出来。
     */
    NONE,
}

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
uniform float dispersion;
uniform float rimLight;      // 边缘环境光强度（0 = 关）
uniform float saturation;    // 饱和度增益（1 = 不变）
// 面级方向光（Kyant Highlight.Default）：不是描边，是整块面上按 SDF 梯度与光方向点乘，
// 朝光那半面亮、背光那半面暗。intensity = pow(|dot(grad, normal)|, falloff) * alpha
uniform float2 highlightNormal;
uniform float highlightFalloff;
uniform float highlightAlpha;
uniform float highlightSpread;
uniform float highlightWidth;
uniform float3 highlightColor;

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

// 统一出口：饱和度 + 边缘环境光。
// edge = 0 表示在玻璃中部（内层，不参与边缘光），1 = 正好在轮廓上。
half4 finish(half4 c, float edge, float hl) {
    float3 rgb = float3(c.rgb);
    // 高光是"往高光色里推一点"，不是加光。用加法在浅色纸上会直接溢出成 255 纯白
    // （实测底栏中段整片 (255,255,255)），mix 才有上限：hl=0.3 最多推 30%。
    // 高光色默认纯白；浅暖色系里给一点暖白，边缘和中心才是同一个色相（一体感）。
    if (hl > 0.0) rgb = mix(rgb, highlightColor, hl);
    if (saturation != 1.0) {
        // 真玻璃透过去的颜色比裸眼更"润"（轻微增饱和），完全不动会显得发灰
        float l = dot(rgb, float3(0.2126, 0.7152, 0.0722));
        rgb = mix(float3(l), rgb, saturation);
    }
    if (rimLight > 0.0) {
        // 玻璃边缘会把周围环境光"收"进来：越贴轮廓越亮，且亮度是平方衰减，
        // 集中最外那一圈。这是 1px 描边做不出来的厚度感。
        // 同样往高光色上靠（不是加白）：浅暖底色上加纯白会发冷、和中心分家
        float e = clamp(edge, 0.0, 1.0);
        rgb = mix(rgb, highlightColor, e * e * rimLight);
    }
    return half4(half3(rgb), c.a);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    // SDF 梯度：玻璃内每一点都指向离它最近的那条边
    float2 gradUnit = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);

    // 整面方向光：朝光那半亮的强、背光那半弱 —— 玻璃的"体积"就是这么读出来的。
    float hl = 0.0;
    if (highlightAlpha > 0.0) {
        // ① 只亮"朝光"的那半边（max(d,0)）。Kyant 原版用 abs() 两面都亮，
        //    在深色/彩色背景上看不出问题，但浅纸底上两面都亮就整片泛白、没有方向。
        // ② 再乘一个"离边缘的距离"衰减：不衰减的话，长条胶囊中部每一点的最近边都是上下边，
        //    dot 恒等于 cos45°≈0.71，整片中部被均匀提亮 —— 深色主题下就是一大块白雾
        //    （实测中右段一片发白）。真玻璃的亮也只集中在边缘那一圈。
        // 衰减半径 = 折射带 × highlightSpread。
        //   0.9  → 亮部只贴在轮廓最外一圈，紧挨着就是纯色中腔 = 生硬（浅色问题）
        //   2.0  → 十几 dp 的柔和光带，向内渐隐 = 真玻璃
        // 光带宽度：优先用独立的 highlightWidth（dp→px），否则退回折射带倍数。
        float spread = highlightWidth > 0.5 ? highlightWidth
                                            : max(refractionHeight * highlightSpread, 1.0);
        float edgeFade = 1.0 - smoothstep(0.0, spread, max(-sd, 0.0));
        hl = pow(max(dot(gradUnit, highlightNormal), 0.0), highlightFalloff)
             * highlightAlpha * edgeFade;
    }

    if (-sd >= refractionHeight) {
        // 折射带之内（玻璃中部）：原样透过去，只做色彩处理
        return finish(content.eval(coord), 0.0, hl);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float2 grad = normalize(gradUnit + depthEffect * normalize(centeredCoord));

    half4 c;
    if (dispersion <= 0.0) {
        c = content.eval(coord + d * grad);
    } else {
        // 色散：边缘处把 R / G / B 分别按略不同的折射量采样，形成一圈极细的彩边
        // —— 真玻璃在边缘的色差。dispersion 0 时走上面的单次采样，零额外开销。
        half4 cr = content.eval(coord + d * grad * (1.0 - dispersion));
        half4 cg = content.eval(coord + d * grad);
        half4 cb = content.eval(coord + d * grad * (1.0 + dispersion));
        c = half4(cr.r, cg.g, cb.b, cg.a);
    }
    // 边缘权重：-sd 是到轮廓的距离，0 = 轮廓上，refractionHeight = 折射带内界
    float edge = 1.0 - smoothstep(0.0, refractionHeight * 0.85, -sd);
    return finish(c, edge, hl);
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
    /**
     * 体色的下半段。给了就画成「上亮下淡」的竖向渐变，模拟玻璃上表面受光、下缘变厚的一层底；
     * 不给就是原来的平涂 [tint]。
     */
    tintBottom: Color? = null,
    /** 内阴影厚度（0 = 不画）。底部一条极淡的暗部，给玻璃一个"厚度" */
    innerShadow: Dp = 0.dp,
    /** 边缘色散强度（0 = 关）。0.05~0.10 出很细的彩边，再大就成故障风了 */
    dispersion: Float = 0f,
    /**
     * 边缘环境光强度（0 = 关，建议 0.06~0.14）。
     *
     * 真玻璃的轮廓会把周围的光"收"进来，形成一圈比体色更亮的边 —— 这不是描边能替代的：
     * 描边是均匀的 1px，而环境光是沿轮廓按距离平方衰减的一圈厚度，且会随背景明暗变化。
     */
    rimLight: Float = 0f,
    /** 饱和度增益（1 = 不变，建议 1.06~1.15）。玻璃后的颜色比裸眼更"润" */
    saturation: Float = 1f,
    /**
     * 面级方向光强度（0 = 关，Kyant 的 Highlight.Default 默认 0.5）。
     *
     * 注意它**不是描边**：着色器按圆角矩形 SDF 的梯度算出"这个像素朝向哪条边"，
     * 再和光方向点乘 —— 朝光的那半片玻璃亮、背光那半片暗，`pow(|dot|, falloff)`
     * 让亮部贴着边缘收拢。这样整块玻璃才有体积感；只画 1px 描边是画不出来的。
     */
    highlightAlpha: Float = 0f,
    /** 光的方向（度）。45 = 光从左上来，和 Apple 那套默认一致 */
    highlightAngle: Float = 45f,
    /** 高光衰减指数。1 = 线性，越大亮部越贴着边缘 */
    highlightFalloff: Float = 1f,
    /**
     * 高光带宽度 = `highlightSpread × refractionHeight`（[highlightWidth] 为 0 时才用）。
     *
     * ⚠️ 拿 refractionHeight 当基准有致命缺陷：浅色滑块**静止时折射是 0**（照上游
     * pressProgress 语义），`0 × 2.0` 被钳到 1px —— 于是高光退化成一条 1px 硬线，
     * 紧挨着就是纯色中腔，这就是"折射区域和周围一圈高光之间的过渡没做好"。
     */
    highlightSpread: Float = 0.9f,
    /**
     * 高光带宽度（dp，直接给值，优先级高于 [highlightSpread]）。
     *
     * 光带宽窄和折射带是**两件独立的事**：折射只看边缘那一圈被弯折的厚度，
     * 而光晕可以比它宽得多（真玻璃边缘的漫反射是十几 dp 的渐隐）。分成两个参数之后，
     * 折射关掉（静止）也照样有柔和的光带。
     */
    highlightWidth: Dp = 0.dp,
    /**
     * 高光（面级方向光 + 边缘环境光）的颜色，默认纯白。
     *
     * 暖色系界面里要给**暖白**：纯白高光在米色底上偏冷，边缘一圈和中心是两个色相，
     * 看着就像"贴了一圈冷白边"。换成同色相的亮色之后，中心到边缘是一整块连续的介质。
     */
    highlightColor: Color = Color.White,
    /**
     * 饱和度增强（colorFilter，1 = 不变，Kyant 的 vibrancy() 是 1.5）。
     * 与 [saturation] 不同：这个在模糊**之前**改色，影响的是"透过来的画面本身的浓度"。
     */
    vibrancy: Float = 1f,
    /**
     * 玻璃表面之外的"薄纱"（Snapzy 的 veil）。
     *
     * 体色 [tint] 是提亮的；但浅色主题下 App 底色本身接近白，白提亮＝看不见，
     * 玻璃就只能靠描边和投影勉强读出来。veil 是反向的极淡压暗，
     * 让"这一块是玻璃，不是背景"在纯白内容上也能成立。暗色主题一般给透明。
     */
    veil: Color = Color.Transparent,

    /** 描边/高光的画法，见 [GlassRim] */
    rim: GlassRim = GlassRim.LEGACY,
    /**
     * [GlassRim.SPECULAR] 描边的起始不透明度（< 0 = 用内置的深 0.22 / 浅 0.52）。
     *
     * 内置值是按**底栏**（宽 290dp、高 50dp）调的；同样一条 0.52 的白线套到 55dp 的
     * 圆钮上，周长一半的像素都贴着边缘，会堆成一圈"发光环"把球心淹掉
     * （2026-11-06 小圆首次改玻璃的实拍）。所以描边强度必须跟着尺寸走。
     */
    rimAlpha: Float = -1f,
    /** 描边内缩（条目用 2dp，和原 glassSurface(strokeInset=2.dp) 对齐；底栏保持 0） */
    strokeInset: Dp = 0.dp,
    /**
     * 叠在背景之上的**第二层采样源**（Kyant 的 `rememberCombinedBackdrop(backdrop, tabsBackdrop)`）。
     *
     * 用途只有一个：做「玻璃叠玻璃」。
     * 底栏先把自己的「玻璃 + 内容」录进一个 layer（[Modifier.recordBackdrop]），
     * 选中胶囊再把这一层当作采样源之一 —— 于是胶囊里看到的是**底栏那层玻璃**，
     * 而不是原始页面。这才是真玻璃压真玻璃；两者都采样页面的话，折射相位对不上，
     * 视觉上就是「两块不同的玻璃拼在一起」。
     *
     * 注意：给了 [over] 之后**不要再开模糊**（blurRadius = 0.dp）——
     * 上游的滑块只做 lens，模糊会把叠上来的内容糊成一团。
     */
    over: LiquidGlassBackdropState? = null,
    /**
     * 采样偏移（本节点左上角相对 [backdrop] 的位置，px）的**实时来源**。
     *
     * 不传就用 `onGloballyPositioned` 记录的位置 —— 对静态玻璃没问题，
     * 但**会移动的玻璃**（底栏滑块）在快速拖动时会滞后一帧：布局回调记下的是上一帧的位置，
     * 而绘制用的是这一帧 → 每帧错开十几像素，图标就在里面"颤"。
     * 慢速时位移小（1~2px）看不出来，一快就明显 —— 这正是用户报的"快速移动时抖动依旧"。
     *
     * 传一个 lambda 进来（读的是**同一帧**驱动位移的那份状态），偏移和位置就严格同帧。
     */
    samplingOffset: (() -> Offset)? = null,
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
    // 玻璃的光学效果（折射带、边缘高光、vibrancy 的 SDF）全部按「圆角矩形」推导，
    // 注释里也写明了「折射目前只支持圆角矩形」。传超椭圆进来时，玻璃体仍按圆角矩形算，
    // 而外层 Surface/clip 是超椭圆 —— 超椭圆比圆角矩形更鼓，四角就有一小块实色露在
    // 玻璃体（连同它的描边）之外。这里统一降级成同半径的圆角矩形，让两者严丝合缝。
    // 视觉上不吃亏：玻璃边缘本来就带模糊，24dp 上圆角与超椭圆只差约 5px，看不出来。
    val glassShape = shape.asRoundedForGlass()

    var myCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var mySize by remember { mutableStateOf(IntSize.Zero) }
    val offset = remember { mutableStateOf(Offset.Zero) }
    // over 层（底栏玻璃）相对本节点的偏移，单独算一份
    val overOffset = remember { mutableStateOf(Offset.Zero) }
    // over 层自己在背景层里的位置。底栏不移动 → 这个值稳定，滞后一帧也无影响；
    // 但它是「同帧采样偏移」换算 over 层坐标的基准（见下方 drawWithContent）。
    val overInBg = remember { mutableStateOf(Offset.Zero) }
    val cornerRadii = remember { mutableStateOf(FloatArray(4)) }

    val rhPx = with(density) { refractionHeight.toPx() }
    val raPx = with(density) { refractionAmount.toPx() }
    val blurPx = with(density) { blurRadius.toPx() }

    // ⚠️ RenderEffect 必须在**组合期**算好。
    // 之前是放到 draw 阶段里 `effect.value = ...`、graphicsLayer 再读 —— 那是无效的：
    // 在 graphicsLayer 的 lambda 里读快照不构成依赖，读到的永远是旧值(null)，
    // 结果就是「既不模糊也不折射，只剩一层透明」。
    // ⚠️ key 里**绝不能带 offset**：offset 每帧随滚动/拖动变化，而 shader 根本不读它
    // （它只在下方 drawWithContent 里用于 translate 背景层）。带上的后果是拖动时每帧
    // 重建 RuntimeShader + RenderEffect + 离屏缓冲 —— 画面里的图案就会"纯抖动"
    // （2026-11-06 用户：「不是那种液态玻璃折射的抖动，是纯抖动」）。
    val glassEffect = remember(
        mySize, cornerRadii.value[0], rhPx, raPx, blurPx,
        depthEffect, dispersion, rimLight, saturation,
        highlightAlpha, highlightAngle, highlightFalloff, highlightSpread, highlightWidth,
        highlightColor, vibrancy
    ) {
        // RuntimeShader 在个别 ROM/驱动上编译失败会抛异常（抛在组合期 = 整页崩）。
        // 这里兜底成 null：只是没有折射，玻璃体与描边照旧，绝不连累整个界面。
        runCatching {
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
            shader.setFloatUniform("dispersion", dispersion)
            shader.setFloatUniform("rimLight", rimLight)
            shader.setFloatUniform("saturation", saturation)
            val rad = highlightAngle * (Math.PI / 180.0).toFloat()
            shader.setFloatUniform("highlightNormal", kotlin.math.cos(rad), kotlin.math.sin(rad))
            shader.setFloatUniform("highlightFalloff", highlightFalloff.coerceAtLeast(0.01f))
            shader.setFloatUniform("highlightAlpha", highlightAlpha)
            shader.setFloatUniform("highlightSpread", highlightSpread)
            shader.setFloatUniform("highlightWidth", with(density) { highlightWidth.toPx() })
            shader.setFloatUniform(
                "highlightColor",
                highlightColor.red, highlightColor.green, highlightColor.blue
            )

            val refract = android.graphics.RenderEffect
                .createRuntimeShaderEffect(shader, "content")
            val blur = android.graphics.RenderEffect.createBlurEffect(
                blurPx.coerceAtLeast(0.1f), blurPx.coerceAtLeast(0.1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            // 效果链顺序照 Kyant 的硬性约定：colorFilter → blur → lens（内层先执行）。
            // vibrancy 就是她那个 setSaturation(1.5) 的 ColorMatrix，放在模糊之前，
            // 改的是"透过来的颜色浓度"，不是玻璃自己的色。
            val inner = if (vibrancy != 1f) {
                val cm = android.graphics.ColorMatrix().apply { setSaturation(vibrancy) }
                android.graphics.RenderEffect.createChainEffect(
                    blur,
                    android.graphics.RenderEffect.createColorFilterEffect(
                        android.graphics.ColorMatrixColorFilter(cm)
                    )
                )
            } else blur
            android.graphics.RenderEffect.createChainEffect(refract, inner).asComposeRenderEffect()
        } else null
        }.getOrNull()
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
            over?.coordinates?.let { overCoords ->
                overOffset.value = offsetOf(overCoords, coords)
                backdrop.coordinates?.let { bg -> overInBg.value = offsetOf(bg, overCoords) }
            }
        }
        // ── ① 最外层：描边与高光 ─────────────────────────────────────────────
        // 必须画在 drawContent() **之后**：这样它在玻璃层之上、且在 graphicsLayer 之外，
        // 既不会被 clip 裁掉，也不会被折射/模糊糊掉。
        // （之前把描边写在 graphicsLayer 里面 → 整条"镶边"被裁没了，就是"没有框"的原因。）
        .drawWithContent {
            drawContent()
            if (rim == GlassRim.NONE) return@drawWithContent
            val cr = cornerRadiusOf(shape, Size(size.width, size.height), density)
            val line = 1.dp.toPx()
            val si = with(density) { strokeInset.toPx() }
            val inner = Size(
                (size.width - si * 2f).coerceAtLeast(0f),
                (size.height - si * 2f).coerceAtLeast(0f)
            )
            val innerCr = CornerRadius(
                (cr.x - si).coerceAtLeast(0f), (cr.y - si).coerceAtLeast(0f)
            )

            if (rim == GlassRim.SPECULAR) {
                // ── Snapzy 式镜面：一条沿左上→右下渐变的全周高光，再加一条柔和的顶缘线 ──
                // 不用「中间 0.85 白」那种笔直的亮线（那是把描边画成了白杠），
                // 而是让亮度沿轮廓走，圆角处自然收细，像玻璃边缘的反光。
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            // 0.62 → 0.42：浅底上 0.62 的白在圆弧处堆成一圈"塑料白边"，
                            // 反而把通透感吃掉。真玻璃的轮廓反光是很细很淡的一条。
                            // 浅色 0.42 → 0.52：用户要"胶囊周围一圈白色细线"，
                            // 底栏白玻璃压在米色纸上，过渡再柔也需要一条明确的轮廓，
                            // 否则就"和背景融成一片"（实测观感）。
                            Color.White.copy(
                                alpha = if (rimAlpha >= 0f) rimAlpha
                                else if (dark) 0.22f else 0.52f
                            ),
                            Color.White.copy(alpha = (if (rimAlpha >= 0f) rimAlpha
                                else if (dark) 0.22f else 0.52f) * 0.27f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.55f, size.height)
                    ),
                    topLeft = Offset(si, si),
                    size = inner,
                    cornerRadius = innerCr,
                    style = Stroke(line)
                )
                // 顶缘：极窄的一条横向反光，两端淡出（替代原来 0.85 的白杠）
                if (topHighlight) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (dark) 0.18f else 0.78f),
                                Color.Transparent
                            ),
                            startX = si,
                            endX = si + inner.width
                        ),
                        topLeft = Offset(si, si + line),
                        size = Size(inner.width, line),
                        cornerRadius = CornerRadius(line / 2, line / 2)
                    )
                }
                // 下缘：一条更淡的反射，给玻璃一个"底边也有厚度"的读感
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (dark) 0.05f else 0.16f),
                            Color.Transparent
                        ),
                        startX = si,
                        endX = si + inner.width
                    ),
                    topLeft = Offset(si, si + inner.height - line * 2),
                    size = Size(inner.width, line),
                    cornerRadius = CornerRadius(line / 2, line / 2)
                )
            } else {
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
                    else Color(0xFF574266).copy(alpha = 0.16f),
                    topLeft = Offset(si, si),
                    size = inner,
                    cornerRadius = innerCr,
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
            val off = samplingOffset?.invoke() ?: offset.value
            // 把背景层里"玻璃身后那一块"画进本层的离屏缓冲。
            // RenderEffect 的采样坐标系就是这个缓冲 → 整体平移，
            // 让玻璃左上角正好对准背景层的对应位置。
            translate(-off.x, -off.y) { drawLayer(bgLayer) }
            // 第二层采样源（底栏自己的玻璃）：压在页面之上。
            // 沙盒坐标同样用平移对齐 —— over 层的 (0,0) 落在本节点的 overOffset 处。
            if (over != null) {
                // ⚠️ over 层同样不能吃 onGloballyPositioned 的位置 —— 那是上一帧的。
                // 底栏滑块里看到的图标**全部来自这一层**，所以它滞后一帧 = 图标在滑块里颤，
                // 而且位移越大错得越多（快速拖动明显、慢速看不出来）。
                // 给了 samplingOffset 就用「本帧滑块在 bg 里的位置 − 底栏在 bg 里的位置」，
                // 换算成本节点在 over 层坐标系里的位置，和内容严格同帧。
                val oo = if (samplingOffset != null) off - overInBg.value else overOffset.value
                translate(-oo.x, -oo.y) { drawLayer(over.layer) }
            }
            // 玻璃的"体色"。给了 tintBottom 就画成上亮下淡的竖向渐变
            // （Snapzy 的 substrate + veil 那套：上表面受光、下缘变厚），否则平涂。
            val body = cornerRadiusOf(shape, Size(size.width, size.height), density)
            if (tintBottom != null) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(tint, tintBottom),
                        startY = 0f,
                        endY = size.height
                    ),
                    cornerRadius = body
                )
            } else {
                drawRoundRect(color = tint, cornerRadius = body)
            }
            // 薄纱：反向压暗，让玻璃在纯白内容上也有"换了一层介质"的读感
            if (veil.alpha > 0f) {
                drawRoundRect(color = veil, cornerRadius = body)
            }
            // 底部一条极淡的暗部：玻璃下缘的厚度 / 与背景的接触阴影
            if (innerShadow > 0.dp) {
                val band = with(density) { innerShadow.toPx() }
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.16f else 0.035f)),
                        startY = (size.height - band).coerceAtLeast(0f),
                        endY = size.height
                    ),
                    cornerRadius = body
                )
            }
            drawContent()
        }
}

internal fun offsetOf(bg: LayoutCoordinates, my: LayoutCoordinates): Offset = try {
    bg.localPositionOf(my)
} catch (_: Exception) {
    // 外层有旋转/缩放时 localPositionOf 可能抛异常，退回根坐标近似
    my.positionInRoot() - bg.positionInRoot()
}

private fun cornerRadiusOf(shape: Shape, size: Size, density: Density): CornerRadius {
    val r = when (shape) {
        is RoundedCornerShape -> shape.topStart.toPx(size, density)
        // 连续曲率圆角：只有 nominally 的“角半径”，超椭圆的实际曲线更漫，玻璃折射按同一半径算已经够近
        is SquircleShape -> with(density) { shape.cornerRadius.toPx() }
        else -> 0f
    }
    return CornerRadius(r.coerceAtMost(size.minDimension / 2f))
}

private fun cornerRadiiOf(shape: Shape, size: Size, density: Density): FloatArray {
    val r = cornerRadiusOf(shape, size, density).x
    return floatArrayOf(r, r, r, r)
}


/** 把任意形状压成等效半径的圆角矩形 —— 玻璃着色器只认圆角矩形。 */
internal fun Shape.asRoundedForGlass(): RoundedCornerShape = when (this) {
    is SquircleShape -> RoundedCornerShape(cornerRadius)
    is RoundedCornerShape -> this
    else -> RoundedCornerShape(0.dp)
}
