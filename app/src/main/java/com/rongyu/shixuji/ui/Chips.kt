package com.rongyu.shixuji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.rongyu.shixuji.theme.Accent
import com.rongyu.shixuji.theme.AccentDark
import com.rongyu.shixuji.theme.isDarkTheme
import com.rongyu.shixuji.theme.songtiFamily

// 胶囊高度：M3 默认 32dp 在这个界面里显得「胖」（用户 2026-11-04：「胶囊太胖了，上下高度再细一点」）。
// 26dp = 13sp 文字上下各留 ~6dp，视觉上是一颗精细的细胶囊，不再是药丸坨。
private val CHIP_HEIGHT = 26.dp

/**
 * 胶囊里文字的唯一字号：13sp。
 *
 * 以前 [ShixuChip] 不设字号，label 直接吃 `LocalTextStyle`：
 * 在「记一笔」这种 `ShixuDialog` 里没有 ProvideTextStyle，于是掉回 M3 的 bodyLarge **16sp**
 * （用户 2026-11-08：「支出和收入的字明显很大」）；而同样的胶囊放在 `ShixuCompactDialog` 的
 * text 槽里就只有 13sp —— 同一颗控件两个大小。现在在控件内部统一，调用处写不写 fontSize 都一样。
 */
private val ChipTextStyle = TextStyle(
    fontFamily = songtiFamily,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.Normal,
)

/**
 * 胶囊的「液态玻璃」皮肤（2026-11-06）。
 *
 * 为什么这里不用 `Modifier.liquidGlass`：
 * 这些胶囊只有 26dp 高、六七十 dp 宽，**折射带最多只能给到 8dp**，着色器几乎使不上劲；
 * 更麻烦的是它们的宿主——新增待办 / 记账的弹窗、批量整理页——里面根本没有
 * 一层「只含背景」的录制源可采样（采样弹窗自己 = 递归）。与其为几十颗小胶囊各挂一个
 * RenderEffect（一屏十几个标签，滚动直接掉帧），不如把玻璃的**四层结构**画出来：
 *
 *   ① 体色：竖向渐变（上亮下淡）—— 玻璃有厚度，不是一块死色
 *   ② 内亮环：1px，沿轮廓收一圈 —— 真玻璃把环境光收进来的那圈边
 *   ③ 顶部高光：横向渐变（中间最亮两头淡出）—— 上缘的镜面反射
 *   ④ 投影：极小半径，只负责「浮起 1mm」
 *
 * 选中态 = 同一套结构换成主题色：体色是半透明妃色、亮环偏妃，
 * 于是它读起来是「一块染了色的玻璃」，而不是「一块红色塑料」。
 */
private fun Modifier.glassCapsule(
    dark: Boolean,
    selected: Boolean,
    accent: Color,
    shape: Shape,
    elevation: Dp = 2.dp
): Modifier = this
    .shadow(
        elevation, shape, clip = false,
        ambientColor = if (dark) Color(0x33000000) else Color(0x0F000000),
        spotColor = if (dark) Color(0x4D000000) else Color(0x1A000000)
    )
    .clip(shape)
    .drawBehind {
        val cr = shape.cornerPx(size, this)
        val corner = CornerRadius(cr, cr)
        val line = 1.dp.toPx()

        // ① 体色。选中 = 妃色玻璃，未选中 = 白玻璃。
        // 2026-11-06 第一版给 0.62→0.44，实机看下来太"塑料"——白得太满、没有通透感。
        // 降到 0.44→0.28：它现在读起来是一块薄玻璃，而不是一片白漆。
        //
        // 2026-11-08：三处描边/体色从 drawRoundRect 换成 [drawCardShape]，
        // 这样胶囊能按超椭圆端头画（见 [CapsuleShape]）—— 否则 clip 是超椭圆、
        // 画出来的身子是正圆端头，端头内侧会露一圈错位的边缘。
        drawCardShape(
            shape = shape,
            size = size,
            corner = corner,
            brush = Brush.verticalGradient(
                colors = when {
                    selected && dark -> listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.20f))
                    selected -> listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.12f))
                    dark -> listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.06f))
                    else -> listOf(Color.White.copy(alpha = 0.46f), Color.White.copy(alpha = 0.28f))
                }
            ),
        )

        // ①½ 边缘厚度：亮环内侧再压一道极淡的暗线。
        // 没有它，亮环下面直接接体色，边界只是"贴上去的一道白线"；
        // 白线—暗线—体色 三层排开，边缘才有「光在介质边界被弯折」的厚度。
        // （画在轮廓内侧：外面被 clip 裁掉了，见上面的 .clip(shape)）
        val innerInset = line * 1.6f
        drawCardShape(
            shape = shape,
            size = size,
            corner = corner,
            inset = innerInset,
            color = if (dark) Color.Black.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.05f),
            style = Stroke(width = line * 0.9f),
        )

        // ② 内亮环：内缩半线宽画，才是一条贴在轮廓里的线（画在边上会被圆角吃掉一半）。
        val inset = line / 2f
        drawCardShape(
            shape = shape,
            size = size,
            corner = corner,
            inset = inset,
            color = when {
                selected && dark -> accent.copy(alpha = 0.62f)
                selected -> accent.copy(alpha = 0.50f)
                dark -> Color.White.copy(alpha = 0.16f)
                else -> Color.White.copy(alpha = 0.85f)
            },
            style = Stroke(width = line),
        )

        // ③ 顶部高光：30dp 高的胶囊只留 1px 就够，再宽会整颗泛白、把字压灰。
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = if (selected) 0.45f else if (dark) 0.24f else 0.75f),
                    Color.Transparent
                )
            ),
            topLeft = Offset(size.width * 0.10f, line * 0.6f),
            size = Size(size.width * 0.80f, line),
            cornerRadius = CornerRadius(line / 2f, line / 2f)
        )
    }

