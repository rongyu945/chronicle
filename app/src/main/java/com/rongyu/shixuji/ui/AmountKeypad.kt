package com.rongyu.shixuji.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.theme.isDarkTheme
import com.rongyu.shixuji.theme.songtiFamily
import com.rongyu.shixuji.ui.motion.LocalReducedMotion

/**
 * 金额输入用的**自绘小键盘**（7.5.3）。
 *
 * 为什么不用系统键盘（审查 6.1）：金额框支持算式（`128/3`、`199*0.85`），但系统
 * `KeyboardType.Decimal` 的数字键盘在很多输入法里**只有数字和小数点**，要打 `× ÷ + − ( )`
 * 得先切到符号页；`KeyboardType.Ascii` 又变成字母键盘。自绘键盘一次把 20 个键摆出来，
 * 顺序固定、不会因为输入法不同而变样。
 *
 * 乘除显示成 **× ÷**（不是 * /）：这是中文记账里习惯的写法，[com.rongyu.shixuji.data.AmountExpr.normalize]
 * 会把它们换算成 `* /` 再算，所以显示与计算口径一致。
 *
 * 键位（4 列 × 5 行，共 20 键；7.6.6 起为「数字优先」布局）：
 * ```
 * (  )  AC    ⌫
 * 7  8  9   ÷
 * 4  5  6   ×
 * 1  2  3   −
 * 0─────  .  +
 * ```
 * 点一下给一次轻震动（与分段控件、底栏同一档 `TextHandleMove`）。
 */
