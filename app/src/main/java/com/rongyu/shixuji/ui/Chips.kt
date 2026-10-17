package com.rongyu.shixuji.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rongyu.shixuji.theme.Accent
import com.rongyu.shixuji.theme.AccentDark
import com.rongyu.shixuji.theme.isDarkTheme

/**
 * 全 App 统一的「选择框」（芯片）。
 *
 * 用户 2026-10-16：「很多选择的框框都是白色边框，可读性有点差，改成和主题色淡红相近的颜色」。
 * 根因：M3 的 FilterChip 未选中时描边取 colorScheme.outline，本 App 主题里那是「白色 72% 透明」，
 * 落在浅色玻璃卡上等于看不见（同理还有勾选框 / 开关 / 单选圈的描边）。
 *
 * 这里统一改成**主题淡红**：未选中 = 淡红细描边（浅色红 42% / 深色亮红 55%），
 * 选中 = 淡红底（primaryContainer）+ 主题红文字。所有调用点都走这个组件，不要再直接用 FilterChip。
 */
@Composable
fun ShixuChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
) {
    val dark = isDarkTheme
    val red = if (dark) AccentDark else Accent
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        // compact：弹窗里用。M3 芯片为了触摸安全会强制占 48dp 高（图案只有 32dp），
        // 弹窗里四五行叠起来就多出一大截空白，这里把占高压回图案本身的 32dp。
        modifier = if (compact) modifier.height(FilterChipDefaults.Height) else modifier,
        leadingIcon = leadingIcon,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
        ),
        border = BorderStroke(
            width = 1.dp,
            // 选中态描边比未选中略深一点，让"已经选上"更明确
            color = red.copy(alpha = if (selected) (if (dark) 0.85f else 0.62f) else (if (dark) 0.55f else 0.42f))
        ),
    )
}
