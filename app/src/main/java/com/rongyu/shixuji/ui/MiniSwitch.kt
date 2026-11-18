package com.rongyu.shixuji.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rongyu.shixuji.theme.isDarkTheme
import com.rongyu.shixuji.ui.motion.rememberReducedMotion
import com.rongyu.shixuji.ui.motion.Motion

/**
 * 自绘小开关（设置页「使用宋体」那行用）。
 *
 * M3 的 `Switch` 是按 52×32dp 画死的，塞在 15sp 的设置行里比文字大出整整一圈
 * （用户 2026-11-05：「那个使用宋体的开关非常大，改得跟其他东西一样尺寸」）。
 * 直接 `Modifier.scale()` 能把视觉缩下去，但**布局占位还是 32dp 高**——
 * 那一行会比上下行高出 14dp，看着像空了半行。
 *
 * 这里按设置页自己的刻度重画：轨道 38×22dp（和一级胶囊 26dp 同一档），
 * 滑块 18dp、左右各留 2dp，行高就回到 7dp 内边距 + 22dp = 36dp，和 NavRow 齐平。
 * 交互语义照旧（Role.Switch + toggleable，点哪一行都行由调用方决定）。
 *
 * 2026-11-06 跟着底栏 / 分段控件一起升级成同一套"会动的东西"：
 *   · 轨道颜色不再是开关一拨就换色，用 spring 追过去（和药丸、底栏胶囊同一个家族）；
 *   · 滑块用 spring(0.62 / 700) 滑，落位时有一点点过冲，是"拨过去"而不是"跳过去"；
 *   · 滑块不再是死白圆片：上缘受光 + 下缘一点点阴影，所以它看起来是**浮在轨道上面**；
 *   · 按住的时候滑块微微鼓起来（18 → 19.2dp），给手指一个"抓到了"的确认。
 */
@Composable
fun MiniSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackW = 38.dp
    val trackH = 22.dp
    val thumb = 18.dp
    val pad = 2.dp

    val dark = isDarkTheme
    val reduced = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val onColor = MaterialTheme.colorScheme.primary
    // 2026-11-10 深浅巡检：关闭态轨道在浅色卡上只有 1.01:1（实测与卡底差 5 级），等于没画。
    // 浅色 0.16 → 0.30、深色 0.20 → 0.26。
    val offColor = if (dark) Color.White.copy(alpha = 0.26f)
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
    val trackColor by animateColorAsState(
        targetValue = if (checked) onColor else offColor,
        animationSpec = if (reduced) tween(0) else tween(215, easing = Motion.ColorSettle),
        label = "miniSwitchTrack"
    )
    val x by animateDpAsState(
        targetValue = if (checked) trackW - thumb - pad else pad,
        // 减弱动态效果：不要过冲，直接换位置
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = 0.85f, stiffness = 430f),
        label = "miniSwitchThumb"
    )
    // 按住时鼓起来；松手回原尺寸
    val bulge by animateFloatAsState(
        targetValue = if (pressed) 1.0f else 0f,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 620f),
        label = "miniSwitchBulge"
    )

    Box(
        modifier
            .size(width = trackW, height = trackH)
            .clip(CircleShape)
            .background(trackColor)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(it)
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(thumb)
                .drawBehind {
                    // 统一走 drawGlassKnob（见 GlassKnob.kt）：高光裁在圆里、描边最后画，
                    // 所以剪影永远是正圆，不会再出现「顶上一块白把圆撑变形」。
                    val r = size.minDimension / 2f + 0.6.dp.toPx() * bulge
                    drawGlassKnob(
                        center = center,
                        radius = r,
                        dark = dark,
                        rimColor = if (dark) Color.White.copy(alpha = 0.22f)
                                   else Color.White.copy(alpha = 0.70f),
                        rimWidth = 0.8.dp.toPx(),
                        shadowScale = 1f + bulge * 0.6f
                    )
                }
        )
    }
}
