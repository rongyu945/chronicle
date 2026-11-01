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
    PRIORITY_HIGH -> if (isDark) Color(0xFFF05654) else Color(0xFFED5736)   // 银红 / 妃色
    PRIORITY_MID -> if (isDark) Color(0xFFFFC773) else Color(0xFFFFA631)    // 姜黄 / 杏黄
    PRIORITY_LOW -> if (isDark) Color(0xFF7397AB) else Color(0xFF75878A)    // 苍青 / 苍色
    else -> Color.Transparent
}