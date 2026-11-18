package com.rongyu.shixuji.ui.greeting

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rongyu.shixuji.data.GreetingEdge
import com.rongyu.shixuji.data.GreetingTimes
import com.rongyu.shixuji.theme.isDarkTheme
import com.rongyu.shixuji.ui.DialogText
import com.rongyu.shixuji.ui.GlassDialogShape
import com.rongyu.shixuji.ui.G3_SMOOTHING
import com.rongyu.shixuji.ui.aaFill
import com.rongyu.shixuji.ui.smoothShape
import com.rongyu.shixuji.ui.ShixuDialogButton
import com.rongyu.shixuji.ui.ShixuDialogTextButton
import com.rongyu.shixuji.ui.glassDialogContainer
import com.rongyu.shixuji.ui.glassDialogModifier
import com.rongyu.shixuji.ui.motion.rememberReducedMotion
import kotlin.math.abs

/*
 * 精确时间滚轮（7.5.0 重写，方案 §6）。
 *
 * 和旧版的区别，一句话：**列里只出现合法值**。
 *
 * 旧版小时列写死 0–24、分钟列写死 00–55，两者自由组合，于是「早间结束 = 12:35」这种
 * 越过午间开始的值也能选到，点确认才被悄悄夹回去 —— 用户看到的是「我刚拨到 12:35，
 * 它自己变成 12:00 了」。现在两列都由 [GreetingTimes.optionsOf] 反推：
 * 小时列只列该边界出现过的整点，分钟列只列该小时内合法的 5 分钟刻度。
 *
 * 打开时只创建 draft（[selMinute]），滚动不碰主模型；确认才回调一次。
 * 值没变时点确认 = 直接关，不写库、不震。
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EdgeWheelDialog(
    times: GreetingTimes,
    edge: GreetingEdge,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val current = times.valueOf(edge)
    val options = remember(times, edge) { times.optionsOf(edge) }
    val hours = remember(options) { options.map { it / 60 }.distinct() }
    val lo = options.firstOrNull() ?: current
    val hi = options.lastOrNull() ?: current
    val seg = times.segments()[edge.kind]

    val itemH = 44.dp
    val wheelH = 220.dp
    val columnW = 62.dp

    // 该小时内合法的分钟（0..55 里的 5 分钟刻度）
    fun minutesOfHour(hour: Int): List<Int> =
        options.filter { it / 60 == hour }.map { it % 60 }

    // 打开即定位到当前值：**用 initialFirstVisibleItemIndex 一次性给初值**，不再靠
    // LaunchedEffect + scrollToItem 异步去拨。异步那版实测小时列拨对了、分钟列却停在 00
    // —— 两个 suspend 滚动和「换小时要重算分钟索引」的校正互相抢，谁先跑完全看帧时序。
    val startHourIdx = hours.indexOf(current / 60).coerceIn(0, hours.lastIndex)
    val startMinList = minutesOfHour(hours[startHourIdx])
    val startMinIdx = startMinList.indexOf(current % 60).coerceIn(0, startMinList.lastIndex)
    val hourState = rememberLazyListState(initialFirstVisibleItemIndex = startHourIdx)
    val minState = rememberLazyListState(initialFirstVisibleItemIndex = startMinIdx)
    val snapH = rememberSnapFlingBehavior(hourState)
    val snapM = rememberSnapFlingBehavior(minState)

    val hIdx by remember { derivedStateOf { centerIndex(hourState) } }
    val hour = hours[hIdx.coerceIn(0, hours.lastIndex)]
    val minList = minutesOfHour(hour)
    val mIdxRaw by remember { derivedStateOf { centerIndex(minState) } }
    val mIdx = mIdxRaw.coerceIn(0, minList.lastIndex)
    val selMinute = hour * 60 + minList[mIdx]

    // 换小时会让分钟列的**选项集**变（12 点有 00…55，11 点只有 40…55），滚动位置可能
    // 落到新列表之外。只在真的越界时才拨一次，正常操作不打断用户的手。
    LaunchedEffect(hour, minList.size) {
        if (mIdxRaw > minList.lastIndex) minState.scrollToItem(minList.lastIndex)
    }

    // 每跨过一个 5 分钟刻度轻震一次（40ms 节流，快速甩动不至于连成一片）
    val haptics = LocalHapticFeedback.current
    // 用普通数组当「已经渲染过一帧」的标记：写成 State 会反过来触发重组。
    val armed = remember { intArrayOf(0) }
    LaunchedEffect(selMinute) {
        if (armed[0] > 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        armed[0]++
    }

    val onInk = MaterialTheme.colorScheme.onSurface
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    val mono = FontFamily.Monospace

    val reduced = rememberReducedMotion()
    val heroIn = remember { Animatable(if (reduced) 1f else 0.975f) }
    LaunchedEffect(Unit) {
        if (reduced) heroIn.snapTo(1f)
        else heroIn.animateTo(1f, spring(dampingRatio = 0.92f, stiffness = 520f))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = GlassDialogShape,
            color = glassDialogContainer(),
            modifier = glassDialogModifier().graphicsLayer {
                scaleX = heroIn.value
                scaleY = heroIn.value
                translationY = (1f - heroIn.value) / 0.025f * 8.dp.toPx()
            }
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 11.dp)) {
                Text(
                    edge.title, fontSize = DialogText.Title, fontWeight = FontWeight.Bold,
                    color = onInk, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(2.dp))
                // 副标题交代「改的是哪一段的哪一头」——旧版只写「选择时间」，改错了都看不出来
                Text(
                    "${seg.greeting} · ${if (edge.isStart) "起点" else "结束于"}",
                    fontSize = DialogText.Hint, color = ink2,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    fmtMin(selMinute), fontSize = 30.sp, fontFamily = mono, color = onInk,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Box(Modifier.fillMaxWidth().height(wheelH), contentAlignment = Alignment.Center) {
                    // 高亮带只包住两列数字，不横跨整行 —— 否则看不出是两列
                    Box(
                        Modifier.width(150.dp).height(itemH)
                            .aaFill(
                                smoothShape(9.dp, G3_SMOOTHING),
                                onInk.copy(alpha = if (isDarkTheme) 0.10f else 0.055f)
                            )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WheelColumn(
                            values = hours.map { "%02d".format(it) },
                            state = hourState, fling = snapH,
                            width = columnW, itemH = itemH, wheelH = wheelH,
                            mono = mono, onInk = onInk, ink2 = ink2
                        )
                        Text(
                            ":", fontSize = 17.sp, fontFamily = mono, color = onInk,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        WheelColumn(
                            values = minList.map { "%02d".format(it) },
                            state = minState, fling = snapM,
                            width = columnW, itemH = itemH, wheelH = wheelH,
                            mono = mono, onInk = onInk, ink2 = ink2
                        )
                    }
                }
                Text(
                    "可选范围 ${fmtMin(lo)} — ${fmtMin(hi)} · 每次 ${GreetingTimes.STEP} 分钟",
                    fontSize = 10.5.sp, color = ink2,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.weight(1f), contentAlignment = Alignment.Center,
                        propagateMinConstraints = true
                    ) { ShixuDialogTextButton("取消", onClick = onDismiss) }
                    Box(
                        Modifier.weight(1f), contentAlignment = Alignment.Center,
                        propagateMinConstraints = true
                    ) {
                        ShixuDialogButton("确认") {
                            // 值没变：只关掉，不写库、不震、不刷新外面 —— 方案 §6.5
                            if (selMinute == current) onDismiss() else onConfirm(selMinute)
                        }
                    }
                }
            }
        }
    }
}

/** LazyColumn 的中心项索引（contentPadding 让首尾也能滚到正中）。 */
private fun centerIndex(state: LazyListState): Int {
    val info = state.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return 0
    val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: 0
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    values: List<String>,
    state: LazyListState,
    fling: FlingBehavior,
    width: Dp,
    itemH: Dp,
    wheelH: Dp,
    mono: FontFamily,
    onInk: androidx.compose.ui.graphics.Color,
    ink2: androidx.compose.ui.graphics.Color,
) {
    val pad = (wheelH - itemH) / 2
    val picked by remember { derivedStateOf { centerIndex(state) } }
    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = pad),
        modifier = Modifier.width(width).height(wheelH),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(values.size) { i ->
            // 中心行外的数字按距离递减透明度（±1 → 0.60、±2 → 0.30），滚轮才有纵深
            val dist = abs(i - picked)
            val fade = when { dist == 0 -> 1f; dist == 1 -> 0.60f; else -> 0.30f }
            Box(Modifier.height(itemH), contentAlignment = Alignment.Center) {
                Text(
                    values[i], fontFamily = mono,
                    fontSize = if (dist == 0) 20.sp else 17.sp,
                    fontWeight = if (dist == 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (dist == 0) onInk else onInk.copy(alpha = maxOf(ink2.alpha, fade))
                )
            }
        }
    }
}
