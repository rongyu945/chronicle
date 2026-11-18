package com.rongyu.shixuji.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import com.rongyu.shixuji.theme.isDarkTheme
import com.rongyu.shixuji.ui.motion.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Canvas as GfxCanvas
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import com.rongyu.shixuji.ui.motion.Motion

/** 一个"什么都不画"的点击反馈：交给它之后，点任何东西都不会再泛灰 */
private object NoIndication : IndicationNodeFactory {
    private class NoIndicationNode : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() { drawContent() }
    }

    override fun create(interactionSource: InteractionSource): DelegatableNode = NoIndicationNode()
    override fun hashCode(): Int = "ShixuJiNoIndication".hashCode()
    override fun equals(other: Any?): Boolean = other === this
}

/**
 * 关掉点击时那层灰色的涟漪反馈（ripple），全 App 生效。
 *
 * 两条路都要堵：`Modifier.clickable` 走 LocalIndication；
 * Material3 的 Button/Card(onClick) 等内部直接调 ripple()，走 LocalRippleConfiguration。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoPressFeedback(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIndication provides NoIndication,
        LocalRippleConfiguration provides null
    ) { content() }
}

/**
 * 按下去会轻微缩一下的反馈。
 *
 * 本 App 为了去掉灰色涟漪把 LocalIndication 关掉了，于是"点下去毫无反应"——
 * 这个修饰符负责补回触感：按下缩到 0.94、松手弹回（弹簧），配合 clickable 的 interactionSource 用。
 *
 * 用法：`val is = remember { MutableInteractionSource() }` →
 * `.clickable(interactionSource = is, indication = null) { ... }.pressScale(is)`
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressed: Float = 0.94f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    // 2026-11-11「Motion P0」：减弱动态效果（系统动画缩放 = 0）时按压不再缩放 ——
    // 缩放本身就是「动效」，全 App 几十处 pressScale 在这里统一受控，不用逐个调用点改。
    val reduced = rememberReducedMotion()
    val s by animateFloatAsState(
        targetValue = if (isPressed && !reduced) pressed else 1f,
        // 7.4.0（方案 §22「全局普通按压」）：按下与回位分开 —— 按下用 90ms 的 tween
        // （"我碰到了"要即时，弹簧起步慢半拍），回位用 TouchSpring（极短、轻微回弹）。
        // 旧版统一 spring(0.45, High)：压下去时就带一次明显回弹，手感像按在橡皮上。
        animationSpec = if (reduced) tween(0)
        else if (isPressed) tween(90, easing = Motion.PaperExit)
        else Motion.TouchSpring,
        label = "pressScale"
    )
    return graphicsLayer {
        scaleX = s; scaleY = s
    }
}

/**
 * 液态玻璃质感：底层柔光色斑 + 半透明卡片 + 高光描边。
 *
 * 说明：安卓没有 iOS 那样的"背景虚化"系统能力，所以这里用
 * 「半透明 + 高光内描边 + 柔光背景 + 大圆角」逼近玻璃观感，
 * 不做折射/形变。文字与金额一律保持纯色实底，保证对比度。
 */

// ===== 弹窗统一的玻璃外观 =====

/**
 * 当前有几个玻璃弹窗开着。
 *
 * 安卓（不像 iOS）没有系统级的"背景模糊"给弹窗用，所以反过来做：
 * 弹窗打开时把背后的主界面整体模糊掉，半透明的玻璃弹窗盖上去——
 * 视觉上就是毛玻璃实时透出背后的内容，这是不用第三方渲染库能做的最接近液态玻璃的做法。
 * 低版本（< Android 12）Modifier.blur 自动不生效，退化成普通半透明，不影响使用。
 */
object GlassModalState {
    var count by mutableStateOf(0)
        private set

