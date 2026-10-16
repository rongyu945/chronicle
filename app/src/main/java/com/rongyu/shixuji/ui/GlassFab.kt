package com.rongyu.shixuji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 悬浮圆钮（「＋」「今」）的**玻璃版**——和底栏同一套液态玻璃（折射 + 模糊 + 边缘高光）。
 *
 * 两条铁律（都由 LiquidGlass 的实现决定）：
 * ① 玻璃必须是**独立的一层**，文字画在它外面。把文字一起放进带 RenderEffect 的那层，字会被折射+模糊糊掉。
 * ② 必须传 [backdrop]（背景采样层）。拿不到（安卓 12 以下 / 没有 backdrop）就退回原来的不透明圆。
 *
 * @param tint          玻璃表面的着色（＋ 用主色半透明 = 磨砂红玻璃；「今」用淡白 = 清玻璃）
 * @param fallbackColor 无玻璃时的不透明底色
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
    labelBold: Boolean = false,
    backdrop: LiquidGlassBackdropState? = null,
) {
    val press = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .size(size)
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .pressScale(press)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        if (backdrop != null && supportsLiquidGlass) {
            Box(
                Modifier.matchParentSize().liquidGlass(
                    backdrop = backdrop,
                    shape = shape,
                    refractionHeight = 14.dp,
                    refractionAmount = 16.dp,
                    blurRadius = 8.dp,
                    depthEffect = true,
                    tint = tint,
                    strokeInset = 1.dp,
                    topHighlight = false
                )
            )
        } else {
            Box(Modifier.matchParentSize().clip(shape).background(fallbackColor))
        }
        Text(
            label, fontSize = labelSize, color = labelColor,
            fontWeight = if (labelBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
