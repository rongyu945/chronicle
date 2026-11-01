package com.rongyu.shixuji.ui

import com.rongyu.shixuji.data.Account
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 金额工具：内部一律用「分」（Long）存储。
 *
 * 关键教训：绝不能用 `(元 * 100).toLong()` 做换算——浮点误差 + 直接截断会让
 * 0.29 变成 28 分、1.15 变成 114 分（用户可见的金额错误）。统一走 BigDecimal。
 */

private val CENT = java.math.BigInteger.valueOf(100)

/**
 * 分 → "¥12.34" / "-¥0.29" / "¥0.00"
 *
 * 两个坑（都是静态审查指出的边界，虽然当前调用链进不来，仍按正确写法兜住）：
 * ① 不能用 `kotlin.math.abs(cents)`：Long.MIN_VALUE 的绝对值仍是负数（补码溢出），
 *    会打印出 "-¥-92233720368547758.08" 这种负的金额主体。改走 BigInteger.abs()。
 * ② 不能用 `"%d.%02d".format(...)` 的默认 Locale：在阿拉伯语等「本地数字形」的语言下
 *    会输出 ٩٢٣ 之类的非 ASCII 数字。显式 Locale.ROOT。
 */
fun formatCents(cents: Long): String {
    val value = java.math.BigInteger.valueOf(cents)
    val sign = if (value.signum() < 0) "-" else ""
    val parts = value.abs().divideAndRemainder(CENT)
    return "$sign¥${parts[0]}.${parts[1].toString().padStart(2, '0')}"
}

/** 分 → 编辑框预填的纯数字串："12.34" */
fun centsToYuanString(cents: Long): String =
    BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

/**
 * 用户输入的「元」→「分」。四舍五入到分、最多两位小数；非法输入或超出上限返回 null。
 * 例："0.29" → 29；"1.15" → 115；"3.145" → 315；"." / "-5" / 乱码 → null
 */
fun parseYuanToCents(input: String): Long? {
    val t = input.trim().removePrefix("¥").replace(",", "").replace(" ", "")
    if (t.isEmpty() || t == "." || t == "-") return null
    return try {
        val bd = BigDecimal(t).setScale(2, RoundingMode.HALF_UP)
        if (bd.signum() < 0) return null
        val cents = bd.movePointRight(2).longValueExact()
        if (cents > 99_999_999_999L) return null
        cents
    } catch (_: Exception) {
        null
    }
}

/**
 * 账目的显示名：优先备注；备注为空（或历史数据里被写成占位符"记账"）时用标签名兜底。
 */
fun accountTitle(a: Account): String {
    val t = a.title.trim()
    if (t.isNotBlank() && t != "记账") return t
    return if (a.tag.isNotBlank()) a.tag else "记账"
}