/**
 * 全 App 统一的「选择框」（芯片）。
 *
 * 用户 2026-10-16：「很多选择的框框都是白色边框，可读性有点差，改成和主题色淡红相近的颜色」。
 * 根因：M3 的 FilterChip 未选中时描边取 colorScheme.outline，本 App 主题里那是「白色 72% 透明」，
 * 落在浅色玻璃卡上等于看不见（同理还有勾选框 / 开关 / 单选圈的描边）。
 *
 * 2026-11-06：从 M3 `FilterChip` 换成自绘，套上 [glassCapsule]。
 * 原来未选中是**完全透明 + 一圈灰边**，在弹窗的玻璃体上等于两片玻璃直接贴在一起，
 * 边界全靠那条 18% 的灰线撑着。现在它自己有体色、有亮环、有投影，
 * 就不再是"贴在弹窗上的一道线"，而是一颗独立的小玻璃。
 */
@Composable
fun ShixuChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
) {
    val dark = isDarkTheme
    val red = if (dark) AccentDark else Accent
    // 胶囊的端头用超椭圆（曲率连续），不是正圆 —— 见 [CapsuleShape] 的注释。
    val shape = remember { CapsuleShape() }
    val press = remember { MutableInteractionSource() }
    val ink = if (selected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier
            .height(CHIP_HEIGHT)
            .glassCapsule(dark, selected, red, shape, elevation = if (selected) 3.dp else 2.dp)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            CompositionLocalProvider(LocalContentColor provides ink) { leadingIcon() }
            Spacer(Modifier.width(5.dp))
        }
        CompositionLocalProvider(LocalContentColor provides ink, LocalTextStyle provides ChipTextStyle) { label() }
    }
}

/**
 * 小号「标签胶囊」：给二级筛选（标签、只看收入、全选这类）用。
 *
 * 和 [ShixuChip] 的区别不是形状（都是胶囊），而是**分量**：它更矮、字更小、没有 M3 的
 * 48dp 触摸区外壳，所以一行能放下更多个，也不会跟一级的玻璃分段抢视线。
 */
@Composable
fun ShixuTagChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    /** 和别的控件并排时提高一点（高矮要和邻居同级），默认维持二级小胶囊的 23dp。 */
    minHeight: Dp = 23.dp,
    fontSize: TextUnit = 11.sp,
) {
    val dark = isDarkTheme
    val accent = MaterialTheme.colorScheme.primary
    // 胶囊的端头用超椭圆（曲率连续），不是正圆 —— 见 [CapsuleShape] 的注释。
    val shape = remember { CapsuleShape() }
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .defaultMinSize(minHeight = minHeight)
            // 比一级胶囊再轻一档：2dp → 1dp，它更矮更小，投影一重就成了"厚塑料片"。
            .glassCapsule(dark, selected, accent, shape, elevation = 1.dp)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dotColor != null) {
            Box(Modifier.size(6.dp).background(dotColor, androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}