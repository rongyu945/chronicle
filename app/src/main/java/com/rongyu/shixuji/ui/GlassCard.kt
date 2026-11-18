package com.rongyu.shixuji.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rongyu.shixuji.theme.isDarkTheme

/*
 * 分组卡（大字卡）的玻璃外壳 —— 设置页与「问候时段」页共用一套。
 *
 * 抽出来的原因（2026-11-11）：问候时段页原来三张卡是「白纸 + 1dp 描边 + 13dp 圆角」，
 * 而设置页的卡早就是液态玻璃（体色偏冷白 + SPECULAR 亮边 + 落在纸上的投影）。
 * 两页并排看不是一个 App —— 用户的评价是「整体语言和液态玻璃、胶囊不太符合」。
 *
 * 采样源必须是 LocalPaperBackdrop（只录了背景色斑），**不能**用给底栏录的那层：
 * 这些卡就在它的录制子树里，采自己 = 递归。
 *
 * 参数逐条都有来历，见下面注释；改之前先读一遍，别当成随手可调的旋钮：
 *  · refractionHeight / Amount —— 卡片比底栏大得多，折射带按比例放大；
 *    位移反而收着，大面上位移过大会把边缘色斑拉成果冻。
 *  · blurRadius —— 卡后面是背景色斑，糊不够会看见色斑边界线横穿卡片，像脏印。
 *  · tint alpha —— 体色不能薄。浅色纸底上 0.30 的白几乎不改变像素，卡片比原来更糊；
 *    磨砂玻璃的意义是「比背景亮一档」，玻璃感交给边缘（折射 + SPECULAR + 投影）。
 *  · highlightAlpha —— 大面积高光要收着，0.3 以上铺在 328dp 宽的卡上会让上半张泛白。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = CardRadius.SECTION,
    content: @Composable BoxScope.() -> Unit,
) {
    val paper = LocalPaperBackdrop.current
    val shape = cardShape(radius)
    val glass = paper != null && supportsLiquidGlass
    val dark = isDarkTheme

    Box(modifier = modifier.fillMaxWidth()) {
        if (glass) {
            Box(
                Modifier
                    .matchParentSize()
                    .shadow(
                        12.dp, shape, clip = false,
                        ambientColor = if (dark) Color(0x14000000) else Color(0x0F000000),
                        spotColor = if (dark) Color(0x38000000) else Color(0x24000000)
                    )
                    .liquidGlass(
                        backdrop = paper!!,
                        shape = shape,
                        refractionHeight = 34.dp,
                        refractionAmount = 44.dp,
                        blurRadius = 10.dp,
                        depthEffect = true,
                        tint = (if (dark) Color(0xFF0B0908) else Color(0xFFFAFAFA))
                            .copy(alpha = if (dark) 0.34f else 0.42f),
                        veil = Color.Transparent,
                        tintBottom = null,
                        innerShadow = if (dark) 5.dp else 0.dp,
                        dispersion = if (dark) 0f else 0.06f,
                        rimLight = if (dark) 0.04f else 0.08f,
                        saturation = 1f,
                        vibrancy = if (dark) 1.05f else 1.32f,
                        highlightAlpha = if (dark) 0.10f else 0.18f,
                        highlightAngle = 45f,
                        highlightFalloff = 1.4f,
                        highlightSpread = 2.0f,
                        highlightWidth = 40.dp,
                        highlightColor = if (dark) Color(0xFFFFF8F0) else Color(0xFFFFFBF2),
                        rim = GlassRim.SPECULAR,
                    )
            )
        } else {
            // 没有纸底层（预览 / 单测）时退化成静态玻璃：观感同族，但不需要采样
            Box(
                Modifier.matchParentSize().glassSurface(
                    shape, dark = dark, ring = true, elevation = 4.dp, clipContent = false
                )
            )
        }
        content()
    }
}
