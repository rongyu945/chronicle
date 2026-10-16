package com.rongyu.shixuji.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.theme.isDarkTheme

/** 一个底栏项：标题 + 自绘图标资源 */
data class TabItem(val label: String, val iconRes: Int)

/**
 * 浮动玻璃胶囊底栏。
 *
 * 三个图标是自绘的同一套（24 网格 / 2dp 描边 / 圆头端点），视觉重量一致。
 *
 * 选中态 = **一枚 56dp 的正圆把图标和文字一起包住**（浅红底），切 tab 时用弹簧滑过去。
 * 图标与文字间距 1.5dp —— 文字必须显式给 lineHeight：主题里 bodyLarge 带 24sp 行高，
 * 10.5sp 的字套上 24sp 行框会在上下各留一大截空白，看起来就是"字离图标很远"。
 */
@Composable
fun GlassBottomBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LiquidGlassBackdropState? = null,
) {
    val shape = RoundedCornerShape(34.dp)
    val circleSize = 56.dp
    val barHeight = 64.dp

    // 底栏形状（给液态玻璃的折射用）。外层 BoxWithConstraints 只负责量尺寸，
    // 真正画玻璃的是它里面那层 —— 这样玻璃层的坐标正好等于胶囊矩形。
    // 三图标重构（用户删掉「模型」后要求）：胶囊不再靠左右 34dp 边距拉满整屏，
    // 而是「每项固定 84dp」收紧成一小条居中 —— 三项散在大宽屏上会显得很空。
    // 每项 84dp 与四项时代的 85.75dp 基本一致，图标观感不变，胶囊总宽 252dp。
    val itemWidth = 84.dp
    BoxWithConstraints(
        modifier.height(barHeight)
    ) {
        val barW = itemWidth * items.size
        // ⚠️ 玻璃必须是**独立的一层**，图标/文字画在它外面。
        // 若把图标也画进带 RenderEffect 的那层，图标会被折射+模糊一起糊掉。
        Box(Modifier.width(barW).fillMaxHeight()) {
            if (backdrop != null) {
                Box(
                    Modifier
                        .matchParentSize()
                        .liquidGlass(
                            backdrop = backdrop,
                            shape = shape,
                            refractionHeight = 20.dp,
                            refractionAmount = 22.dp,
                            blurRadius = 10.dp,
                            depthEffect = true,
                            tint = Color.White.copy(alpha = if (isDarkTheme) 0.10f else 0.16f),
                        )
                )
            } else {
                Box(
                    Modifier.matchParentSize().glassSurface(
                        shape, dark = isDarkTheme, ring = true, elevation = 7.dp, clipContent = false
                    )
                )
            }

            Box(Modifier.width(barW).fillMaxHeight().clip(shape)) {
                val itemW = itemWidth

        // 选中圆：整条底栏共用一枚，切 tab 时滑过去（弹簧），而不是每个 tab 各画一枚"跳"过去
        val pillX by animateDpAsState(
            targetValue = itemW * selectedIndex + (itemW - circleSize) / 2,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
            label = "pillX"
        )
        Box(
            Modifier
                .offset(x = pillX, y = (barHeight - circleSize) / 2)
                .size(circleSize)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
        )

        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, item ->
                val selected = i == selectedIndex
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.06f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                    label = "iconScale"
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = item.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                        )
                        Spacer(Modifier.height(1.5.dp))
                        Text(
                            item.label,
                            fontSize = 11.sp,
                            lineHeight = 12.sp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
            }
        }
    }
}
