package com.rongyu.shixuji.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * 点击热区**严格等于布局尺寸**的可点修饰符。
 *
 * 为什么不直接用 `Modifier.clickable`：
 * Compose 会给可点节点套一层 TouchBoundsExpansion，把小于系统最小触摸目标（48dp≈126px）
 * 的节点在命中测试里撑到 48dp。这一层在 View 级别，Material3 的
 * `LocalMinimumInteractiveComponentSize` 管不到它（置空后 uiautomator 仍报 126×123）。
 *
 * 后果是「视觉上分开、逻辑上粘在一起」：
 *  · 表头 ‹ 7日 › 的两个箭头 25dp，撑成 48dp 后中间只剩 9px，日期落进缝里，点日期变成翻页；
 *  · 色板 28dp 相邻 5dp，撑成 48dp 后热区重叠 43dp，点一个色会选中隔壁；
 *  · 上移/下移两颗 32dp 紧贴，撑开后互相盖住。
 *
 * 换成 pointerInput 自绘命中区后，热区严格等于 `Modifier.size(...)`。
 * 代价：触摸目标低于 M3 建议的 48dp，无障碍精度下降 —— 视觉精度优先时接受。
 * 语义上仍暴露 Button 角色与 onClick 动作，TalkBack 照常可用。
 */
fun Modifier.tapNoExpand(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this
        .pointerInput(enabled) {
            if (enabled) detectTapGestures(onTap = { onClick() })
        }
        .semantics {
            role = Role.Button
            if (enabled) onClick { onClick(); true }
        }
