package com.rongyu.shixuji.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 日期步进器里的 ‹ / › 按钮（待办、账目两个编辑弹窗共用）。
 *
 * 2026-11-01：内部实现从「Text("‹") + 手工 offset 补偿」换成**画出来的折线**（见 [Chevron]）。
 * 用字形时，墨迹贴在基线一侧、行框却上下对称，箭头看着永远偏下；
 * 之前靠 `offset(y = -2.4.dp)` 硬掰，字号一改或换字体就不准了。画线没有这个问题。
 */
@Composable
fun StepperArrow(
    dir: ChevronDir,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    // ★ onClick 会随每次重组换新（它捕获的是当前的 dueMs/Cal），但 pointerInput 只按
    //   enabled 作 key —— 手势检测器不会重建，闭包里若直接调 onClick 就会一直用**第一次**
    //   那个 lambda（捕获的是旧日期）。症状：日期只能改一天，之后怎么点都停在「初始+1」。
    //   用 rememberUpdatedState 让手势里永远调到最新的那个。
    val latestOnClick by rememberUpdatedState(onClick)
    Box(
        Modifier.size(25.dp).clip(RoundedCornerShape(8.dp))
            // 同 ChevronButton：Modifier.clickable 会被 TouchBoundsExpansion 撑到 48dp，
            // 而这里两侧都紧贴日期文字，撑大后点日期会误触翻页。pointerInput 让热区严格等于 25dp。
            .pointerInput(enabled) { if (enabled) detectTapGestures(onTap = { latestOnClick() }) }
            .semantics {
                role = Role.Button
                if (enabled) onClick { latestOnClick(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        Chevron(
            dir = dir,
            // 日期文字紧贴在箭头右侧，把墨迹往文字那边挪 3dp，点击区保持 25dp 不动
            modifier = Modifier.offset(x = if (dir == ChevronDir.Left) 3.dp else (-5).dp),
            size = 12.5.dp,
            stroke = 1.3.dp,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/** 兼容旧调用点：直接给字形字符串也能用（"‹" → 左，"›" → 右） */
@Composable
fun StepperArrow(glyph: String, enabled: Boolean = true, onClick: () -> Unit) {
    StepperArrow(if (glyph == "‹") ChevronDir.Left else ChevronDir.Right, enabled, onClick)
}
