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
import androidx.compose.ui.graphics.Color
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
    val s by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessHigh),
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
/** 弹窗形状：比卡片更圆，像一块浮起来的玻璃 */
val GlassDialogShape = RoundedCornerShape(30.dp)

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
@Composable
fun glassDialogModifier(): Modifier {
    val dark = isDarkTheme
    // 弹窗在的时候让主界面模糊（onDispose 时恢复）
    DisposableEffect(Unit) {
        GlassModalState.open()
        onDispose { GlassModalState.close() }
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
        .graphicsLayer {
            alpha = t
            val sc = 0.94f + 0.06f * t
            scaleX = sc; scaleY = sc
        }
        .shadow(
            26.dp, GlassDialogShape,
            ambientColor = Color.Black.copy(alpha = 0.18f),
            spotColor = Color.Black.copy(alpha = 0.34f)
        )
        .drawBehind {
            val r = CornerRadius(30.dp.toPx(), 30.dp.toPx())
            // ② 弹窗体：与条目同一套 —— 纯白 / 暖深 + 0.85 透明
            //    0.85 比 0.88 再"小小拉高"一点透明度（用户 2026-10-03）
            drawRoundRect(
                color = if (dark) Color(0xFF2E2A26).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f),
                cornerRadius = r
            )
            // ④ 顶部镜面线（中间最亮、两头淡出）
            val line = 1.3.dp.toPx()
            val inset = size.width * 0.10f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = if (dark) 0.55f else 1f), Color.Transparent)
                ),
                topLeft = Offset(inset, 0.7.dp.toPx()),
                size = Size(size.width - inset * 2, line),
                cornerRadius = CornerRadius(line / 2, line / 2)
            )
            // ④ 底部反光线（更弱，暗示玻璃下缘的折射）
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = if (dark) 0.20f else 0.42f), Color.Transparent)
                ),
                topLeft = Offset(inset * 1.6f, size.height - line - 0.7.dp.toPx()),
                size = Size(size.width - inset * 3.2f, line * 0.8f),
                cornerRadius = CornerRadius(line / 2, line / 2)
            )
        }
        // ⑤ 描边：上亮下淡
        .border(
            BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.40f else 0.95f),
                        Color.White.copy(alpha = if (dark) 0.10f else 0.30f)
                    )
                )
            ),
            GlassDialogShape
        )
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
    shape: RoundedCornerShape,
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
            val cr = shape.topStart.toPx(size, this)
            val corner = CornerRadius(cr, cr)
            val line = 1.dp.toPx()

            // ② 卡身：纯白（深色用暖深 #2E2A26）+ 0.85 透明度
            //    （2026-10-03 用户：透明度再小小拉高 —— 0.80 → 0.85）
            drawRoundRect(
                color = if (dark) Color(0xFF2E2A26).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f),
                cornerRadius = corner
            )

            // ②½ 柔和层：卡身越透，透进来的背景细节就越硬（一眼看出是"没做模糊"）。
            //    这里在卡身下面铺一层半径很小的圆形渐变（亮 0.55），把背景细节压柔一点点。
            //    只画亮色：深色下这层白渐变叠在深卡底上 = 正中间一团白色光晕（用户反馈），
            //    而深色背景本来就暗、透进来的细节几乎看不见，这层在深色下纯属多余。
            if (!dark) {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0f)
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        radius = size.minDimension * 0.72f
                    ),
                    cornerRadius = corner
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
                drawRoundRect(
                    color = if (dark) Color.White.copy(alpha = 0.14f) else Color(0xFF5A4B96).copy(alpha = 0.16f),
                    topLeft = Offset(si, si),
                    size = Size(size.width - si * 2f, size.height - si * 2f),
                    cornerRadius = CornerRadius((cr - si).coerceAtLeast(0f), (cr - si).coerceAtLeast(0f)),
                    style = Stroke(line)
                )
            } else {
                drawRoundRect(
                    color = if (dark) Color.White.copy(alpha = 0.14f) else Color(0xFF5A4B96).copy(alpha = 0.16f),
                    cornerRadius = corner,
                    style = Stroke(line)
                )
            }
            if (ring) {
                drawRoundRect(
                    color = Color.White.copy(alpha = if (dark) 0.10f else 0.45f),
                    cornerRadius = corner,
                    style = Stroke(line)
                )
            }
        }
}

/** 卡片的高光描边：左上亮、右下淡 */
fun glassBorder(width: Dp = 1.dp): BorderStroke =
    BorderStroke(width, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.15f))))

