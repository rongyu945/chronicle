package com.rongyu.shixuji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rongyu.shixuji.theme.BgLight
import com.rongyu.shixuji.theme.isDarkTheme

/**
 * 三个页面底部共用的「纸色压层」。
 *
 * 要解决的是：玻璃底栏是透的，它背后露出的是彩色背景；而列表滚到下面时又是纸色卡片 ——
 * 一条浅、一条彩，横着就有一条接缝。这里把内容在底栏上方一段柔和地淡成纸色、
 * 并一路铺到屏幕最底（穿过底栏与手势条），底栏背后就也是纸，接缝消失。
 *
 * 用法（必须在页面根 Box 里、**在悬浮按钮之前**调用）：
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     Column(...) { ...列表... }
 *     BottomPaperFade()          // ← 压在内容上、按钮下
 *     GlassCircleButton(...)
 * }
 * ```
 * 放在按钮之后会把「＋/今/撤」一起糊掉 —— 它们下沿就贴着底栏上沿，正好落在实心段里。
 *
 * @param extendBottom 页面底边止于导航栏上沿（Scaffold 的内边距），要铺满屏幕底部就得往下顶出这一段。
 *   不给就用本机导航栏高度（手势条机型约 28dp）。
 * @param height 渐隐段总高。太短则收边急，太长会吃掉列表内容。
 */
@Composable
fun BoxScope.BottomPaperFade(
    extendBottom: Dp = 28.dp,
    height: Dp = 240.dp
) {
    // 纸色必须用真实不透明色：本主题 colorScheme.background 是 Color.Transparent，
    // 拿它 copy(alpha=…) 只会得到一条黑带（透明色的 RGB 是 0）。
    val paper = if (isDarkTheme) Color(0xFF121114) else BgLight
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .offset(y = extendBottom)
            .fillMaxWidth()
            .height(height)
            .background(
                // 2026-11-05：这层原来在 66% 处就铺成 alpha=1.0 的纯纸色，一路到底。
                // 底栏是透的玻璃，背后却是一片纯色 —— 折射位移再大也只是把同一块颜色
                // 挪来挪去，屏幕上永远是"一块白"。这就是液态玻璃一直失败的真正原因。
                // 现在把封顶降到 0.62：内容仍然被压淡（接缝照旧消失），
                // 但轮廓还在，玻璃才有东西可以弯折、透出来。
                Brush.verticalGradient(
                    0.00f to paper.copy(alpha = 0f),
                    0.28f to paper.copy(alpha = 0.22f),
                    0.50f to paper.copy(alpha = 0.42f),
                    0.70f to paper.copy(alpha = 0.55f),
                    1.00f to paper.copy(alpha = 0.62f)
                )
            )
    )
}
