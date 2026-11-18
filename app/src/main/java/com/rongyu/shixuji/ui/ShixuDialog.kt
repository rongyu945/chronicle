package com.rongyu.shixuji.ui

import com.rongyu.shixuji.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect

import android.view.WindowManager
import com.rongyu.shixuji.ui.motion.LocalReducedMotion
import com.rongyu.shixuji.ui.motion.MotionDebug
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rongyu.shixuji.theme.AccentDeep

/**
 * 紧凑玻璃弹窗（替代 M3 AlertDialog 做「新增/修改」这类表单弹窗）。
 *
 * 用户 2026-10-16：「添加的弹窗各项目之间的间距都有点太大了，希望更紧凑一点、更小一点」。
 * AlertDialog 的留白是写死的、改不动：外圈上下各 24dp、标题→内容 16dp、内容→按钮 24dp，
 * 按钮和芯片还要各占 48dp 触摸高度（图案只有 32~40dp）——四五段叠起来就多出 100dp 空白。
 * 这里把这些间距全部收进一个地方，玻璃观感仍走 glassDialogModifier()/GlassDialogShape，
 * 和其余 AlertDialog（详情、设置里那些）保持一致。
 *
 * 尺寸不写死（原来是 `.width(296.dp)` 一锤子买卖）：
 *  - 宽度取「可用宽度的 92%」与 [width] 的较小者 —— 窄屏（320dp）上 296dp 的固定宽会顶满、
 *    左右一点余量都不剩；宽屏上也不会超过 296dp（视觉不变）。
 *  - 内容可以纵向滚动：系统字号调到最大（设置里还有「大」档 1.15×）叠上「＋备注」「标签」
 *    两种展开态时，弹窗会高过屏幕 —— 不可滚动的话底部按钮会被顶出去、弹窗关不掉。
 *
 * 用法：内容用 content 传（自动 6dp 行距），按钮用 ShixuDialogButton / ShixuDialogTextButton。
 * 注意：内容里**不要**用 ColumnScope 的 weight（滚动容器给子项的高度是无界的，weight 会崩）。
 */
/** 系统输入法（或自绘键盘）与弹窗之间固定留的缝：12dp —— 与键盘面板那档一致 */
private val IME_GAP = 12.dp

