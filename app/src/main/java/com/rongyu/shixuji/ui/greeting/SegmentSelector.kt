package com.rongyu.shixuji.ui.greeting

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.rongyu.shixuji.data.GreetingTimes
import com.rongyu.shixuji.theme.isDarkTheme
import com.rongyu.shixuji.ui.drawGlassPill
import com.rongyu.shixuji.ui.motion.Motion
import kotlin.math.roundToInt

/*
 * 段落选择器（7.5.7 重做：胶囊滑块）。
 *
 * 旧版是「三张互不相连的小方卡」，选中项底下画一条 2dp 同色短线。两条评价都来自用户：
 *  「中间的三个时段，请你做成胶囊滑块的样子」「我不需要这个底下有一条这个绿色的红线，很丑」。
 *
 * 现在是一条**槽 + 一块滑动的玻璃胶囊**：
 *  · 外槽：胶囊形（半径 = 高/2），1dp 发丝边，淡淡的凹槽底色；
 *  · 滑块：内缩 3dp 的胶囊，白玻璃（竖向渐变 + 1dp 亮边 + 柔影），
 *    换段时用 Motion.AccentSpring 从左滑到右（带一点过冲，像底栏的指示块）；
 *  · 三格等宽且**首尾相邻**（gap = 0）：滑块宽度恒等于一格，滑动过程不拉伸、不变形；
 *  · 文字画在滑块之上（Row 在 thumb 之后），颜色随选中态过渡：选中加粗 + 墨色；
 *  · 7.5.8 起**上色**：滑块染当前时段的色（浅 0.34 / 深 0.30），选中标题用该色的深压版，
 *    每格前面加一颗 6dp 同色小圆点；未选中的名字仍是次要灰。仍然没有那条短线。
 *
 * 为什么不用 Row + weight(1f)：滑块要按「动画进度 × 格宽」定位，必须知道一格的**像素**宽，
 * 用 BoxWithConstraints 量一次比在 Row 里塞占位更直接，也不会因为文字变宽而错位。
 */

/**
 * 时段色 → 文字色。
 *
 * 原始三段色是「墨线分时」里的柔和色（#BDA477 / #88A99A / #68748A），直接当 11sp 文字用对比度只有
 * 2.2–3.4:1。浅色下压黑 48%、深色下提白 40% 后，实测在凹槽底上 ≥5.9:1、
 * 在染色滑块上 ≥4.9:1（浅）/ ≥4.6:1（深），达标且仍是同一个色相。
 */
private fun segInk(c: Color, dark: Boolean): Color =
    if (dark) lerp(c, Color.White, 0.40f) else lerp(c, Color.Black, 0.48f)

private val TROUGH_H = 44.dp
private val INNER_PAD = 3.dp
private val THUMB_R = TROUGH_H / 2 - INNER_PAD   // 19dp