    fun open() { count++ }
    fun close() { count = (count - 1).coerceAtLeast(0) }
    val isOpen: Boolean get() = count > 0
}
/** 玻璃圆角，阴影 / 玻璃体 / 描边 / clip 全部用这一个值，避免内外轮廓错位 */
val DIALOG_CORNER = 40.dp
/** 阴影外扩区：36dp 模糊 + 15dp 偏移，留 44 给四周 */
val SHADOW_PAD = 56.dp
/** 弹窗形状：比卡片更圆，像一块浮起来的玻璃。
 *  2026-11-08：改用 `squircle()`（平滑/连续曲率），不再用 `cardShape`。
 *  这里是全 App 唯一「换了形状绘制也不会错位」的地方 —— bodyPath / strokePath 都有
 *  `as? SquircleShape` 分支走 toPath，内外轮廓自动一致；卡片那边走了会露实色，
 *  因为玻璃着色器的 SDF 只认 sdRoundedRect。
 *
 *  【半径】角占的边长 = 半径本身，所以「直线区域太长」只能靠加大半径治：
 *    30dp 占 300dp 宽弹窗的 10%，看起来还是一块直边的板；现在 40dp（13%），
 *    上下两条直边各短了 10dp，角显得「吃进」更多。
 *  【exponent】平滑程度：值越大越「方」。
 *    5.0 → 距顶边 12px 处只内缩 8px（正圆 37）→ 用户 2026-11-08：「圆框很方」；
 *    3.0 → 20px，用户：「还是有点不够圆润，直线区域太长」；
 *    2.5 → 40dp 半径下同一位置内缩 40px，贴着正圆的手感，但四个角仍是连续曲率。
 *  想退回纯圆角就 `cardShape(DIALOG_CORNER)`。
 */
const val DIALOG_CORNER_EXPONENT = 2.5f
val GlassDialogShape = squircle(DIALOG_CORNER, DIALOG_CORNER_EXPONENT)

/**
 * 弹窗底色：交给 [glassDialogModifier] 自己画，所以这里透明。
 * （M3 的 containerColor 只收纯色，画不了渐变/高光。）
 */
@Composable
fun glassDialogContainer(): Color = Color.Transparent

/**
 * 弹窗的玻璃体（AlertDialog 的 modifier 会作用在它内部的 Surface 上）。
 *
 * 从这个外圈往里，一层层叠出玻璃感：
 *  ① 抬起阴影：大范围、低透明度，玻璃块才像浮在界面上方
 *  ② 玻璃体：左上亮 → 右下沉的斜向渐变，制造厚度（不是一块死白）
 *  ③ 斜向光带：一道很淡的流光，液态感主要来自它
 *  ④ 顶部镜面线 + 底部反光线：真实玻璃上下边缘都会反光，上亮下弱
 *  ⑤ 1dp 描边：上亮下淡，勾出边界
 */
/**
 * 弹窗的玻璃体（AlertDialog 的 modifier 会作用在它内部的 Surface 上）。
 *
 * 2026-11-06 用户：「把所有弹窗都做成液态玻璃效果」。这一版的核心是**把玻璃做透**：
 *
 *  ⓪ 弹窗 window 的遮罩从系统默认的 0.32 压到 0.10 —— 遮罩太黑的话，后面那片已经模糊过的
 *     主界面根本透不上来，玻璃就成了一张白纸（液态玻璃的前提是「看得见背后的东西」）；
 *  ① 抬起阴影：大范围、低透明度，玻璃块才像浮在界面上方；
 *  ② 玻璃体：0.85 → 0.58（深色 0.66）。背后已经是 15dp 高斯模糊的色块、不是细节，
 *     所以降透明度不会伤可读性，但「透」的感觉全靠这一步；
 *  ③ 边厚：沿圆角内缘一圈内阴影（上浅、下深），玻璃才有厚度，不是一张贴纸；
 *  ④ 顶部镜面：偏心椭圆高光，模拟顶面反射天光；
 *  ⑤ 色散：内缘两道极淡的青 / 品红细线，模仿液态玻璃边缘的 RGB 分离（很淡，只添湿感）；
 *  ⑥ 上下反光线 + 渐隐描边：真实玻璃上下边缘都会反光，上亮下弱。
 */
