package com.rongyu.shixuji.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.rongyu.shixuji.ui.motion.rememberReducedMotion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.theme.isDarkTheme
import kotlin.math.abs
import com.rongyu.shixuji.ui.motion.Motion

/** 分段控件里的一个选项。[count] 不为空时小一号跟在文字后面（如「未完成 21」）。 */
internal data class SegOption(val label: String, val count: Int? = null)

/**
 * 玻璃分段控件 —— 一条胶囊形的槽，里面**一块玻璃药丸滑块**在选项之间滑。
 *
 * 2026-11-05 用户从九个方案里挑的第 ② 个（槽 + 浮起的药丸）。
 * 2026-11-06 用户：「既然底栏已经做得如此精巧，其他有类似滑块结构的界面也做成类似效果」——
 * 于是药丸从「每个选项各自一块死背景」改成**一块共享的、会滑的玻璃**，和底栏那颗红胶囊同一套读法：
 *
 *   · **会滑**：位置用 Animatable 带过去。相邻一格用弹簧（dampingRatio 0.58 / stiffness 560，
 *     带一点回弹，灵动）；隔一格以上改用 tween 且时长随距离增长 —— 弹簧在长距离下初速太大，
 *     看起来是「瞬移」而不是「滑过去」（这条结论是从底栏那版直接搬过来的）。
 *   · **有玻璃质感**：顶缘受光 → 底缘压深 → 一圈 1dp 亮边，和底栏胶囊、弹窗玻璃同一套画法。
 *   · **跑起来会被"甩"出流光**：读 Animatable.velocity，往右跑时高光落在左半边（光被甩在身后），
 *     停下时速度归零、流光回到正中只剩一点点。
 *   · **文字跟着平滑过渡**：粗细和颜色都用动画，不是到了位置才"啪"地变。
 *
 * 药丸是画在 Row 的 drawBehind 里的（槽底 → 药丸 → 高光），文字在上层，
 * 所以不需要在布局上做任何特殊处理，等宽选项直接按容器宽度算格子即可。
 */
@Composable
internal fun ShixuSegmented(
    options: List<SegOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 药丸染色（2026-11-11 用户：「这两个是相同颜色的滑块容易看混，请你加点颜色」）。
     *
     * 一条屏上并排两个分段控件时，两边都是白药丸会分不清手指落在哪条上。
     * 给了颜色就往药丸里掺一层这个色：体色仍是白玻璃（文字保持墨色，对比度不打折），
     * 只是带上一层色调 + 选中的字也换成这个色，一眼能认出「这条是管什么的」。
     * null = 原样的白药丸（时间范围那条）。
     */
    pillTint: Color? = null,
) {
    if (options.isEmpty()) return
    val dark = isDarkTheme
    val shape = CircleShape
    val accent = MaterialTheme.colorScheme.primary

    // 槽底：浅色是半透明白（能透出背后的色斑）；深色反过来——压一层黑，做成**凹槽**。
    // 2026-11-05 用户截图：「今年」上方那一行很割裂。原先是白 7% + onSurfaceVariant 18% 描边，
    // 在深色纸上等于一块**比背景更亮、还带一圈亮边**的板子，边框顶边就是那道割裂的线。
    // 现在：黑 26% 的槽（比背景更暗 = 陷进去）+ 白 5% 的极淡内描边，界限靠明度差、不靠亮线。
    val trackBg = if (dark) Color.Black.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.55f)
    val trackBorder = if (dark) Color.White.copy(alpha = 0.05f)
                      else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)

    val padDp = 3.dp
    val gapDp = 2.dp
    val padPx = with(LocalDensity.current) { padDp.toPx() }
    val gapPx = with(LocalDensity.current) { gapDp.toPx() }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(trackBg, shape)
            .border(1.dp, trackBorder, shape)
            .padding(padDp)
    ) {
        val n = options.size
        val totalPx = constraints.maxWidth.toFloat()
        val itemW = ((totalPx - gapPx * (n - 1)) / n).coerceAtLeast(1f)
        val targetX = selected.coerceIn(0, n - 1) * (itemW + gapPx)

        // 首次出现直接落位：不要从第 0 格滑过来（那一下很像"加载中"）
        var first by remember { mutableStateOf(true) }
        val lensX = remember { Animatable(if (first) targetX else 0f) }
        // 系统开了「减弱动态效果」就不滑，直接到位（Apple §14：弹簧换成静态变化）
        val reduced = rememberReducedMotion()
        val haptics = LocalHapticFeedback.current
        LaunchedEffect(targetX, itemW, reduced) {
            if (first) { first = false; lensX.snapTo(targetX); return@LaunchedEffect }
            if (reduced) {
                lensX.snapTo(targetX)
                return@LaunchedEffect
            }
            val gaps = abs(targetX - lensX.value) / (itemW + gapPx).coerceAtLeast(1f)
            val spec = if (gaps <= 1.05f)
                Motion.AccentSpring
            else
                tween(
                    durationMillis = (260f + 100f * (gaps - 1f)).toInt().coerceAtMost(380),
                    easing = Motion.Glide
                )
            lensX.animateTo(targetX, spec)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .drawBehind {
                    // 8.0.0 起改调 ui/GlassPill.kt 的统一配方（材质在那边，签名留在调用点）。
                    // 分段控件的签名 = 「跟手流光」：streak = true，跑起来光被甩在身后、停下归中。
                    drawGlassPill(
                        x = lensX.value,
                        w = itemW.coerceAtMost(size.width),
                        h = size.height,
                        dark = dark,
                        tint = pillTint,
                        velocity = lensX.velocity,
                        streak = true
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(gapDp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { i, o ->
                val sel = i == selected
                val press = remember { MutableInteractionSource() }
                // 文字跟着药丸一起过渡，不是到了位置才"啪"地变
                val bold by animateFloatAsState(if (sel) 1f else 0f, tween(210), label = "segBold$i")
                Box(
                    Modifier
                        .width(with(LocalDensity.current) { itemW.toDp() })
                        .fillMaxWidth()
                        .clickable(interactionSource = press, indication = null) {
                            // 震动必须跟手指走：在落位那帧才震，药丸已经滑过去 200~360ms，
                            // 手感就是「慢半拍」。这里按下即震，和药丸起步同一拍。
                            if (i != selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            o.label,
                            fontSize = 12.5.sp,
                            fontWeight = if (bold > 0.5f) FontWeight.Bold else FontWeight.Normal,
                            color = androidx.compose.ui.graphics.lerp(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                // 染色的药丸上，选中的字也换成那个色调 —— 但要按底子的明暗调亮度：
                                // 深色模式里 #B7473A 直接压在暗红药丸上只有 1.4:1，几乎看不见，
                                // 所以深色往白里掺 62%、浅色往黑里掺 12%，两边都落在 4:1 上下。
                                pillTint?.let {
                                    if (dark) androidx.compose.ui.graphics.lerp(it, Color.White, 0.78f)
                                    else androidx.compose.ui.graphics.lerp(it, Color.Black, 0.12f)
                                } ?: MaterialTheme.colorScheme.onBackground,
                                bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (o.count != null) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                o.count.toString(),
                                fontSize = 9.5.sp,
                                fontWeight = if (bold > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                                color = androidx.compose.ui.graphics.lerp(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    accent,
                                    bold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
