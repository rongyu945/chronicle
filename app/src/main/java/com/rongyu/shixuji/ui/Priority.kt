package com.rongyu.shixuji.ui

import androidx.compose.ui.graphics.Color

// ===== 优先级 =====
const val PRIORITY_NONE = 0
const val PRIORITY_LOW = 1
const val PRIORITY_MID = 2
const val PRIORITY_HIGH = 3

fun priorityLabel(p: Int): String = when (p) {
    PRIORITY_HIGH -> "高"
    PRIORITY_MID -> "中"
    PRIORITY_LOW -> "低"
    else -> ""
}

/** 优先级色条颜色（传入当前是否深色，红在深色下提亮） */
fun priorityColor(p: Int, isDark: Boolean): Color = when (p) {
    PRIORITY_HIGH -> if (isDark) Color(0xFFFF6B61) else Color(0xFFFF3B30)
    PRIORITY_MID -> if (isDark) Color(0xFFFFB340) else Color(0xFFFF9F0A)
    PRIORITY_LOW -> if (isDark) Color(0xFF7EA6D8) else Color(0xFF8E8E93)
    else -> Color.Transparent
}