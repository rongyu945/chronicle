package com.rongyu.shixuji.data

import java.math.BigInteger
import java.util.Calendar

/**
 * 账目搜索的纯逻辑（不碰 Room / Compose，可单测）。
 *
 * 用户 2026-11 提出的需求：账目页能按**备注 / 金额 / 标签**搜，并带「本月 / 上月 / 今年 / 只看收入」
 * 快捷筛选。数据量是个人的几千条以内，直接整表取回来在内存里过滤 —— 好处是输入即出结果，
 * 不必为每个关键字拼一条 LIKE SQL，也不会因为写法不同（空格、¥、大小写）漏掉。
 */
object AccountSearch {

    /** 快捷筛选的时间范围；[label] 就是芯片文字 */
    enum class Range(val label: String) {
        ALL("全部"), THIS_MONTH("本月"), LAST_MONTH("上月"), THIS_YEAR("今年")
    }

    /**
     * 收支方向筛选（2026-11 审查 6.5）。
     *
     * 旧版只有「只看收入」一个布尔开关：既筛不出「只看支出」，也没法一眼看出当前处在哪种状态
     * （关掉开关时它和「不限」长得一模一样）。改成三态互斥，[label] 就是胶囊文字。
     * 第一态叫「收支」而不是「全部」——时间范围那头已经有一个「全部」，同一个屏幕上两个
     * 「全部」分属两组筛选，用户点错也看不出错在哪。
     */
    enum class Kind(val label: String) {
        BOTH("收支"), EXPENSE("支出"), INCOME("收入")
    }

    /**
     * 金额转成搜索用的文本："8888.00"。
     * 用 BigInteger 拆商余，`Long.MIN_VALUE` 也不会因为 abs 溢出打出负数。
     */
    fun amountText(cents: Long): String {
        val v = BigInteger.valueOf(cents).abs()
        val parts = v.divideAndRemainder(BigInteger.valueOf(100))
        return "${parts[0]}.${parts[1].toString().padStart(2, '0')}"
    }

    /**
     * 关键字命中：备注 / 标签 / 金额（忽略大小写、`¥`、前后空格）。
     *
     * 金额要两种写法都算命中：`"23.5"`（带小数点）和 `"2350"`（只打数字）。
     * 只比带小数点的 "23.50" 的话，用户输 "2350" 会一条都搜不到 —— 而多数人记账时
     * 只记得整数部分。
     */
    fun matches(a: Account, query: String): Boolean {
        val q = query.trim().removePrefix("¥").trim().lowercase()
        if (q.isEmpty()) return true
        if (a.title.lowercase().contains(q)) return true
        if (a.tag.lowercase().contains(q)) return true
        val amount = amountText(a.amountCents)
        return amount.contains(q) || amount.replace(".", "").contains(q)
    }

    /**
     * 时间范围的上下界（含两端）。null = 不限时间。
     * 用本机时区的自然月，与界面上的「本月」「上月」口径一致。
     */
    fun rangeBounds(range: Range, todayMs: Long): LongRange? {
        if (range == Range.ALL) return null
        val today = Calendar.getInstance().apply { timeInMillis = todayMs }
        val y = today.get(Calendar.YEAR)
        val m = today.get(Calendar.MONTH)

        fun monthStart(yy: Int, mm: Int): Long = Calendar.getInstance().apply {
            clear(); set(yy, mm, 1, 0, 0, 0)
        }.timeInMillis

        fun monthEnd(yy: Int, mm: Int): Long = Calendar.getInstance().apply {
            clear(); set(yy, mm, 1, 0, 0, 0); add(Calendar.MONTH, 1)
        }.timeInMillis - 1L

        return when (range) {
            Range.ALL -> null
            Range.THIS_MONTH -> monthStart(y, m)..monthEnd(y, m)
            Range.LAST_MONTH -> {
                val prev = Calendar.getInstance().apply {
                    clear(); set(y, m, 1, 0, 0, 0); add(Calendar.MONTH, -1)
                }
                val py = prev.get(Calendar.YEAR)
                val pm = prev.get(Calendar.MONTH)
                monthStart(py, pm)..monthEnd(py, pm)
            }
            Range.THIS_YEAR -> monthStart(y, 0)..monthEnd(y, 11)
        }
    }

    /**
     * 收支方向判定。
     *
     * ★ 7.5.5（复核报告 6.1）：原来写成 `(kind == INCOME) == (a.type == 1)` 的一行布尔等式 ——
     *   在 `type` 只有 0/1 时等价，但它把「不是收入」默默等同于「支出」。将来真要加退款/转账，
     *   或者历史数据里混进一个 2，那种行会**看不见也筛不出**（只在「收支」下出现）。
     *   写成显式分支：未知取值仍按支出处理（与旧行为一致，不会突然少数据），
     *   但这是一处看得见的决定，不再是布尔等式的副作用。
     */
    internal fun kindMatches(kind: Kind, type: Int): Boolean = when (kind) {
        Kind.BOTH -> true
        Kind.INCOME -> type == TYPE_INCOME
        Kind.EXPENSE -> type != TYPE_INCOME
    }

    /** 账目类型：0 = 支出，1 = 收入（与 [Account.type] 同一口径） */
    const val TYPE_INCOME = 1

    /**
     * 过滤 + 按时间倒序。
     * @param kind 收支方向：两边都要 / 只看支出 / 只看收入
     * @param tag 指定标签（空串 = 不限）
     */
    fun filter(
        list: List<Account>,
        query: String,
        range: Range = Range.ALL,
        todayMs: Long = System.currentTimeMillis(),
        kind: Kind = Kind.BOTH,
        tag: String = "",
    ): List<Account> {
        val bounds = rangeBounds(range, todayMs)
        return list.filter { a ->
            (bounds == null || a.date in bounds) &&
                kindMatches(kind, a.type) &&
                (tag.isBlank() || a.tag == tag) &&
                matches(a, query)
        }.sortedByDescending { it.date }
    }
}
