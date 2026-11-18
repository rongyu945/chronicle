package com.rongyu.shixuji.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rongyu.shixuji.data.GreetingEdge
import com.rongyu.shixuji.data.GreetingTimes
import com.rongyu.shixuji.ui.greeting.EdgeWheelDialog
import com.rongyu.shixuji.ui.greeting.OverviewTimeline
import com.rongyu.shixuji.ui.greeting.SegmentEditor
import com.rongyu.shixuji.ui.greeting.SegmentSelector
import com.rongyu.shixuji.ui.motion.Motion
import kotlinx.coroutines.delay

/*
 * 「问候时段」二级页（2026-11-11 三次重写，7.5.0）。
 *
 * 这次不是调参，是**换架构**（按《时序集_欢迎时间功能重构与交互优化方案》）：
 *
 *  旧版 = 一条只读总览轴 + 一张「段名 + 两个数字 + 放大轴」卡 + 一条三段清单。
 *         三段信息在三处重复（轴、卡、清单），谁都能点、谁都能切，用户得先猜
 *         「我现在改的是哪一段」；总览轴上的色块还会随选中段上浮，看着像能拖。
 *
 *  新版 = 页头一句说明 → 一日时序卡（看分布、点着切段）→ 三段选择器（明确当前段）
 *         → 编辑面板（时间值、局部时间尺、时长/空档、预览）→ 一句生效说明。
 *         三处职责互不重叠：轴只「看 + 切」，选择器只「切」，面板才「改」。
 *
 * 数据侧同样收口：所有边界合法性检查都在 [GreetingTimes.updateEdge] / [GreetingTimes.rangeOf]，
 * 页面、拖动、滚轮三个入口共用一套，不再各写一遍夹取（旧版拖动用 edgeRange、滚轮用 withEdge，
 * 两套口径对不上时，松手后数字会自己跳一下）。
 *
 * 拖动中的值仍然只活在 [SegmentEditor] 内部的 IntState 里，松手才 [onSet] —— 页级状态
 * 在拖动期间一个都不变，这是 7.3.1 就定下的性能地基，不动。
 */

@Composable
fun GreetingTimesPage(
    times: GreetingTimes,
    onSet: (GreetingTimes) -> Unit,
    /**
     * 请求「预览某个时段的欢迎页」。预览必须由 **MainScreen 顶层** 画（见那里的注释）：
     * 放在 Dialog 里会得到一个独立窗口，状态栏那一条永远铺不到渐变（实测残留灰带）。
     */
    onPreview: (Int) -> Unit = {}
) {
    // draft：只装「已提交」的值；拖动中的临时值不出编辑面板。
    var draft by remember(times) { mutableStateOf(times) }
    var sel by remember { mutableIntStateOf(GreetingTimes.GREETING_NOON) }
    var sheetEdge by remember { mutableStateOf<GreetingEdge?>(null) }
    var previewKind by remember { mutableStateOf<Int?>(null) }
    var gapHint by remember { mutableStateOf<String?>(null) }

    val haptics = LocalHapticFeedback.current
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant

    // 空档点击的轻提示：2.4s 自己消失，不打断操作、不需要点掉。
    LaunchedEffect(gapHint) {
        if (gapHint != null) {
            delay(2400)
            gapHint = null
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {

        Text("设定一天中，时序集向你问候的时间。", fontSize = 11.5.sp, color = ink2)
        Spacer(Modifier.height(8.dp))

        // ── 一日时序（只看 + 切段，不许在这里拖）────────────
        OverviewTimeline(
            times = draft,
            selected = sel,
            onPick = { i ->
                if (i != sel) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    sel = i
                }
            },
            onGapTap = { gapHint = it }
        )

        AnimatedVisibility(
            visible = gapHint != null,
            enter = fadeIn(Motion.enter(180)),
            exit = fadeOut(Motion.exit(160))
        ) {
            Text(
                gapHint ?: "",
                fontSize = 10.5.sp, color = ink2, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(9.dp))

        // ── 三段选择器 ──────────────────────────────────────
        SegmentSelector(
            times = draft,
            selected = sel,
            onPick = { sel = it }
        )

        Spacer(Modifier.height(8.dp))

        // ── 编辑面板 ────────────────────────────────────────
        SegmentEditor(
            times = draft,
            selected = sel,
            onCommit = { t ->
                draft = t
                onSet(t)
            },
            onEditValue = { sheetEdge = it },
            onPreview = { previewKind = it }
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "修改后自动生效 · 三段以外的时间直接进主界面",
            fontSize = 10.5.sp, color = ink2, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // ── 精确时间滚轮 ───────────────────────────────────────
    sheetEdge?.let { edge ->
        EdgeWheelDialog(
            times = draft,
            edge = edge,
            onDismiss = { sheetEdge = null },
            onConfirm = { v ->
                val t = draft.updateEdge(edge, v)
                draft = t
                onSet(t)
                sheetEdge = null
            }
        )
    }

    // ── 欢迎页预览（全屏、可返回、不写任何状态）─────────────
    // 预览：只把「看哪一段」抛给 MainScreen，由它铺满整屏地画欢迎页。
    previewKind?.let { kind ->
        LaunchedEffect(kind) {
            onPreview(kind)
            previewKind = null
        }
    }
}