@Composable
fun glassDialogModifier(
    // 7.6.4：宽度与阴影留白可调。金额弹窗带 20 键键盘，需要更宽的热区。
    // 注意 shadowPad 也就是「玻璃体外侧那块画阴影的画布」：画布太小，36dp 模糊的
    // 阴影会被裁出一条直边，所以只能在「宽度」与「阴影完整度」之间权衡。
    maxWidth: Dp = 276.dp,
    // 7.6.5：留白分横竖。横向留白直接吃掉弹窗宽度（弹窗不能比屏宽更宽），
    // 而纵向屏幕还有一大截空余 —— 金额弹窗要「更宽」，就把横向留白收到最小、
    // 纵向保持 56dp 给 36dp 模糊铺开。横竖都用同一个值时行为与 7.6.4 以前一致。
    shadowPadH: Dp = SHADOW_PAD,
    shadowPadV: Dp = SHADOW_PAD,
): Modifier {
    val dark = isDarkTheme
    // 弹窗在的时候让主界面模糊（onDispose 时恢复）
    DisposableEffect(Unit) {
        GlassModalState.open()
        onDispose { GlassModalState.close() }
    }
    // 遮罩变淡：默认 0.32 的黑会把背后的模糊内容压没，玻璃就透不出东西
    val view = LocalView.current
    LaunchedEffect(view) {
        runCatching {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                // 归零：主界面自己那层暖棕遮罩已经在做变暗，window 再压 0.10 就是两层叠加
                // （合成后 1-(1-0.10)(1-0.24)=0.316，比原来为了通透才降下来的 0.32 还暗）
                setDimAmount(0f)
                // 弹窗 window 的默认背景（theme 里的 dialog_background 九宫格）
                // 在 usePlatformDefaultWidth=false 之后会铺成一大块矩形，直接抹掉
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
        }
    }
    // 入场：0.94 → 1.00 轻微放大 + 淡入，玻璃块像"浮上来"而不是硬切出来
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val t by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "dlgIn"
    )
    return Modifier
        // 给阴影留出画布：drawBehind 的 Canvas 只有组件这么大，36dp 的高斯一旦超出边就被
        // 硬裁——实测弹窗四边外侧有 14~40 级阴影、四个圆角外侧几乎没有（Δ≈10，一角为 0），
        // 画布底边上还留着一道笔直的台阶。阴影要 36dp 模糊 + 15dp 偏移 ≈ 51dp，
        // 所以画布每边外扩 SHADOW_PAD(56dp)，玻璃体画在内缩的 bodyRect 上。
        // ⚠️ 顺序很要紧：widthIn/fillMaxWidth 必须写在 layout **里面**，
        //    写在最外层会先把约束收紧，layout 再减两个 pad，内容就被压窄了（实测窄到 152dp）。
        .graphicsLayer {
            // 只动 scale，alpha 恒 1。
            // alpha < 1 会让 Compose 为这层建离屏缓冲（saveLayer），缓冲是**矩形**的，
            // 和里面的圆角玻璃不是一个形状——低 alpha 时那块矩形会在模糊背景上显出一道
            // 直角淡边（实测弹窗出现的第一帧能看见一个圆角弹窗外面套着直角方块）。
            val sc = 0.94f + 0.06f * t
            scaleX = sc; scaleY = sc
        }
        .drawBehind {
            val padX = shadowPadH.toPx()
            val padY = shadowPadV.toPx()
            // 玻璃本体的真实矩形：四边各内缩 pad
            val body = Rect(padX, padY, size.width - padX, size.height - padY)
            val bodySize = Size(body.width, body.height)
            val radiusPx = DIALOG_CORNER.toPx()
            val r = CornerRadius(radiusPx, radiusPx)
            // 玻璃体的轮廓必须和 Surface(shape = GlassDialogShape) 完全一致。
            // 只改 Surface 的 shape、这里还画圆角矩形的话，超椭圆比圆角矩形鼓出去的那部分
            // 就是「实色露在描边外面」—— 用户看到四角有一小块实心超出勾线。
            val bodyPath = Path().apply {
                val sq = GlassDialogShape as? SquircleShape
                if (sq != null) {
                    addPath(sq.toPath(bodySize, this@drawBehind))
                    translate(Offset(body.left, body.top))
                } else {
                    addRoundRect(RoundRect(body, r))
                }
            }
            // ── ① 抬起阴影 ─────────────────────────────────────────────
            // 不用 Modifier.shadow：它在弹窗中部会浮出一块笔直边界的亮矩形。
            // Skia 的 setShadowLayer 一次绘制就是连续衰减，不会有台阶。
            // 必须挖掉弹窗形状本身：模糊是以形状为中心向内外一起扩散的，不挖的话
            // 暗色留在弹窗内部，玻璃 0.60 的 alpha 盖不住，整块发灰。
            // 挖的容器**就是完整画布**（含 pad 的外扩区），阴影才铺得开。
            // ── ⓪ 柔化光环（2026-11-08 新增）─────────────────────────────
            // iOS 26 弹窗底下那层「尺寸 +50px / 图层模糊 40」本质是**自身轮廓放大后
            // 的一份模糊拷贝**，是亮的；本项目过去只用三层暗投影表达浮起，浅色纸底上
            // 越加越像脏了一块（底栏那边已实测阴影区 RGB 208 对背景 251）。
            // 做法：轮廓外扩 HALO_INFLATE 后**只画它的 Skia 阴影**（填充色 TRANSPARENT
            // → 只留模糊环，不含实心），峰值落在真实边缘外侧约 9dp，向内那半被玻璃体
            // 的 0.60 alpha 压住 → 观感是纸被玻璃打亮了一圈，而不是玻璃贴了张纸。
            // 深色不画：深玻璃的「浮起」本来就该靠暗，加亮光环会变成发光边框。
            // ★ 7.6.5（用户：「弹窗下面又出现有色差的区域」）：
            //   淡出层。阴影/光环都是 Skia 模糊，模糊会一直铺到画布边缘然后被**硬裁**——
            //   裁口是一条横贯屏幕的直线，肉眼就是「弹窗下面一块有色差的区域」。
            //   解法：把这一整片投影画进一个离屏图层，画完后用两道 DstIn 渐变
            //   （横向一道、纵向一道）把最外圈淡到 alpha=0，于是画布边缘与外界无缝。
            drawIntoCanvas { c -> c.nativeCanvas.saveLayer(null, android.graphics.Paint()) }
            if (!dark) {
                val inflate = 9.dp.toPx()
                // 平滑圆角的外扩用「圆角矩形 + 半径同步加大」近似（模糊后看不出差别）
                val haloPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            Rect(body.left - inflate, body.top - inflate,
                                 body.right + inflate, body.bottom + inflate),
                            CornerRadius(radiusPx + inflate, radiusPx + inflate)
                        )
                    )
                }
                drawIntoCanvas { c ->
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.TRANSPARENT
                        setShadowLayer(26.dp.toPx(), 0f, 0f, android.graphics.Color.argb(140, 255, 252, 247))
                    }
                    c.nativeCanvas.drawPath(haloPath.asAndroidPath(), p)
                }
            }
            val outsidePath = Path().apply {
                op(
                    Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) },
                    bodyPath,
                    PathOperation.Difference
                )
            }
            clipPath(outsidePath) {
                drawIntoCanvas { c ->
                    val ink = if (dark) android.graphics.Color.argb(0, 0, 0, 0) else android.graphics.Color.rgb(58, 42, 24)
                    fun shadowLayer(blur: Float, dy: Float, a: Int) {
                        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.TRANSPARENT
                            setShadowLayer(blur, 0f, dy, (ink and 0x00FFFFFF) or (a shl 24))
                        }
                        c.nativeCanvas.drawPath(bodyPath.asAndroidPath(), p)
                    }
                    // 三层：接触（定义边缘）→ 中层（过渡）→ 环境（大范围）
                    // 2026-11-08：浅色档 alpha 减半（62/54/64 → 32/28/34）——
                    // 「浮起」改由 ⓪ 的亮光环主表达，暗投影只留一点贴地感，
                    // 否则两者叠加会把纸压成灰。深色档不动（深底上暗投影本身就是对的）。
                    shadowLayer(4.dp.toPx(), 1.5.dp.toPx(), if (dark) 130 else 32)
                    shadowLayer(14.dp.toPx(), 6.dp.toPx(), if (dark) 120 else 28)
                    shadowLayer(36.dp.toPx(), 15.dp.toPx(), if (dark) 140 else 34)
                }
            }
            // ★ 淡出：只在留白的**外侧 45%** 起作用，内侧（阴影主体）原样保留。
            //   横向留白很小时（金额弹窗 15dp）这道淡出把左右两侧的投影压薄，
            //   换来的是一整块屏幕宽度可用 —— 换来的是「没有硬边」。
            run {
                val fadeX = (padX * 0.55f).coerceAtLeast(1f)
                val fadeY = (padY * 0.55f).coerceAtLeast(1f)
                val keep = Color.Black
                val gone = Color.Black.copy(alpha = 0f)
                val sw = size.width
                val sh = size.height
                // 横向
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to gone, (fadeX / sw) to keep,
                        ((sw - fadeX) / sw) to keep, 1f to gone
                    ),
                    blendMode = BlendMode.DstIn
                )
                // 纵向
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to gone, (fadeY / sh) to keep,
                        ((sh - fadeY) / sh) to keep, 1f to gone
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            drawIntoCanvas { c -> c.nativeCanvas.restore() }
            // ② 弹窗体
            drawPath(
                path = bodyPath,
                color = if (dark) Color(0xFF2B2622).copy(alpha = 0.72f) else Color(0xFFFFFCF7).copy(alpha = 0.68f),
            )
            clipPath(bodyPath) {
                // ③ 边厚：顶部一点压暗 + 底部更沉
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = if (dark) 0.16f else 0.045f),
                        0.13f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (dark) 0.26f else 0.085f)
                    ),
                    topLeft = body.topLeft,
                    size = bodySize
                )
                // ④ 顶部镜面反射（中心在玻璃上方，只见一片柔和的亮）
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color.White.copy(alpha = if (dark) 0.22f else 0.34f),
                            0.55f to Color.White.copy(alpha = if (dark) 0.06f else 0.14f),
                            1f to Color.Transparent
                        ),
                        center = Offset(body.center.x, body.top - body.height * 0.10f),
                        radius = body.width * 0.85f
                    ),
                    topLeft = body.topLeft,
                    size = bodySize
                )
            }
            // ⑤ 色散（初版画过两层错开的青 / 品红细线）—— 已删除。
            //    浅色弹窗上是白玻璃，「色散」没有任何可折射的东西衬托，只剩一圈脏边：
            //    品红那层偏移是 (−0.6dp, −1.2dp)，叠加后左边的青跑在上面、右边的粉贴在下面，
            //    于是右侧和下侧各多出一条淡淡红线（2026-11-06 用户指出）。深色下同样看不见。
            //
            // ⑥ 顶部镜面线 / 底部反光线 —— 也已删除。
            //    它们和 ⑦ 的描边在顶部、底部是同一位置叠加：镜面线 1.3dp(纯白) + 描边 1dp(白 0.95)
            //    实测顶部变成 6px 纯白、底部 4px，而左右只有 2px —— 就是用户说的
            //    「顶部有很粗的白线」。顶部那片柔和高光由 ④ 的径向镜面反射负责，不需要再压一条线。
            //
            // ⑦ 外轮廓描边（上亮下淡，四边等宽）
            // 过去用 Modifier.border 画：那时 modifier 的尺寸已经被 layout 外扩成
            // 「玻璃体 + SHADOW_PAD×2」，border 就画在那个 2×56dp 的大框上 ——
            // 描边离玻璃体 56dp，圆角却还是 30dp，弧线对不上，于是四角的间距比四边宽
            // （用户 2026-11-06：「弹窗的外框细线都和外框不等距」）。
            // 描边必须裹着 body 画，宽度额外内缩半个线宽，才是真正的内描边。
            val bw = 1.dp.toPx()
            // 描边也走同一个轮廓（内缩半个线宽），否则四角的描边和实心对不上
            val strokePath = Path().apply {
                val sq = GlassDialogShape as? SquircleShape
                if (sq != null) {
                    addPath(sq.toPath(Size(bodySize.width - bw, bodySize.height - bw), this@drawBehind))
                    translate(Offset(body.left + bw / 2f, body.top + bw / 2f))
                } else {
                    addRoundRect(
                        RoundRect(
                            Rect(body.left + bw / 2f, body.top + bw / 2f,
                                 body.right - bw / 2f, body.bottom - bw / 2f),
                            CornerRadius(radiusPx - bw / 2f, radiusPx - bw / 2f)
                        )
                    )
                }
            }
            drawPath(
                path = strokePath,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.40f else 0.95f),
                        Color.White.copy(alpha = if (dark) 0.10f else 0.30f)
                    )
                ),
                style = Stroke(bw)
            )
        }
        // 用 layout 把画布撑开、内容原尺寸居中放置。
        // 不能用 padding：那会把玻璃体本身压小（实测 44dp 时弹窗窄了 60px）。
        // 必须放在 drawBehind 的内层，drawBehind 才能拿到扩张后的尺寸。
        .layout { measurable, constraints ->
            val padX = shadowPadH.roundToPx()
            val padY = shadowPadV.roundToPx()
            val inner = constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = (constraints.maxWidth - padX * 2).coerceAtLeast(0),
                maxHeight = (constraints.maxHeight - padY * 2).coerceAtLeast(0)
            )
            val placeable = measurable.measure(inner)
            layout(placeable.width + padX * 2, placeable.height + padY * 2) {
                placeable.place(padX, padY)
            }
        }
        .widthIn(max = maxWidth)
        .fillMaxWidth()
        // 描边不在这里画：Modifier.border 作用在 layout 外扩后的尺寸上，
        // 会离玻璃体 SHADOW_PAD 那么远（见 drawBehind 里 ⑦ 的说明）
}


