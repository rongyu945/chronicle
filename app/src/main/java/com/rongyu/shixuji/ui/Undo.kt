package com.rongyu.shixuji.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.theme.isDarkTheme
import kotlinx.coroutines.delay
import com.rongyu.shixuji.ui.motion.LocalReducedMotion
import com.rongyu.shixuji.ui.motion.Motion
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/**
 * 底部提示条控制器。
 *
 * 两种形态：
 *  - [show]     可撤回（删除 / 修改 / 新增…），右侧固定给「撤回」
 *  - [showInfo] 纯提示（记了一笔、无期限新建…），不显示按钮
 *
 * 5 秒后自动消失，期间再触发一次会重置计时。
 */
/** 撤回窗口时长的默认值（毫秒）。真实值由设置页「撤回停留时间」决定，跟着 [UndoHost] 的 lifeMs 走。 */
const val UNDO_LIFE_DEFAULT_MS = 5000

/** 设置页滑块的可选范围（秒）。外面缩到 2 秒一闪就没意义，放到 20 秒反而挡视线。 */
const val UNDO_MIN_SEC = 2
const val UNDO_MAX_SEC = 16
/** 「撤」按钮的专属色：墨蓝灰玻璃。和 ＋ 的主色区分开，也和提示条的老色号同源。 */
private val UndoInk = Color(0xFF4A5D68)

class UndoController {
    var message by mutableStateOf<String?>(null)
        private set

    /** null = 纯提示（不画撤回）；非空 = 画「撤回」并执行它 */
    var action by mutableStateOf<(() -> Unit)?>(null)
        private set

    /**
     * 每次弹出都自增的计数。
     *
     * 计时不能只用 [message] 当 key：连着两次弹出**同一句话**（比如删掉两条同名的待办），
     * 文案没变、LaunchedEffect 就不会重启，第二条还接着第一条剩下的零点几秒，
     * 提示条会毫无预兆地突然消失（用户看到的「有时一下就没了」）。用计数当 key 每次都重新计时。
     */
    var token by mutableIntStateOf(0)
        private set

    fun show(msg: String, onUndo: () -> Unit) {
        message = msg
        action = onUndo
        token++
    }

    /** 纯提示：没有可撤回的动作，右侧不显示按钮 */
    fun showInfo(msg: String) {
        message = msg
        action = null
        token++
    }

    fun undo() {
        val a = action
        message = null
        action = null
        token++
        a?.invoke()
    }

    fun clear() {
        message = null
        action = null
        token++
    }
}

@Composable
fun rememberUndoController(): UndoController = remember { UndoController() }

/**
 * 底部提示条（5 秒后自动消失）。
 *
 * 2026-10-25 用户要求：「撤回的条目的左右长度收窄，黑色的透明度增加，不要黑色要灰色」——
 * 原来是一条 90% 黑、左右只留 16dp 边距、几乎占满屏宽的硬黑条，压在浅色界面上很像系统报错。
 * 现在：
 *  - **宽度跟着内容走**（最大 300dp，居中），短文案就是一小颗，不再横贯全屏；
 *  - 底色改成中性灰 #515157（不再是纯黑），透明度 0.85 —— 比原来的 0.90 更透一点，
 *    同时保证白字在这层灰上仍有 ~5:1 对比度（再透就看不清了）。
 */
/**
 * 撤回提示。2026-11-05 用户选定「悬浮球」形态：
 * 可撤回时不再是一条横条，而是和 ＋ / 今 **同一颗球、同一尺寸（55dp）** 的玻璃圆钮，
 * 摆在 ＋ 左边 10dp、同一水平线上 —— 删完顺手就能点，不再有一条东西悬在半空。
 * 颜色和 ＋ 同一档（primary 0.30，＋ 也是 0.32），因为它是"临时出现"的，不该抢 ＋ 的位。
 *
 * 纯提示（[UndoController.showInfo]，没有可撤回的动作）仍是那颗小墨胶囊：
 * 圆形按钮在语义上就是"能按"，给一句"已记一笔"配一颗按不动的球反而是错的。
 */
