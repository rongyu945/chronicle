package com.rongyu.shixuji.ui.greeting

import com.rongyu.shixuji.ui.GlassCard
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.data.GreetingTimes
import com.rongyu.shixuji.theme.isDarkTheme
import com.rongyu.shixuji.ui.motion.Motion
import kotlin.math.roundToInt

/*
 * 一日时序卡（7.5.0）。
 *
 * 职责只有三个，**不承担精确拖动**（计划书 §4.3）：
 *  ① 看出三段落在一天里的哪里；
 *  ② 看出两段空档存不存在、大概多长；
 *  ③ 点色块切段；点空档只给解释，不强行选一个相邻段。
 *
 * 与旧版 `OverviewAxis` 的差别：段块更高更厚（26dp / 48dp 命中区）、选中态是「上浮 + 加深 +
 * 一圈同色细描边」而不是只改透明度、空档位置画一段虚基线说明「这里什么都没有」。
 */

@Composable
internal fun OverviewTimeline(
    times: GreetingTimes,
    selected: Int,
    onPick: (Int) -> Unit,
    onGapTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segs = times.segments()
    // 空档区间（含「00:00–早安起点」与「晚安终点–24:00」这两段）：7.6.1 起首尾也能调，
    // 所以不能再用 zipWithNext 只算中间那两段。
    val gaps: List<Pair<Int, Int>> = buildList {
        if (segs.firstOrNull()?.start?.let { it > 0 } == true) add(0 to segs.first().start)
        segs.zipWithNext().forEach { (a, b) -> add(a.end to b.start) }
        if (segs.lastOrNull()?.end?.let { it < GreetingTimes.DAY } == true) {
            add(segs.last().end to GreetingTimes.DAY)
        }
    }.filter { it.second > it.first }
    val dark = isDarkTheme
    val onInk = MaterialTheme.colorScheme.onSurface
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    val hair = onInk.copy(alpha = if (dark) 0.14f else 0.11f)
    val tick = onInk.copy(alpha = if (dark) 0.30f else 0.20f)
    val cardBg = if (dark) onInk.copy(alpha = 0.065f)
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val mono = FontFamily.Monospace

    // 换段时色块硬切会「跳」一下：高度 / 上浮 / 浓度统一从这一条进度派生。
    val selProg = segs.mapIndexed { i, _ ->
        animateFloatAsState(
            targetValue = if (i == selected) 1f else 0f,
            animationSpec = Motion.inOut(260),
            label = "segPick$i"
        )
    }

    // 2026-11-11：卡壳换成设置页同一套液态玻璃（GlassCard），内距 12/10/9，
    // 色块 22dp 高 + 半圆角（胶囊），整页纵向收紧。
    GlassCard(modifier) {
        Column(
            Modifier.fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 9.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("一日时序", fontSize = 12.5.sp, color = onInk, modifier = Modifier.weight(1f))
            Text("00:00 — 24:00", fontSize = 10.5.sp, color = ink2, fontFamily = mono)
        }
        Spacer(Modifier.height(8.dp))

        // 左右各留 12dp：首尾两个刻度数字（00 / 24）以刻度线为中心，半宽会溢出被裁。
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Canvas(
                Modifier.fillMaxWidth().height(30.dp)
                    .semantics {
                        contentDescription = buildString {
                            append("一日时序。")
                            segs.forEach { append("${it.title} ${fmtMin(it.start)} 至 ${fmtMin(it.end)}。") }
                            append("点击时段可选择编辑。")
                        }
                    }
                    .pointerInput(segs, gaps) {
                        detectTapGestures { pos ->
                            val m = (pos.x / size.width * GreetingTimes.DAY).roundToInt()
                            val hit = segs.indexOfFirst { m >= it.start && m < it.end }
                            if (hit >= 0) {
                                onPick(hit)
                            } else {
                                // 空档：说清楚为什么，而不是偷偷选一个相邻段
                                val g = gaps.firstOrNull { m >= it.first && m < it.second }
                                onGapTap(
                                    if (g != null) "${fmtMin(g.first)}–${fmtMin(g.second)} 不显示欢迎页"
                                    else "这段时间不显示欢迎页"
                                )
                            }
                        }
                    }
            ) {
                val w = size.width
                // 7.6.3：色块高度 22dp → 14dp → 12dp（用户要的是「胶囊色块细一点」，再窄一点）。
                // 坐标轴回到原样：1dp 实线贯穿整宽（只在色块之外露出来）+ 1dp 刻度（长 8dp / 短 4dp）。
                val barTop = 6.dp.toPx()
                val barH = 12.dp.toPx()
                drawLine(hair, Offset(0f, barTop + barH / 2), Offset(w, barTop + barH / 2), 1.dp.toPx())

                // 刻度：每小时一根，6 小时的长刻度
                for (h in 0..24) {
                    val px = w * h / 24f
                    val long = h % 6 == 0
                    drawLine(
                        tick,
                        Offset(px, barTop + barH + 3.dp.toPx()),
                        Offset(px, barTop + barH + if (long) 8.dp.toPx() else 4.dp.toPx()),
                        1.dp.toPx()
                    )
                }

                segs.forEachIndexed { i, g ->
                    val p = selProg[i].value
                    val x0 = w * g.start / GreetingTimes.DAY
                    val x1 = w * g.end / GreetingTimes.DAY
                    // 选中上浮 3dp
                    val h = barH + 3.dp.toPx() * p
                    val top = barTop - 1.5.dp.toPx() * p
                    // 半圆角 = 胶囊（barH 12dp ⇒ 6dp）；随高度变，选中放大时仍是胶囊
                    val rd = CornerRadius(h / 2)
                    drawRoundRect(
                        color = g.color.copy(alpha = 0.52f + 0.48f * p),
                        topLeft = Offset(x0, top),
                        size = Size(maxOf(3.dp.toPx(), x1 - x0), h),
                        cornerRadius = rd
                    )
                    if (p > 0.01f) {
                        drawRoundRect(
                            color = g.color.copy(alpha = 0.95f * p),
                            topLeft = Offset(x0, top),
                            size = Size(maxOf(3.dp.toPx(), x1 - x0), h),
                            cornerRadius = rd,
                            style = Stroke(1.dp.toPx())
                        )
                    }
                }
            }

            // 刻度数字：9sp 宋体的行盒约 13.5dp，原来给 13dp 高 + 默认行高，
            // 字被上下裁掉半截（看起来像「∩∩」）。这里给足 17dp 并钉死行高。
            BoxWithConstraints(Modifier.fillMaxWidth().height(17.dp)) {
                val wpx = constraints.maxWidth.toFloat()
                val half = 12.dp
                val halfPx = with(LocalDensity.current) { half.toPx() }
                listOf("00", "06", "12", "18", "24").forEachIndexed { i, label ->
                    Text(
                        label, fontSize = 9.sp, lineHeight = 12.sp, color = ink2, fontFamily = mono,
                        textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier
                            .width(half * 2)
                            .offset { IntOffset((wpx * i / 4f - halfPx).roundToInt(), 0) }
                    )
                }
            }
        }

        // 当前段一行摘要（段名 + 区间 + 时长），与下面的编辑器同口径
        val cur = segs[selected.coerceIn(0, segs.lastIndex)]
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier.width(7.dp).height(7.dp)
                    .clip(RoundedCornerShape(4.dp)).background(cur.color)
            )
            Text(
                cur.title, fontSize = 11.5.sp, color = onInk,
                modifier = Modifier.padding(start = 7.dp)
            )
            Text(
                "  ${fmtMin(cur.start)}–${fmtMin(cur.end)} · ${durationText(cur.duration)}",
                fontSize = 10.5.sp, color = ink2, fontFamily = mono
            )
        }
        }
    }
}