/**
 * 立体玻璃面 —— 用户 2026-10-02 选定的③方案（模糊 0px、通透度 0.25）
 *
 * 就四层，全部是画出来的，没有着色器、没有 API 版本限制：
 *   ① 玻璃体：左上亮 → 右下暗的斜向渐变（0.42 → 0.14），有厚度、不是一块死白
 *   ② 顶部一条高光（中间最亮两头淡出）：玻璃上缘镜面反射
 *   ③ 底部一条反光（更弱）：玻璃下缘的反射
 *   ④ 左右内侧亮边（左亮右弱）：暗示光从左上打来
 * ring = true 时再加一圈 1px 内亮环（底栏那种"镶边"感）
 * 阴影在裁剪之前画，所以浮起来的那层还在。
 */
fun Modifier.glassSurface(
    shape: Shape,
    dark: Boolean,
    ring: Boolean = false,
    elevation: Dp = 5.dp,
    clipContent: Boolean = true,
    strokeInset: Dp = 0.dp
): Modifier {
    return this
        .shadow(
            elevation, shape,
            ambientColor = if (dark) Color(0x66000000) else Color(0x14251F55),
            spotColor = if (dark) Color(0x73000000) else Color(0x1F251F55)
        )
        .then(if (clipContent) Modifier.clip(shape) else Modifier)
        .drawBehind {
            val cr = shape.cornerPx(size, this)
            val corner = CornerRadius(cr, cr)
            val line = 1.dp.toPx()

            // ② 卡身：纯白（深色用暖深 #2E2A26）+ 0.85 透明度
            //    （2026-10-03 用户：透明度再小小拉高 —— 0.80 → 0.85）
            // 深色卡底：2E2A26 → 3B352E（用户 2026-11-05：深色下卡片和背景几乎同色、糊成一片）。
            // 只提亮不换色相，仍是那套暖深玻璃；透明度 0.85 → 0.88 让提亮的部分不被背景吃掉。
            drawCardShape(
                shape, size, corner,
                color = if (dark) Color(0xFF3B352E).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.85f)
            )

            // ②½ 柔和层：卡身越透，透进来的背景细节就越硬（一眼看出是"没做模糊"）。
            //    这里在卡身下面铺一层半径很小的圆形渐变（亮 0.55），把背景细节压柔一点点。
            //    只画亮色：深色下这层白渐变叠在深卡底上 = 正中间一团白色光晕（用户反馈），
            //    而深色背景本来就暗、透进来的细节几乎看不见，这层在深色下纯属多余。
            if (!dark) {
                drawCardShape(
                    shape, size, corner,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0f)
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        radius = size.minDimension * 0.72f
                    )
                )
            }

            // ③ 描边护眼（用户 2026-10-03 从对照页选定 ④）：
            //    一条顶光（亮 0.85 / 深 0.20）+ 整圈 1px 淡描边
            //    （亮色浅紫 #5A4B96@0.16 / 深色白@0.14）—— 深浅模式都做（用户点名"深色也不要拉下"）。
            //    底光 / 左右光去掉：有了整圈描边它们反而多余。
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = if (dark) 0.20f else 0.85f), Color.Transparent)
                ),
                topLeft = Offset(size.width * 0.06f, 0f),
                size = Size(size.width * 0.88f, line),
                cornerRadius = CornerRadius(line / 2, line / 2)
            )
            if (strokeInset.value > 0f) {
                // 内缩描边：薄卡片（账目行）描边贴边会显得框格太满，往里收一点（对照页 C 方案）
                val si = strokeInset.toPx()
                drawCardShape(
                    shape, size, corner, inset = si,
                    color = if (dark) Color.White.copy(alpha = 0.10f) else Color(0xFF574266).copy(alpha = 0.24f),
                    style = Stroke(line)
                )
            } else {
                drawCardShape(
                    shape, size, corner,
                    color = if (dark) Color.White.copy(alpha = 0.10f) else Color(0xFF574266).copy(alpha = 0.24f),
                    style = Stroke(line)
                )
            }
            if (ring) {
                drawCardShape(
                    shape, size, corner,
                    color = Color.White.copy(alpha = if (dark) 0.10f else 0.45f),
                    style = Stroke(line)
                )
            }
        }
}