@Composable
fun UndoHost(
    ctrl: UndoController,
    modifier: Modifier = Modifier,
    backdrop: LiquidGlassBackdropState? = null,
    /** 停留时长（毫秒），来自设置页「撤回停留时间」。倒计时环与自动消失共用这一个值。 */
    lifeMs: Int = UNDO_LIFE_DEFAULT_MS,
) {
    // 用 token 当 key（不是 message）：同一句话连续弹两次也要重新计时。
    // lifeMs 也进 key：在设置里把时长调短后立刻删一条，要按新时长计时而不是老的那份。
    LaunchedEffect(ctrl.token, lifeMs) {
        if (ctrl.message != null) {
            delay(lifeMs.toLong())
            ctrl.clear()
        }
    }
    // 退出动画期间内容还被保留着，但 ctrl.action / ctrl.message 已经被清空了 ——
    // 分支如果直接读它们，就会在消失的那 150ms 里**换成另一种形态**：
    // 玻璃球瞬间变成纯提示的深灰胶囊、还在原地缩一下（用户 2026-11-05：
    // 「撤回的圆圈转完之后会在偏下的页面里弹一下」，指的就是这团深灰方块）。
    // 所以这里记一个快照：只要这一轮弹过「可撤回」，直到整块彻底消失前都按球画。
    var keepUndo by remember { mutableStateOf(false) }
    if (ctrl.message != null) keepUndo = ctrl.action != null

    // 7.4.1（方案 P0-5）：撤回球是「删错了」那一刻唯一的挽回入口 —— 减弱动效时直接出现，
    // 不能让它在 240ms 里一边长一边淡（那时候用户只想赶紧点到它）。
    val reducedMotion = LocalReducedMotion.current
    AnimatedVisibility(
        visible = ctrl.message != null,
        // 7.4.0（方案 P1）：原来是从 0.72 倍"弹"出来（小七成），几乎像缩放到位；
        // 现在 0.96 → 1 只做微弱生长 + 200/260ms，个头基本不动，靠淡入交代出现。
        // 退场对称到 0.96，不再"缩成一小颗再消失"。
        enter = if (reducedMotion) EnterTransition.None
        else fadeIn(Motion.enter(240)) +
            scaleIn(Motion.enter(240), initialScale = 0.96f) +
            slideInVertically(Motion.enter(240)) { 22 },   // ≈8dp @2.6x
        exit = if (reducedMotion) ExitTransition.None
        else fadeOut(Motion.exit(170)) +
            scaleOut(Motion.exit(180), targetScale = 0.96f) +
            slideOutVertically(Motion.exit(180)) { 13 },
        modifier = modifier
    ) {
        if (keepUndo) {
            // 倒计时进度：和 5s 自动消失同一条时间轴（线性，不缓动）。
            // 用 Animatable 而不是 animateFloatAsState：token 变了要能 snapTo(1f) 重来，
            // 否则连删两条时第二颗球会从上一颗剩下的角度开始走。
            // **不能**写成 remember(ctrl.token) { Animatable(1f) }：退出时 token 又变一次，
            // 会重建成满环 → 消失那几帧里圆环「从空一下子涨满」，同样是那下弹跳的一部分。
            val life = remember { Animatable(1f) }
            LaunchedEffect(ctrl.token, lifeMs) {
                if (ctrl.message != null && ctrl.action != null) {
                    life.snapTo(1f)
                    life.animateTo(0f, tween(lifeMs, easing = LinearEasing))
                }
            }
            // DrawScope 里不能调 @Composable，环的颜色先在组合作用域里取好
            val ringColor = (if (isDarkTheme) Color(0xFFF05654) else MaterialTheme.colorScheme.primary)
                .copy(alpha = 0.85f)
            val ringTrack = if (isDarkTheme) Color.White.copy(alpha = 0.14f)
            else Color(0xFF4A5D68).copy(alpha = 0.14f)
            Box(contentAlignment = Alignment.Center) {
                GlassCircleButton(
                    label = "撤",
                    // 2026-11-06 实心版：白球 + 墨色字，和 ＋ 的红球、今 的白球同一套语言。
                    // 仍是墨蓝灰（用户 2026-11-05：「删除的颜色不要跟加号一样」），
                    // 但不填充成深色块 —— 那样它在浅色页面上会抢 ＋ 的视线。
                    tint = if (isDarkTheme) Color(0xFF3A3430) else Color.White,
                    fallbackColor = if (isDarkTheme) Color(0xFF3A3430) else Color.White,
                    labelColor = if (isDarkTheme) Color(0xFFF0E8DC) else UndoInk,
                    // 44 → 48dp（2026-11-08 审计 D 项）：它是删错之后唯一的挽回入口，
                    // 直径低于 48dp 的触摸目标本身就偏小。球右缘的位置不变（仍是 ＋ 左边 3dp），
                    // 变大的 4dp 全往左长，所以不用动间距。
                    size = 48.dp,
                    labelSize = 17.sp,
                    labelBold = true,
                    border = if (isDarkTheme) Color(0x33FFFFFF) else Color(0xFFEAE0D2),
                    contentDescription = ctrl.message ?: "撤回",
                    onClick = { ctrl.undo() },
                    backdrop = backdrop
                )
                // 倒计时圆环：贴着球的内缘走（半径 = 半宽 − 2.5dp），从 12 点顺时针耗尽。
                // 画在玻璃层外面，所以不会被折射/模糊糊掉。
                Canvas(Modifier.matchParentSize()) {
                    // 环必须压在玻璃边缘那条白线上（而不是往里缩 3dp）：
                    // 缩进去就成"双环"——外面一圈玻璃描边、里面一圈更亮的进度环，
                    // 2026-11-06 实测像两块不同的东西套在一起。
                    val r = this.size.minDimension / 2f - 1.2.dp.toPx()
                    val sw = 1.6.dp.toPx()
                    val tl = Offset((this.size.width - 2 * r) / 2f, (this.size.height - 2 * r) / 2f)
                    val box = Size(2 * r, 2 * r)
                    // 底环用极淡的墨色（白球上用白环等于没画）
                    drawArc(
                        color = ringTrack,
                        startAngle = -90f, sweepAngle = 360f,
                        useCenter = false, topLeft = tl, size = box, style = Stroke(sw)
                    )
                    if (life.value > 0.001f) {
                        drawArc(
                            // 进度走主题妃色：白球 + 红环，一眼看出还剩多久
                            color = ringColor,
                            startAngle = -90f, sweepAngle = 360f * life.value,
                            useCenter = false, topLeft = tl, size = box,
                            style = Stroke(sw, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                // 2026-11-05：纯提示条从"实色墨蓝块"改成深色液态玻璃。
                // 它和「撤」那颗玻璃球是同一条提示里的两个形态，原来一个玻璃一个实色，
                // 同屏出现时像两个不同系统的控件。深色 tint 在浅底上就是烟灰玻璃，
                // 白字压在上面依然够亮（对比度由 tint 0.55 + 黑纱 0.30 保底）。
                val toastShape = RoundedCornerShape(11.dp)
                Row(
                    Modifier.widthIn(max = 300.dp)
                        .heightIn(min = 34.dp)
                        .clip(toastShape)
                        .then(
                            if (backdrop != null && supportsLiquidGlass) Modifier.liquidCapsule(
                                backdrop = backdrop,
                                shape = toastShape,
                                height = 38.dp,
                                dark = isDarkTheme,
                                // 体色沿用它的深墨玻璃：这条和「撤」那颗球是同一条提示的两个形态
                                tint = UndoInk.copy(alpha = 0.62f),
                                tintBottom = UndoInk.copy(alpha = 0.72f),
                                rim = GlassRim.SPECULAR,
                                rimAlpha = -1f,
                                highlightAlpha = if (isDarkTheme) 0.14f else 0.18f,
                                highlightWidth = 11.dp,
                                highlightColor = if (isDarkTheme) Color(0xFFFFF8F0) else Color(0xFFFFFBF2),
                                strokeInset = 1.dp,
                            ) else Modifier.background(UndoInk.copy(alpha = 0.85f), toastShape)
                        )
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        ctrl.message ?: "", color = Color.White, fontSize = 12.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 210.dp)
                    )
                    androidx.compose.foundation.layout.Spacer(
                        Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}