@Composable
fun ShixuDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    // 7.6.8（用户）：「那种很小的弹窗没必要做那么大的胶囊，挤在右下角、
    //   每颗只有原来一半长，反而更和谐」——一句话的确认框走紧凑档。
    compactButtons: Boolean = false,
    width: Dp = 276.dp,
    shadowPadH: Dp = SHADOW_PAD,
    shadowPadV: Dp = SHADOW_PAD,
    gap: Dp = 6.dp,
    // ★ 7.7.3（用户：「键盘弹出来的时候弹窗不要动」）：
    //   [bottomClearance] 是「玻璃体底边到容器底边」的净距。传了正数就表示这颗弹窗要
    //   在下方永久让出一块地方（底部键盘面板的高度 + 12dp 缝）—— 弹窗一次性按这个位置排好，
    //   **键盘升降都不再移动它**，动效只发生在面板自己身上。
    //   0 表示维持原行为（垂直居中，绝大多数弹窗都是这一档）。
    bottomClearance: Dp = 0.dp,
    // 贴容器底部的浮层（现在是底部键盘面板）。它和弹窗是两个并列的兄弟，
    // 键盘面板不参与弹窗的排版，所以弹窗绝不会被它顶得往上跳。
    bottomOverlay: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // usePlatformDefaultWidth = false：window 铺满屏幕，弹窗的遮罩（dim）才能盖住全屏。
    // 默认的 wrap_content window 会让遮罩只盖住弹窗那一小块，背景上出现一个方形色差。
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // 入场（7.4.0 方案 P1）：0.975 → 1.0 + 上浮 8dp，约 300ms 到位。
        // 旧版从 0.94 起（视觉上是「小七成」突然撑开），弹层边缘会明显扫过一段距离；
        // 0.975 让「长大」几乎看不出来，交代出现主要靠位移与淡入 —— 更像一张纸被放上来。
        // 曾经的 420ms / 阻尼 0.82 会甩过头再回来、尾巴拖到 400ms，那就是"不连贯"。
        // 7.4.1（方案 P0-5）：减弱动态效果时直接以终态出现 —— 弹窗的入场是「缩放 + 位移」，
        // 恰恰是无障碍里最容易被判定为不适的那一类动效。
        val reducedMotion = LocalReducedMotion.current
        val heroIn = remember { Animatable(0.975f) }
        LaunchedEffect(Unit) {
            MotionDebug.log("dialog", "open")
            if (reducedMotion) heroIn.snapTo(1f)
            else heroIn.animateTo(1f, spring(dampingRatio = 0.92f, stiffness = 520f))
        }
        // ★ 7.7.6：系统输入法升起时弹窗带缓动上移、并与键盘留 12dp 的缝。
        //   具体几何与几个坑都写在 DialogPlacement.kt（ShixuCompactDialog 共用同一套）。
        val placement = Modifier.dialogPlacement(bottomClearance = bottomClearance, shadowPadV = shadowPadV)
        Box(Modifier.fillMaxSize()) {
            Surface(
                shape = GlassDialogShape,
                color = glassDialogContainer(),
                // 宽度约束在 glassDialogModifier() 内部做（widthIn + fillMaxWidth），
                // 这里再套一层会让 drawBehind 的尺寸算错 → 玻璃体只铺中间一块。
                // ⚠️ placement 必须放在**最外层**：放在 glassDialogModifier 之后就成了
                //    「在 Surface 内部摆放正文」——place 的 y 会被当成内容偏移，正文被推出卡片。
                modifier = placement
                    .then(glassDialogModifier(maxWidth = width, shadowPadH = shadowPadH, shadowPadV = shadowPadV))
                    .graphicsLayer {
                        scaleX = heroIn.value
                        scaleY = heroIn.value
                        // 上浮 8dp：0.975 的缩放几乎看不出动，位移才是「被放上来」的主要线索
                        translationY = (1f - heroIn.value) / 0.025f * 8.dp.toPx()
                    },
            ) {
                // 弹窗正文每次打开都从顶部开始：rememberScrollState() 是 saveable 的，上次滚到哪
                // 这次就停在哪（用户 2026-11-03 报「点开时显示的是中间那段」）。
                val bodyScroll = remember { ScrollState(0) }
                LaunchedEffect(bodyScroll) { bodyScroll.scrollTo(0) }
                Column(
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 11.dp)
                        .verticalScroll(bodyScroll)
                ) {
                    Text(
                        title,
                        fontSize = DialogText.Title,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(7.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(gap), content = content)
                    Spacer(Modifier.height(8.dp))
                    // ── 按钮行（2026-11-08 改成 iOS 26 弹窗那种排布）──────────────
                    // 原来：右对齐、各自按内容宽度（保存 82px、取消 78px 挤在右边）。
                    // 现在：两枚等宽胶囊并排铺满整行，间距 8dp；只有一个按钮时独占整行。
                    // `propagateMinConstraints = true` 是把 Box 的宽度当成最小约束压给子按钮——
                    // 这样调用方那 20 多处 `ShixuDialogButton("保存") { }` 一个字都不用改（文案在 R.string.action_save）。
                    DialogButtonRow(confirmButton, dismissButton, compactButtons)
                }
            }
            // 底部面板（自绘键盘）是弹窗的**兄弟**，贴容器底，不参与弹窗排版
            if (bottomOverlay != null) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { bottomOverlay() }
            }
        }
    }
}

/** 弹窗主按钮（保存）。比默认矮 8dp、窄一点，配紧凑弹窗。
 *  2026-11-08：颜色从主题红（妃色 #ED5736）换成**枣红 #C32136**（用户：色卡里稍微深沉些的红），
 *  并且不再吃主题 primary —— 这样只动「保存」这一类实心按钮，不影响芯片/选中态/图标的红。 */
@Composable
fun ShixuDialogButton(
    text: String,
    // ⚠️ onClick 必须留在**最后一个**参数位：Kotlin 的尾随 lambda 只会绑最后一个函数类型参数，
    //   把颜色/开关塞到 onClick 后面（7.6.7 首版就这么写）会让所有 `ShixuDialogButton("保存") { … }`
    //   编译报「No value passed for parameter 'onClick'」——2026-11-12 又踩了一次 7.4.2 的老坑。
    // 7.6.7：允许换底色 —— 删除类动作要用错误色，但仍必须是同一颗胶囊。
    // 以前删除/二次确认各自写裸 TextButton，于是出现了「文字按钮」和「胶囊」两套皮肤。
    containerColor: Color = AccentDeep,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        // 端头是超椭圆而非正圆：36dp 高的胶囊上，正圆与直边的接点曲率突变会看成一道硬拐
        // （用户 2026-11-08：「直线和弧线连接的地方非常生硬」）。见 [CapsuleShape]。
        shape = CapsuleShape(),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = containerColor.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Text(text, fontSize = DialogText.Button)
    }
}

/**
 * 弹窗次按钮（取消）。
 *
 * 形状与高度对齐 [ShixuDialogButton]：同 36dp、同胶囊、1dp 淡描边，只是空心。
 * 旧版是纯文字按钮 —— 和右边实心胶囊摆在一起，视觉高度差一截，看着像没对齐
 * （用户 2026-11-05：「这个按钮可以改成稍微小一点的胶囊」）。
 */
@Composable
fun ShixuDialogTextButton(
    text: String,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        shape = CapsuleShape(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
        ),
        modifier = Modifier.height(36.dp),
    ) {
        Text(text, fontSize = DialogText.Button, color = contentColor)
    }
}