/**
 * 纸面底色（卡身那一层）。浅色 = 纸白，深色 = 暖深。
 *
 * 单独抽出来是因为「撤销删除」那一行要在**自己的纸背**上淡入：纸背必须与卡身同色，
 * 那一行的 alpha 再低也只是「同色叠同色」，不会混进页面底色（混进去就是用户说的「抠图脏」）。
 */
internal fun paperBaseColor(dark: Boolean): Color =
    if (dark) Color(0xFF2C2823) else Color(0xFFFFFDFA)

/**
 * 实心纸面 —— 内容卡片专用（2026-11-05 材质分层）。
 *
 * 为什么突然不用玻璃了：卡片底下是纸底色 + 很淡的色斑，半透明卡身透上来的就是那层
 * 极淡的白，结果「一层浅透明叠一层浅透明」把字压得发灰、卡片也没有边界感。
 * Apple 那套材质分层讲的是同一件事：**玻璃只给浮在内容之上的 chrome**
 * （底栏 / 悬浮钮 / 弹层 / 撤销条），承载正文的东西必须是实心的、能一眼立住的。
 *
 * 所以这里画的是「一张纸」，不是「一块玻璃」：
 *   ① 实色卡身（不透明，字直接坐在实色上，对比度可预测）
 *   ② 顶部一条极淡的受光渐变（只暗示纸有厚度，不做通透）
 *   ③ 一圈暖色细描边（浅色用缟色 16%，深色用白 10%）
 *   ④ 投影：比玻璃卡轻一点，因为它不该压过底栏那层玻璃
 */
