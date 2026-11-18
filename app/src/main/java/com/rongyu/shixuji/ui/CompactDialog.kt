package com.rongyu.shixuji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import com.rongyu.shixuji.ui.motion.LocalReducedMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rongyu.shixuji.theme.isDarkTheme
import com.rongyu.shixuji.theme.songtiFamily

/**
 * 紧凑弹窗 —— 和 M3 `AlertDialog` 参数同形，但把那圈写死的留白全收掉。
 *
 * 2026-11-06 用户：「有些弹窗明显可以缩小的请你缩小一点，我都不知道这些文字输入框为什么都如此巨大，
 * 和我精致的画面风格不符」。两条都要治：
 *
 * ① 弹窗本身：M3 AlertDialog 的内边距是写死的（四周 24dp、标题→正文 16dp、正文→按钮 24dp、
 *    按钮还要各占 48dp 触摸高度），一个只有一行输入的「添加标签」也被撑成一大块。
 *    这里统一收到 16dp 外框 / 6dp 行距 / 36dp 按钮。
 * ② 输入框：见 [ShixuField]。
 *
 * 用这个函数替掉 `AlertDialog` 时**参数名一一对应**（onDismissRequest / title / text /
 * confirmButton / dismissButton），所以替换只是改函数名。`shape` / `containerColor` /
 * `properties` 这几个旧参数保留只为编译兼容，实际全由玻璃体接管。
 */