/*
 * 弹窗里输入框的高度：**不要指定**。
 *
 * M3 带标签的 OutlinedTextField 会按字体度量算自然高度。本 App 用的 songti_sc 字体度量很特殊
 * （ascent = 1.06em、descent = 0.435em，普通字体约 0.75/0.2），自然高度算出来是 64dp ——
 * 不是 M3 规范里的 56dp。所以任何"为了紧凑"写的 .height(52.dp) 之类都会踩这个坑：
 * 字段内部给文本行的高度不够，字身下半截被就地裁掉（p/g/q/y 的下伸部、汉字的下缘笔画全没了，
 * 而框内下方还留着几十 px 空白）。2026-10-16 逐像素实测（模拟器 1080×2400，密度 2.625）：
 *   'ABpgqy' 完整字形墨迹高 43px；52dp 框只画出 20px，56dp 剩 31px，60dp 剩 42px（临界），
 *   不指定高度（自然 64dp）与 80dp 基准一致、画出完整 43px；汉字 34px 同样在 60dp 起才完整。
 * 想更紧凑请从弹窗的其它间距里省（ShixuDialog 的 gap/内边距），别动输入框高度。
 */

/**
 * 弹窗底部按钮行（7.6.7 统一，7.6.8 加紧凑档）。
 *
 * 两档：
 * - **整行档**（默认，表单类弹窗）：两枚等宽胶囊铺满整行；只有一颗时独占整行。
 *   `propagateMinConstraints = true` 把 Box 的宽度当最小约束压给子按钮，
 *   所以调用点写 `ShixuDialogButton("保存") { }` 就会自己撑成胶囊，不用传 modifier。
 * - **紧凑档**（`compact = true`，一句话的确认框）：两颗挤在右下角、**按内容宽度**，
 *   并统一给 `min = 84dp`（≈ 整行档单颗的一半）好让两颗等宽。
 *   ⚠️ 不能按行宽百分比缩（首版用 0.24 行宽 → 58dp，减去 40dp 内边距只剩 18dp，
 *   「取消」被挤成两行还截字）。
 */
@Composable
fun DialogButtonRow(
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dismissButton != null) {
                Box(
                    Modifier.widthIn(min = 84.dp),
                    contentAlignment = Alignment.Center,
                    propagateMinConstraints = true,
                ) { dismissButton() }
            }
            Box(
                Modifier.widthIn(min = 84.dp),
                contentAlignment = Alignment.Center,
                propagateMinConstraints = true,
            ) { confirmButton() }
        }
    } else {
        Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dismissButton != null) {
                Box(
                    Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                    propagateMinConstraints = true,
                ) { dismissButton() }
                Box(
                    Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                    propagateMinConstraints = true,
                ) { confirmButton() }
            } else {
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                    propagateMinConstraints = true,
                ) { confirmButton() }
            }
        }
    }
}

/**
 * 详情弹窗右上角的圆形「i」（用户 2026-11-12）。
 *
 * 原话：「我想要把这个修改的按钮做成一个圆圈里面一个小写的 i，放到删除上方位置，
 * 然后剩下的两个按键就可以做小然后放到右侧」。于是「修改」从操作行里提出来，
 * 变成标题行右上角的一个信息圆钮 —— 语义也更准：它不是「关闭/删除」那类出口动作，
 * 而是「这条记录的属性还能改」的入口。
 *
 * 画法不用 Info 图标资源：图标自带的是无衬线粗体 i，跟 App 的宋体不搭；
 * 这里用 Text("i") 吃全局宋体，圆用 1dp 描边自绘。
 * 注意小写 i 只有 x-height + 一个点，靠 Text 默认行盒会偏下 —— 用 lineHeight 收紧再往回 1dp。
 */
@Composable
fun DialogInfoButton(
    // ⚠️ onClick 必须排在最后一个参数：尾随 lambda 只认「最后一个函数类型参数」，
    //   写成 (onClick, modifier) 时 `DialogInfoButton { … }` 会把 lambda 塞给 Modifier 而编译报错。
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .size(28.dp)
            // 2026-11-12 用户：「这个 i 可以再大一点粗一点」「圈圈也可以再粗一点」
            // 描边 1 → 1.5dp、i 15 → 17sp / Medium → Bold，透明度 .5 → .62
            // （圈变粗后若仍用 .5 会显脏，字要跟着提一点才压得住边）。
            .border(1.5.dp, tint.copy(alpha = 0.62f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "i",
            fontSize = 17.sp,
            lineHeight = 17.sp,
            color = tint.copy(alpha = 0.92f),
            fontWeight = FontWeight.Bold,
            // 2026-11-12：曾按「墨迹包围盒」做光学居中（offset -3dp/-4.3dp），
            // 实机看着整体上飘、左偏 —— 用户直接否决。**行盒居中就是对的**，
            // 小写 i 的点本来就占上方空间，别再拿 bbox 去搬位置。
        )
    }
}