fun Modifier.paperSurface(
    shape: Shape,
    dark: Boolean,
    elevation: Dp = 4.dp,
    strokeInset: Dp = 2.dp,
    clipContent: Boolean = true
): Modifier {
    return this
        .shadow(
            elevation, shape,
            // 纸面比玻璃更需要投影：它不透光，只能靠影子说明"这张纸浮在背景上"。
            ambientColor = if (dark) Color(0x7A000000) else Color(0x1A3A2A18),
            spotColor = if (dark) Color(0x8C000000) else Color(0x293A2A18)
        )
        .then(if (clipContent) Modifier.clip(shape) else Modifier)
        .drawBehind {
            val cr = shape.cornerPx(size, this)
            val corner = CornerRadius(cr, cr)
            val line = 1.dp.toPx()

            // ① 卡身：不透明。浅色用比底色亮一档的纸白，深色用比背景亮一档的暖深。
            //    色值统一走 [paperBaseColor]：撤销删除的「纸背」要跟它一模一样，
            //    否则那一行在自己的纸上淡入时会露出两种白。
            drawCardShape(shape, size, corner, color = paperBaseColor(dark))
            // ② 顶部受光：上缘 14% 高处到透明的白，浅色几乎看不出、深色给出厚度
            drawCardShape(
                shape, size, corner,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (dark) 0.055f else 0.55f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = size.height * 0.34f
                )
            )
            // ③ 细描边（可内缩，薄行往里收一点不至于太满）
            val si = if (strokeInset.value > 0f) strokeInset.toPx() else 0f
            drawCardShape(
                shape, size, corner, inset = si,
                color = if (dark) Color.White.copy(alpha = 0.09f) else Color(0xFF8B7A63).copy(alpha = 0.20f),
                style = Stroke(line)
            )
        }
}