/** 底层柔光色斑：让上面的半透明卡片"有东西可透" */
/** 柔光色斑的实际绘制：屏幕背景和"背景副本"都必须走这一份，否则玻璃里的背景会错位 */
fun DrawScope.drawColorBlobs(dark: Boolean) {
    // 亮色色斑强度 0.90 → 0.55 → 0.42（2026-10-03 两次调淡：
    // 卡片透出来的就是背景颜色，"条目发灰发脏"的根子在背景强度，不在卡片本身；
    // 背景越淡，透进来的越是纯白。深色保持 0.42 不动 —— 深色下色斑是唯一彩色来源。）
    val a = if (dark) 0.42f else 0.42f
    drawRect(if (dark) Color(0xFF0E0F12) else Color(0xFFF4F2FB))
    fun blob(color: Color, alpha: Float, sizeDp: Float, xDp: Float, yDp: Float, right: Boolean, bottom: Boolean) {
        val r = (sizeDp / 2f).dp.toPx()
        val cx = if (right) size.width + xDp.dp.toPx() - r else xDp.dp.toPx() + r
        val cy = if (bottom) size.height + yDp.dp.toPx() - r else yDp.dp.toPx() + r
        val c = Offset(cx, cy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                center = c, radius = r
            ),
            radius = r, center = c
        )
    }
    blob(Color(0xFFFF8E7A), a, 360f, -110f, -90f, right = false, bottom = false)
    blob(Color(0xFF7FA9FF), a * 0.92f, 320f, 90f, 30f, right = true, bottom = false)
    blob(Color(0xFFFFC98A), a * 0.88f, 340f, 100f, -40f, right = true, bottom = true)
    blob(Color(0xFF8FE8C0), a * 0.82f, 320f, -90f, 60f, right = false, bottom = true)
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

/** 玻璃卡片：半透明底 + 高光描边 + 大圆角 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = glassBorder()
    ) { Column(content = content) }
}

// ===== 优先级 =====
const val PRIORITY_NONE = 0
const val PRIORITY_LOW = 1
const val PRIORITY_MID = 2
const val PRIORITY_HIGH = 3

fun priorityLabel(p: Int): String = when (p) {
    PRIORITY_HIGH -> "高"
    PRIORITY_MID -> "中"
    PRIORITY_LOW -> "低"
    else -> ""
}

/** 优先级色条颜色（传入当前是否深色，红在深色下提亮） */
fun priorityColor(p: Int, isDark: Boolean): Color = when (p) {
    PRIORITY_HIGH -> if (isDark) Color(0xFFFF6B61) else Color(0xFFFF3B30)
    PRIORITY_MID -> if (isDark) Color(0xFFFFB340) else Color(0xFFFF9F0A)
    PRIORITY_LOW -> if (isDark) Color(0xFF7EA6D8) else Color(0xFF8E8E93)
    else -> Color.Transparent
}

// ===== 撤销条 =====
class UndoController {
    var message by mutableStateOf<String?>(null)
        private set
    private var action: (() -> Unit)? = null

    fun show(msg: String, onUndo: () -> Unit) {
        message = msg
        action = onUndo
    }

    fun undo() {
        action?.invoke()
        message = null
        action = null
    }

    fun clear() {
        message = null
        action = null
    }
}

@Composable
fun rememberUndoController(): UndoController = remember { UndoController() }

/** 底部"已删除 · 撤销"提示，5 秒后自动消失 */
@Composable
fun UndoHost(ctrl: UndoController, modifier: Modifier = Modifier) {
    LaunchedEffect(ctrl.message) {
        if (ctrl.message != null) {
            delay(5000)
            ctrl.clear()
        }
    }
    AnimatedVisibility(
        visible = ctrl.message != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xE61C1C1E))
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(ctrl.message ?: "", color = Color.White, fontSize = 13.5.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "撤销", color = Color(0xFFFF8A80), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { ctrl.undo() }.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * 左滑露出操作按钮的行容器。
 * actions：按钮文字 + 底色（从右往左排）；onAction 回调下标。
 */
@Composable
fun SwipeRow(
    actions: List<Pair<String, Color>>,
    onAction: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val revealPx = with(LocalDensity.current) { (actions.size * 78).dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(18.dp)

    Box(modifier.clip(shape)) {
        // 后面的操作按钮：只在划开时才画出来（否则会从半透明的行底下透出红/灰色）
        Row(
            Modifier.matchParentSize().graphicsLayer {
                alpha = if (revealPx > 0f) (-offsetX.value / revealPx).coerceIn(0f, 1f) else 0f
            },
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEachIndexed { i, (label, color) ->
                Box(
                    Modifier.width(78.dp).fillMaxHeight().background(color)
                        .clickable { onAction(i) },
                    contentAlignment = Alignment.Center
                ) { Text(label, color = Color.White, fontSize = 14.sp) }
            }
        }
        // 前景内容：横向拖动
        Box(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f))
                        }
                    },
                    onDragStopped = {
                        scope.launch {
                            offsetX.animateTo(if (offsetX.value < -revealPx / 2) -revealPx else 0f, tween(200))
                        }
                    }
                )
        ) { content() }
    }
}