@Composable
internal fun SegmentSelector(
    times: GreetingTimes,
    selected: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segs = times.segments()
    val dark = isDarkTheme
    val onInk = MaterialTheme.colorScheme.onSurface
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    val hair = onInk.copy(alpha = if (dark) 0.14f else 0.11f)
    // 凹槽比卡片再深一档，滑块才有「浮在槽里」的层次
    val troughBg = if (dark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.035f)
    val haptics = LocalHapticFeedback.current

    // 换段：滑块位移跟底栏指示块同一根弹簧（0.78 / 520，轻微过冲）
    val slide by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = Motion.AccentSpring,
        label = "segSlide"
    )
    val density = LocalDensity.current

    // 签名「槽随段色」：整条凹槽会淡淡地染上当前时段的颜色 —— 换段时槽、滑块、圆点一起变色。
    // 这是问候时段独有的（账目那条槽永远是中性灰白），一眼就能看出「这个控件是按颜色分段的」。
    val troughTint by animateColorAsState(
        targetValue = segs[selected.coerceIn(0, segs.lastIndex)].color,
        animationSpec = Motion.color(300), label = "segTrough"
    )
    val troughFill = if (dark) troughTint.copy(alpha = 0.13f) else troughTint.copy(alpha = 0.09f)
    val troughLine = lerp(hair, troughTint, if (dark) 0.22f else 0.18f)

    BoxWithConstraints(
        modifier.fillMaxWidth().height(TROUGH_H)
            // 只在背景/描边上用胶囊形状，**不 clip 整个 Box**：
            // 滑块贴到最左/最右时，它的柔影会越过槽的内边界，一旦外层 clip 就会被切出一条直边。
            .background(troughBg, RoundedCornerShape(TROUGH_H / 2))
            .background(troughFill, RoundedCornerShape(TROUGH_H / 2))
            .border(1.dp, troughLine, RoundedCornerShape(TROUGH_H / 2))
            .padding(INNER_PAD)
            .selectableGroup()
    ) {
        // 三格等宽、首尾相邻：滑块宽度恒等于一格
        val slotW = maxWidth / 3f
        val slotPx = with(density) { slotW.toPx() }

        // 滑块染「当前选中那段」的色：换段时跟着滑块一起过渡
        val thumbTint by animateColorAsState(
            targetValue = segs[selected.coerceIn(0, segs.lastIndex)].color,
            animationSpec = Motion.color(260), label = "segThumb"
        )

        // ── 滑动的玻璃胶囊 ──────────────────────────────────
        // 材质交给 ui/GlassPill.kt 的统一配方（和分段控件、开关用的是同一套影/光/收边），
        // 这里只交代自己的签名：**滑块染色**（换成哪段就染哪段的色）+ **整条槽跟着换色**（见上面的 troughFill）。
        // streak = false —— 连续流光留给分段控件，两者不能撞签名。
        Box(
            Modifier
                .offset { IntOffset((slotPx * slide).roundToInt(), 0) }
                .width(slotW)
                .fillMaxHeight()
                .drawBehind {
                    drawGlassPill(
                        x = 0f,
                        w = size.width,
                        h = size.height,
                        dark = dark,
                        tint = thumbTint,
                        tintAlpha = if (dark) 0.42f else 0.44f,
                        streak = false
                    )
                }
        )

        // ── 三格文字（画在滑块之上）──────────────────────────
        Row(Modifier.fillMaxWidth().fillMaxHeight().zIndex(1f)) {
            segs.forEachIndexed { i, seg ->
                val on = i == selected
                val ink = segInk(seg.color, dark)
                val label by animateColorAsState(
                    targetValue = if (on) ink else ink2,
                    animationSpec = Motion.color(200), label = "segTxt$i"
                )
                val sub by animateColorAsState(
                    targetValue = if (on) ink.copy(alpha = 0.82f) else ink2.copy(alpha = 0.72f),
                    animationSpec = Motion.color(200), label = "segSub$i"
                )
                val dot by animateColorAsState(
                    targetValue = if (on) seg.color else seg.color.copy(alpha = if (dark) 0.72f else 0.55f),
                    animationSpec = Motion.color(200), label = "segDot$i"
                )
                val dotSize by animateDpAsState(
                    targetValue = if (on) 6.dp else 5.dp,
                    animationSpec = Motion.settleSoftSpec<Dp>(), label = "segDotSz$i"
                )
                Box(
                    Modifier
                        .width(slotW)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(THUMB_R))
                        .selectable(
                            selected = on,
                            role = Role.Tab,
                            onClick = {
                                if (!on) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onPick(i)
                                }
                            }
                        )
                        .semantics {
                            contentDescription =
                                "${seg.title} ${fmtMin(seg.start)} 至 ${fmtMin(seg.end)}" +
                                    if (on) "，当前选中" else ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 宋体默认行高 ≈1.5em，两行加起来 29dp，塞进 38dp 的滑块只剩 4dp 上下留白 ——
                        // 视觉上「字贴着胶囊边」。这里显式把行高压到 1.18em，块高 24dp，上下各 7dp。
                        // 圆点贴在**标题这一行**里（不是两行之间居中），否则一眼看不出它标的是谁。
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(dotSize).clip(CircleShape)
                                    .background(dot)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                seg.title, fontSize = 11.sp, lineHeight = 13.sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                color = label, maxLines = 1, textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            "${fmtMin(seg.start)}–${fmtMin(seg.end)}", fontSize = 8.5.sp,
                            lineHeight = 10.sp, letterSpacing = 0.15.sp,
                            fontFamily = FontFamily.Monospace,
                            color = sub, maxLines = 1, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
