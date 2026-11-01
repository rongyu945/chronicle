package com.rongyu.shixuji.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    Box(
        Modifier.size(25.dp).clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() },
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