@Composable
fun AmountKeypad(
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    // 7.6.4：41dp 的键在金额弹窗里偏小（用户反馈不好点），标准档提到 50dp；
    // 间距 6→8dp 让相邻键之间有清楚的缝。调用点仍可下调（常用记账页空间紧）。
    // ★ 7.7.3（用户：「数字小键盘的上下高度适当收个五分之一」）：54 → 43dp（键高 −20%，
    //   五行整体 302 → 247dp，约收 1/5）。**缝宽不动**：8dp 的键间距是 7.6.4 特意加的，
    //   等比缩缝会让相邻键更易误触；只压键高，键因此略扁，但热区仍有 43dp。
    keyHeight: Dp = 43.dp,
    gap: Dp = 8.dp,
) {
    val haptics = LocalHapticFeedback.current
    val tap = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    @Composable
    fun key(
        label: String,
        modifier: Modifier = Modifier,
        tone: KeyTone = KeyTone.Digit,
        onClick: () -> Unit,
    ) {
        val dark = isDarkTheme
        // ★ 7.6.12（用户：「改成 G3 的圆角」）：键的角从圆弧（G1，曲率在切点突变）
        //   换成 G3 超椭圆角（曲率与曲率导数在接缝处都为 0，直边「流」进角里）。
        //   见 ui/SmoothCornerShape.kt。
        //   smoothing = 1.0 → S = 13 × 2 = 26dp（角几乎吃掉半个键高，最「流」的那档，
        //   用户在强度对照页里选的 C）。
        val keyShape = smoothShape(radius = 13.dp, smoothing = 1.0f)
        val textColor = when (tone) {
            KeyTone.Digit -> if (dark) Color(0xFFEFE7E1) else Color(0xFF2E2A26)
            KeyTone.Operator -> MaterialTheme.colorScheme.primary
            KeyTone.Action -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val keyBg = when (tone) {
            // 运算符给一点点主色底，扫一眼就知道「这排是运算」
            KeyTone.Operator -> MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.14f else 0.10f)
            else -> if (dark) Color.White.copy(alpha = 0.06f) else Color(0xFF2E2A26).copy(alpha = 0.05f)
        }
        val keyLine = if (dark) Color.White.copy(alpha = 0.10f) else Color(0xFF2E2A26).copy(alpha = 0.10f)
        Box(
            modifier
                .height(keyHeight)
                // ★ 7.6.15（用户：「直线曲线的连接处还是有很明显的锯齿感」）：
                //   键的底和描边不再用 `clip` + `border` —— `Modifier.clip` 是**二值裁剪**，
                //   曲线边缘不做抗锯齿，超椭圆那些斜段就被切成一级级的阶梯。
                //   改成自己 drawPath（填充 + 描边都是抗锯齿的），边缘才是平滑的。
                .aaFill(keyShape, keyBg)
                .aaStroke(keyShape, keyLine, 1.dp)
                // 这里的 clip 只为把按下水波纹关在键内（波纹是软边，二值裁剪看不出）
                .clip(keyShape)
                .clickable {
                    tap()
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            // 7.6.17（用户：「数字小键盘的字号还可以再大一点」）：18sp → 21sp
            Text(label, fontSize = 21.sp, color = textColor, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, maxLines = 1)
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        // 7.6.6（用户从四方案里挑的 C「数字优先」）：
        //   第一行集中放低频键（括号 / AC / 退格），四个运算符连成最右一整列，
        //   数字仍是 789 / 456 / 123 的方阵，底部 0 跨两格。
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            key("(", Modifier.weight(1f), KeyTone.Operator) { onInsert("(") }
            key(")", Modifier.weight(1f), KeyTone.Operator) { onInsert(")") }
            key("AC", Modifier.weight(1f), KeyTone.Action, onClear)
            key("⌫", Modifier.weight(1f), KeyTone.Action, onBackspace)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            key("7", Modifier.weight(1f)) { onInsert("7") }
            key("8", Modifier.weight(1f)) { onInsert("8") }
            key("9", Modifier.weight(1f)) { onInsert("9") }
            key("÷", Modifier.weight(1f), KeyTone.Operator) { onInsert("÷") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            key("4", Modifier.weight(1f)) { onInsert("4") }
            key("5", Modifier.weight(1f)) { onInsert("5") }
            key("6", Modifier.weight(1f)) { onInsert("6") }
            key("×", Modifier.weight(1f), KeyTone.Operator) { onInsert("×") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            key("1", Modifier.weight(1f)) { onInsert("1") }
            key("2", Modifier.weight(1f)) { onInsert("2") }
            key("3", Modifier.weight(1f)) { onInsert("3") }
            key("−", Modifier.weight(1f), KeyTone.Operator) { onInsert("-") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            key("0", Modifier.weight(2f)) { onInsert("0") }
            key(".", Modifier.weight(1f)) { onInsert(".") }
            key("+", Modifier.weight(1f), KeyTone.Operator) { onInsert("+") }
        }
    }
}

private enum class KeyTone { Digit, Operator, Action }

/**
 * 底部键盘面板的总高（**不含**导航条 inset，调用方自己加）。
 *
 * 拆解：顶部 10 + 把手 4 + 把手下距 12 + 键区 247（5×43 + 4×8）+ 底部 14 = 287dp。
 * 弹窗的「让位量」直接用它算，所以改任何一个数字都要同步这里；单测 [ ] 里没有它，
 * 靠下面的 require 守住 —— 见 [BottomKeypadPanel] 的 dragHandle 高度。
 */
val KEYPAD_PANEL_HEIGHT: Dp = 287.dp

/**
 * 从屏幕底部滑出的**小键盘面板**（7.7.3）。
 *
 * 为什么不再是「键盘塞在弹窗正文里」（用户 2026-11-03：「做成类似系统键盘那样从底下弹出」）：
 * 上一版键盘跟着弹窗一起居中，结果是弹窗被 20 个键撑到 ~700dp 高、把背景遮掉大半，
 * 而键本身还只能做到 212px 宽。改成贴底全宽之后：
 *  - 键区 1017dp？——不，是 1080px 屏宽减去左右各 12dp，四个键从 212px 变 238px，更好点；
 *  - 弹窗只放表单，高度回到 ~270dp，四周留白舒服；
 *  - 位置固定：面板升降**不影响**弹窗（用户 2026-11-03：「键盘弹出来的时候弹窗不要动」），
 *    弹窗由调用点的 `bottomClearance` 一次算死，动画只发生在面板自己身上。
 *
 * 动效：只有位移（`slideInVertically`），**不加淡入** —— 透明纸两层交叉淡变在这套视觉语言里
 * 是明令禁止的（会同时画两张纸，玻璃录到的是两层底）。减弱动态效果时 [Motion] 之外直接出现，
 * 由 `LocalReducedMotion` 在面板内部短路。
 *
 * 面板的玻璃底故意比弹窗**淡**一点（0.94 白 vs 弹窗的多层高光）：键盘是"垫在下面的一层台面"，
 * 不该比内容本身更亮。顶部加一道 24dp 的向上渐变 + 1px 高光线表示它压在内容之上。
 */
@Composable
fun BottomKeypadPanel(
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    // 导航条（手势条）高度：由调用方读 windowInsets 后传进来，保证面板高度与弹窗算出的
    // 让位量是**同一个数**（各自读一次 inset 一旦不同步，弹窗底就会被键盘顶掉几十像素）。
    navInset: Dp = 0.dp,
    keyHeight: Dp = 43.dp,
    gap: Dp = 8.dp,
) {
    val dark = isDarkTheme
    val reducedMotion = LocalReducedMotion.current
    val radius = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    // 面板底色：浅色主题一层暖白；深色主题跟着弹窗走，用暖深灰而不是纯黑
    val panel = if (dark) Color(0xFF2A2622).copy(alpha = 0.96f) else Color(0xFFF6F1EB).copy(alpha = 0.97f)
    // 7.7.4：顶上那道大渐变删掉后，分界全压在这条线上 —— 所以从 0.09/0.10 提到 0.14/0.18，
    // 刚好「看得见一条边」但不至于变成描边。
    val hairline = if (dark) Color.White.copy(alpha = 0.18f) else Color(0xFF2E2A26).copy(alpha = 0.14f)
    val handleColor = if (dark) Color.White.copy(alpha = 0.22f) else Color(0xFF2E2A26).copy(alpha = 0.18f)

    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) EnterTransition.None
        else slideInVertically(animationSpec = tween(300, easing = LinearOutSlowInEasing)) { it },
        exit = if (reducedMotion) ExitTransition.None
        else slideOutVertically(animationSpec = tween(240, easing = FastOutLinearInEasing)) { it },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // 顶上那道阴影画在面板**外面**（负 y），所以必须写在 clip 之前：
                // clip 之后再画只会被裁在面板内，看不出「压在内容上」的层次
                //
                // ★ 7.7.4：**这道 22dp 向上的渐变整个删掉了**。它横贯整屏、压过弹窗底边
                //   （弹窗只有 ~992px 宽，渐变却是 fillMaxWidth 的 1080px）又盖住两侧背景，
                //   渲染出来就是横在键盘上方的一条大灰黑污痕 —— 用户 2026-11-12 一眼指出。
                //   层次现在只靠「面板自己更淡的底色 + 顶边 1px 高光线」表达：够分界，且不脏。
                //   以后要加分层，只能用 1px 线，不要再往弹窗外面铺大块渐变。
                // 圆角只做上面两个：底部要顶到屏幕边，圆角会把导航条区域露出来
                .clip(radius)
                .background(panel)
                .drawWithContent {
                    drawContent()
                    // 顶边高光线：这道线是「台面」的边沿，不是描边（所以只有一条、1px）
                    drawLine(
                        color = hairline,
                        start = Offset(0f, 0.5.dp.toPx()),
                        end = Offset(size.width, 0.5.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp + navInset)
                // ★ 7.7.9：退场动画的 240ms 里面板仍在屏幕上、键还在原位 —— 此时落下的手指
                //   照样能点中键位（AnimatedVisibility 只管画，不管拦截触摸）。用户既然已经
                //   明确离开金额模式（点备注、点收起），这半秒里被改掉的金额就是纯粹的误操作。
                //   所以不可见期间把整层的按下事件全部吃掉。
                .then(
                    if (visible) Modifier
                    else Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
                )
        ) {
            // 把手：系统键盘没有，但这是"可滑出的一层"的身份标识，也给手指一个抓取位置
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(handleColor)
            )
            Spacer(Modifier.height(12.dp))
            AmountKeypad(
                onInsert = onInsert,
                onBackspace = onBackspace,
                onClear = onClear,
                keyHeight = keyHeight,
                gap = gap,
            )
        }
    }
}


/**
 * 金额的**只读显示框**（配合 [AmountKeypad] 用）。
 *
 * 为什么不再用 `BasicTextField`：只要还有一个可获焦的输入框，系统输入法就会被拉起来，
 * 两条键盘叠在一起既挤又乱（而且 IME 的 resize 会把弹窗顶上去）。
 * 这里只画文字 + 一根光标，输入全部由自绘键盘驱动。
 *
 * ★ 7.7.2（复核报告 §4.3）：光标不再固定在末尾 —— 传进 [caret] 画在对应字符位置，
 *   点文字任意一处通过 [onCaretChange] 把光标挪过去，键盘再按光标位置插入/退格。
 *   定位用 `TextLayoutResult.getOffsetForPosition()`（点 → 最近的字符边界）与
 *   `getHorizontalPosition()`（字符 → 横坐标），两者是 Compose 自己的一对逆运算，
 *   中英文混排、不同字号都不用自己量字宽。
 *
 * 样式与 [ShixuField] 保持一致（同样的圆角、同样的描边、聚焦色改为「有内容时即为主题色」），
 * 看起来仍是同一个输入框，只是点它不再弹输入法。
 */
@Composable
fun AmountDisplay(
    text: String,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    caret: Int = text.length,
    // ★ 7.7.9：金额框的「激活态」。它是只读显示框，没有 TextField 那样的焦点，
    //   所以以前点它（自绘键盘接管输入）时框上一个提示都没有 —— 而点备注框会亮出
    //   明显的橙色聚焦描边，两个框的「正在给谁输入」表达不一致。
    //   现在由调用点把 `inputMode == Amount` 传进来，激活时用**与 ShixuField 聚焦完全相同**
    //   的那一档颜色（primary 0.80），于是整个界面只有一条规则：
    //   橙边 = 当前输入目标。
    isActive: Boolean = false,
    onCaretChange: (Int) -> Unit = {},
) {
    val dark = isDarkTheme
    val radius = smoothShape(11.dp, G3_SMOOTHING)
    val border = when {
        isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
        // 激活：与 ShixuField 聚焦同色同透明度，肉眼是同一件事
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
        // 有内容但没在输入：留一点主题色，表示「这里已经有数据」，比灰边好认
        text.isNotEmpty() -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        dark -> Color.White.copy(alpha = 0.16f)
        else -> Color(0xFF2E2A26).copy(alpha = 0.22f)
    }
    // 记住最近一次排版结果：点击定位与画光标都要用它的度量
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val caretColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            // ★ 7.7.3：点击定位改挂在**整个框**上（原来只在有文字的那个 Text 上，
            //   金额为空时点框毫无反应 —— 底部键盘收起后就没法点它把键盘叫回来了）。
            //   空文本时 layout 还没生成，落到「末尾」（text.length）即光标归 0。
            //
            //   为什么用 `awaitEachGesture`+`awaitFirstDown(pass = Initial)` 而不是
            //   `detectTapGestures`：备注/名称这类 TextField 聚焦时，Compose 的
            //   「点外部清焦点」会在 **Initial** 阶段抢走第一次点击，detectTapGestures
            //   （Main 阶段）根本收不到 —— 实测点金额框毫无反应。在 Initial 阶段先收下，
            //   既能立刻定位光标，也让调用方有机会 clearFocus 收掉输入法。
            .pointerInput(text) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val l = layout
                    onCaretChange(if (l != null) l.getOffsetForPosition(down.position) else text.length)
                }
            }
            // 7.6.16：填充走抗锯齿路径，不再 clip + background
            .aaFill(
                radius,
                if (dark) Color.White.copy(alpha = 0.05f) else Color(0xFF2E2A26).copy(alpha = 0.05f)
            )
            .border(1.dp, border, radius)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (text.isEmpty()) {
            Text(label, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f))
        } else {
            /** 光标横坐标（px）。夹取到文字宽度内，两端半个线宽不会被画框切掉。 */
            fun caretX(l: TextLayoutResult, index: Int, lineW: Float, lineW2: Float): Float =
                l.getHorizontalPosition(index.coerceIn(0, text.length), usePrimaryDirection = false)
                    .coerceIn(0f, (lineW - lineW2).coerceAtLeast(0f))

            Text(
                text,
                // 7.6.19（用户：「输入框里的字体不是宋体」）：`style = TextStyle(...)` 会整份替掉
                // LocalTextStyle，字族没写死就退回系统无衬线 —— 金额框里打出来的数字不是宋体。
                style = TextStyle(
                    fontFamily = songtiFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = if (dark) Color(0xFFEFE7E1) else Color(0xFF2E2A26),
                maxLines = 1,
                onTextLayout = { layout = it },
                modifier = Modifier
                    // 让文字撑满整行：这样点在右侧空白处也能落到「末尾」，
                    // 与 getOffsetForPosition 的语义（点在行外 → 最近端）自然一致。
                    .fillMaxWidth()
                    .drawWithContent {
                        drawContent()
                        val l = layout ?: return@drawWithContent
                        val w = 1.5.dp.toPx()
                        val h = 16.dp.toPx()
                        val x = caretX(l, caret, size.width, w)
                        // 竖直方向跟着行盒居中（不是整框居中），字号变了也不会偏
                        val top = (size.height - h) / 2f
                        drawRect(color = caretColor, topLeft = Offset(x, top), size = Size(w, h))
                    }
            )
        }
    }
}
