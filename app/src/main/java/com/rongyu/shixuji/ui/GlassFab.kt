package com.rongyu.shixuji.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.theme.isDarkTheme

/**
 * 悬浮圆钮（「＋」「今」「撤」）—— **实心圆 + 一层软投影**。
 *
 * 2026-11-06 用户给了张参考图（日历类 App 的右下角）：
 * 「按照这个界面去做加号，今，和撤回的效果，液态玻璃先缓缓，先做的好看一点最重要」。
 * 参考图里那两颗球一点玻璃感都没有：一颗是**纯色实心红圆 + 白色细十字**，
 * 一颗是**白圆 + 红字**，全靠一圈很软很淡的投影离开纸面。
 *
 * 所以把 5.9.2x 那套折射 / 模糊 / 色散 / 菲涅尔描边 / 镜面光斑整段撤掉，只留三件事：
 *  ① 一个正圆（[CircleShape]；原来是 RoundedCornerShape(50%)，在非正方形约束下会变椭圆）；
 *  ② 一层柔和投影（实心之后层次全靠它，浅色 black 0.09/0.17、深色更重）；
 *  ③ 图标：`+` 自己画两条圆头线 —— 字体里的加号粗细不均、上下也不居中；其余仍是文字。
 *
 * [tint] 现在是**填充色本身**：调用方传实色，别再传 0.1x 的 alpha（那是玻璃时代的写法）。
 * [backdrop] 只为兼容旧调用点保留，本实现不再使用。
 *
 * @param tint   圆的填充色（＋ = 主题妃色 Accent）
 * @param border 可选描边。白球压在米色纸面上必须有一道极淡的边，否则轮廓会糊进背景
 */
@Composable
fun GlassCircleButton(
    label: String,
    tint: Color,
    fallbackColor: Color,
    labelColor: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    labelSize: TextUnit = 22.sp,
    labelBold: Boolean = true,
    backdrop: LiquidGlassBackdropState? = null,
    border: Color? = null,
) {
    val press = remember { MutableInteractionSource() }
    val shape = CircleShape
    val density = LocalDensity.current
    // 十字：照参考图**逐像素量**的 —— 白十字墨迹 57×58px，球径 189px，所以臂长只占 30.2%、
    // 笔宽 4.5%（原来按 46% 画，十字几乎顶到球边，球就显大又笨）。
    val plusLen = with(density) { (size * 0.302f).toPx() }
    val plusStroke = with(density) { (size * 0.045f).toPx() }

    Box(
        modifier
            .size(size)
            // 阴影必须在 clickable 之前：它要画在球体下面，按压缩放才不会把影子一起压扁
            .shadow(
                elevation = if (isDarkTheme) 14.dp else 12.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (isDarkTheme) 0.52f else 0.12f),
                spotColor = Color.Black.copy(alpha = if (isDarkTheme) 0.48f else 0.24f)
            )
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .pressScale(press)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.matchParentSize().clip(shape).background(tint))
        if (border != null) {
            Box(Modifier.matchParentSize().clip(shape).border(1.dp, border, shape))
        }
        if (label == "+") {
            Canvas(Modifier.size(size * 0.302f)) {
                val c = this.size.width / 2f
                val inset = plusStroke / 2f
                val half = plusLen / 2f
                drawLine(
                    labelColor, Offset(c - half + inset, c), Offset(c + half - inset, c),
                    plusStroke, StrokeCap.Round
                )
                drawLine(
                    labelColor, Offset(c, c - half + inset), Offset(c, c + half - inset),
                    plusStroke, StrokeCap.Round
                )
            }
        } else {
            Text(
                label, fontSize = labelSize, color = labelColor,
                fontWeight = if (labelBold) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