@Composable
fun ShixuCompactDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    // 7.6.8：一句话的确认框用紧凑档（两颗小胶囊挤右下角），表单类留整行档
    compactButtons: Boolean = false,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape = GlassDialogShape,
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Transparent,
    @Suppress("UNUSED_PARAMETER") properties: DialogProperties =
        DialogProperties(usePlatformDefaultWidth = false),
    width: Dp = 276.dp,
    shadowPadH: Dp = SHADOW_PAD,
    shadowPadV: Dp = SHADOW_PAD,
    // 与 ShixuDialog 同一套语义（7.7.3）：底部让位量 + 贴底浮层，弹窗本身不随键盘移动
    bottomClearance: Dp = 0.dp,
    bottomOverlay: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        // window 铺满屏：遮罩（dim）才能盖住全屏，否则屏幕上会出现一块和弹窗等宽的方形色差。
        // decorFitsSystemWindows = false：不让系统替我们 resize window —— 那是一次**跳变**
        // （弹窗先画在偏下、键盘一起来直接跳到上面）。改由 Compose 用 imePadding 跟随
        // 输入法的 inset 动画平滑上推。
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
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
            if (reducedMotion) heroIn.snapTo(1f)
            else heroIn.animateTo(1f, spring(dampingRatio = 0.92f, stiffness = 520f))
        }
        // ★ 7.7.6：与 ShixuDialog 共用同一套定位（IME 缓动上移 + 12dp 缝 的来龙去脉见
        //   DialogPlacement.kt）。旧写法是 `Box.fillMaxSize().imePadding()` + 居中/BottomCenter：
        //   居中那档只会把卡片往中间挤（实际只上移一半），IME 起来后按钮还是压在键盘上。
        val placement = Modifier.dialogPlacement(bottomClearance = bottomClearance, shadowPadV = shadowPadV)
        Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = GlassDialogShape,
            color = glassDialogContainer(),
            modifier = placement
                .then(glassDialogModifier(maxWidth = width, shadowPadH = shadowPadH, shadowPadV = shadowPadV))
                .then(modifier)
                .graphicsLayer {
                    scaleX = heroIn.value
                    scaleY = heroIn.value
                    // 上浮 8dp：0.975 的缩放几乎看不出动，位移才是「被放上来」的主要线索
                    translationY = (1f - heroIn.value) / 0.025f * 8.dp.toPx()
                },
        ) {
            // 内容可能很长（标签颜色 18 个、预算说明一大段），给个上限并允许滚动，
            // 否则大字号下底部按钮会被顶出屏幕、弹窗关不掉
            val bodyScroll = remember { ScrollState(0) }
            LaunchedEffect(bodyScroll) { bodyScroll.scrollTo(0) }
            // ★ 7.5.5（复核报告 4.3）：整块内容**不再共用一个滚动列**，改成「固定标题 + 可滚正文 +
            //   固定按钮」三段。
            //   旧结构里标题、正文、按钮同在一个 `heightIn(560dp).verticalScroll` 里：内容一长，
            //   排在最后的按钮就被挤出可视区 —— 常用记账弹窗加进 20 键键盘后，「保存」实测被压到
            //   1px（看不见也点不着），当时只能靠把键高压到 32dp 硬挤回来。那不是修好，是把问题藏起来。
            //   现在正文是**唯一**滚动区（weight 吃掉剩余高度），标题和按钮永远在原位；
            //   键盘恢复标准 42dp 也不会再顶掉按钮 —— 内容超出时正文自己滚。
            Column(
                Modifier
                    .heightIn(max = 640.dp)
                    .padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (title != null) {
                    // M3 的 AlertDialog 会给 title 槽套 titleLarge；我们自己排的时候必须补上，
                    // 否则调用方只写 Text("…") 会掉回 bodyLarge 14sp，看着比正文还小
                    ProvideTextStyle(
                        TextStyle(
                            // fontFamily 必须显式写：ProvideTextStyle 是「整体替换」，
                            // 不写就退回平台默认无衬线，整个弹窗的宋体全没了（2026-11-06 用户发现）
                            fontFamily = songtiFamily,
                            fontSize = DialogText.Title, lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    ) { title() }
                }
                if (text != null) {
                    // 正文 = 唯一的滚动区：weight 吃掉「标题与按钮之外」的剩余高度，
                    // fill = false 让内容短时弹窗自然缩小（不会撑满 560dp）
                    Column(Modifier.weight(1f, fill = false).verticalScroll(bodyScroll)) {
                        ProvideTextStyle(
                            TextStyle(
                                fontFamily = songtiFamily,
                                fontSize = DialogText.Body, lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        ) { text() }
                    }
                }
                // 按钮区的文字样式单独给 14sp：M3 的 AlertDialog 会用 labelLarge 排按钮，
                // 换成自己排之后不补这一层，按钮字会掉到正文的 13sp、显得发虚。
                //
                // ★ 颜色**不能**写进这个 TextStyle（2026-11-08 实测踩到）：Text 取色的优先级是
                //   「TextStyle.color 非 Unspecified 时优先于 LocalContentColor」。这里一旦写死
                //   primary，里面每个 TextButton 自己的 contentColor 就全被压掉 —— 详情弹窗的
                //   「关闭 / 修改 / 删除」三颗一起变红，改 ButtonDefaults 完全没反应。
                //   改成「样式不写色 + 外层 provides primary」：默认仍是妃色（其余弹窗观感不变），
                //   而显式传了 colors 的按钮能自己覆盖。
                ProvideTextStyle(
                    TextStyle(fontFamily = songtiFamily, fontSize = DialogText.Button, lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium)
                ) {
                  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                    // ★ 7.6.7（用户：「多处弹窗的下方按钮还是没有做成胶囊」「常用记账弹窗和记一笔
                    //   风格不统一」）：这里原来是把两个按钮**右对齐**丢在一行末尾，而 ShixuDialog
                    //   早就是「两枚等宽胶囊铺满整行」。两套结构并存 → 同样是确认弹窗，一个长得像
                    //   iOS，一个像 M3。现在统一走 ShixuDialog 的排布：
                    //   propagateMinConstraints = true 把 Box 的宽度当最小约束压给子按钮，
                    //   所以 ShixuDialogButton / ShixuDialogTextButton 自己就会撑成等宽胶囊。
                    DialogButtonRow(
                        confirmButton = confirmButton,
                        dismissButton = dismissButton,
                        compact = compactButtons,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        }
            if (bottomOverlay != null) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { bottomOverlay() }
            }
        }
    }
}

/** 弹窗标题文字：16sp 粗体（比 M3 的 24sp 收一档，和紧凑布局配套） */
@Composable
fun ShixuDialogTitle(text: String) {
    Text(
        text,
        fontSize = DialogText.Title,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 紧凑输入框 —— 高 44dp（原来 M3 OutlinedTextField 在 songti 字体下自然高 64dp）。
 *
 * 为什么不用 `OutlinedTextField` 压高度：
 * 带浮动 label 的 OutlinedTextField 要预留「label 上浮跨在边框上的位置 + supportingText 位」，
 * 它按字体度量算自然高度；本 App 的 songti_sc（ascent 1.06em / descent 0.435em）算出来是 64dp。
 * 一旦 `.height(52.dp)` 硬压，文本行在字段内部被就地裁掉下半截（2026-10-16 逐像素实测：
 * 52dp 只剩 20px 笔画、56dp 剩 31px、60dp 剩 42px，完整字形 43px）。
 *
 * 所以这里干脆不用那套浮动 label：**label 当占位符用**（空的时候显示在框内），字号 13sp；
 * 输入文字 15sp、行高 22sp，44dp 的框上下各留 11dp，怎么都不会裁。
 * 高度完全可控，视觉上和 App 里那些胶囊、卡片是一套刻度。
 */
/** 聚焦时描边变主题色 —— 抽出来给两个重载共用 */
@Composable
private fun focusBorder(interaction: MutableInteractionSource, isError: Boolean): Color {
    val focused by interaction.collectIsFocusedAsState()
    val dark = isDarkTheme
    return when {
        isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
        focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
        dark -> Color.White.copy(alpha = 0.16f)
        else -> Color(0xFF2E2A26).copy(alpha = 0.22f)
    }
}

/** TextFieldValue 版：需要自己管选区（金额框的算式过滤）时用它 */
@Composable
fun ShixuField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    innerModifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    height: Dp = 44.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val border = focusBorder(interaction, isError)
    val radius = smoothShape(11.dp, G3_SMOOTHING)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            // 7.6.16：填充改抗锯齿路径（原来 clip + background 是二值裁剪，角上发毛）
            .aaFill(
                radius,
                if (isDarkTheme) Color.White.copy(alpha = 0.05f)
                else Color(0xFF2E2A26).copy(alpha = 0.05f)
            )
            .border(1.dp, border, radius)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.text.isEmpty()) {
            Text(
                label, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = fieldTextStyle(),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth().then(innerModifier)
        )
    }
}

/** String 版（大多数调用点）。直接用 BasicTextField 的 String 重载 —— 自己包一层
 *  `TextFieldValue(value)` 会在每次重组时丢掉光标位置，中文输入法的组合态也会断。 */
@Composable
fun ShixuField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    innerModifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    height: Dp = 44.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val border = focusBorder(interaction, isError)
    val radius = smoothShape(11.dp, G3_SMOOTHING)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            // 7.6.16：填充改抗锯齿路径（原来 clip + background 是二值裁剪，角上发毛）
            .aaFill(
                radius,
                if (isDarkTheme) Color.White.copy(alpha = 0.05f)
                else Color(0xFF2E2A26).copy(alpha = 0.05f)
            )
            .border(1.dp, border, radius)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                label, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = fieldTextStyle(),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth().then(innerModifier)
        )
    }
}

@Composable
private fun fieldTextStyle() = TextStyle(
    // BasicTextField 的 textStyle 也不继承 LocalTextStyle 的字族 —— 同样必须写死，
    // 否则输入框里打出来的中英文都是系统无衬线（用户：「西文不是新罗马体了」）
    fontFamily = songtiFamily,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    color = MaterialTheme.colorScheme.onSurface,
)

/** 弹窗正文的说明文字：13sp、行高 20sp（原来是 bodyMedium 14sp，行距偏松） */
@Composable
fun ShixuDialogBody(text: String) {
    Text(
        text, fontSize = DialogText.Body, lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