/** 卡片的高光描边：左上亮、右下淡 */
fun glassBorder(width: Dp = 1.dp): BorderStroke =
    BorderStroke(width, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.15f))))

/** 底层柔光色斑：让上面的半透明卡片"有东西可透" */
/** 柔光色斑的实际绘制：屏幕背景和"背景副本"都必须走这一份，否则玻璃里的背景会错位 */
fun DrawScope.drawColorBlobs(dark: Boolean, canvasSize: Size = size) {
    // 亮色色斑强度 0.90 → 0.55 → 0.42（2026-10-03 两次调淡：
    // 卡片透出来的就是背景颜色，"条目发灰发脏"的根子在背景强度，不在卡片本身；
    // 背景越淡，透进来的越是纯白。深色保持 0.42 不动 —— 深色下色斑是唯一彩色来源。）
    // 2026-11-05 材质分层：卡片改成实心纸白之后，底色再这么艳就会「背景比卡片还抢眼」，
    // 纸面立不住。浅色色斑 0.42 → 0.30。
    // 2026-11-06 反转：设置页那批卡片改成**半透明玻璃**以后，这条理由整个反过来了 ——
    // 玻璃要有东西可透。背景退成 0.30 的一层水色时，压在它上面的玻璃卡里
    // 什么都透不出来，看着就是一张普通白卡（用户：「浅色模式效果不明显」）。
    // 浅色回 0.42，卡片里重新能看见粉红/淡青/姜黄在慢慢流动，玻璃才成立。
    // 2026-11-10 深浅巡检 D1：深色下 0.42 的暖橘/姜黄压在近黑底上不是「光晕」而是「脏痕」
    // （实测深色空白区色偏 14 级、左侧背景亮度 27 vs 上方 41，眼睛读成一块土黄污渍）。
    // 深色单独降到 0.28：仍能透出色，但不再成块。
    val a = if (dark) 0.28f else 0.42f
    // 底色必须显式按 canvasSize 画：吸顶带是拿这个函数「重放」整屏背景的（平移后只覆盖一小块），
    // 用 DrawScope 自己的 size 只会画出带子那么大一块，露出下面滚过去的卡片。
    drawRect(
        color = if (dark) Color(0xFF121114) else Color(0xFFF7F1E6),
        topLeft = Offset.Zero, size = canvasSize
    )   // 浅色底=宣纸，深色底=墨底
    fun blob(color: Color, alpha: Float, sizeDp: Float, xDp: Float, yDp: Float, right: Boolean, bottom: Boolean) {
        val r = (sizeDp / 2f).dp.toPx()
        val cx = if (right) canvasSize.width + xDp.dp.toPx() - r else xDp.dp.toPx() + r
        val cy = if (bottom) canvasSize.height + yDp.dp.toPx() - r else yDp.dp.toPx() + r
        val c = Offset(cx, cy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                center = c, radius = r
            ),
            radius = r, center = c
        )
    }
    // 2026-11 换成传统淡色（粉红/淡青/杏黄/青白）：原来的 iOS 珊瑚红+靛蓝+橙+薄荷
    // 在宣纸底上会显出「电子味」，换传统色后整体是淡淡的一层水色。
    blob(Color(0xFFFFB3A7), a, 360f, -110f, -90f, right = false, bottom = false)   // 粉红
    blob(Color(0xFFC9DDF5), a * 0.92f, 320f, 90f, 30f, right = true, bottom = false) // 淡青
    blob(Color(0xFFFFC773), a * 0.88f, 340f, 100f, -40f, right = true, bottom = true) // 姜黄
    blob(Color(0xFFC0EBD7), a * 0.82f, 320f, -90f, 60f, right = false, bottom = true) // 青白
}

@Composable
fun ColorBlobsBackground(isDark: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawColorBlobs(isDark) }
}


@Composable
private fun Blob(color: Color, alpha: Float, mod: Modifier, size: Dp) {
    Box(mod.size(size).background(
        Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f))),
        CircleShape
    ))
}

// 【2026-11-11 删除】这里原来是 `GlassCard`（Card + 半透明白底 + 描边）。
// 全项目从未被调用过，而名字与 ui/GlassCard.kt 里真正的液态玻璃外壳重名，
// 调用点会报「Overload resolution ambiguity」。真玻璃见 GlassCard.kt。
