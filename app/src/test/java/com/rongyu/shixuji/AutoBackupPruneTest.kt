package com.rongyu.shixuji

import com.rongyu.shixuji.data.AutoBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 每日快照裁剪的回归测试（2026-11-08 审计发现的事故）。
 *
 * 事故现场：`pruneDaily` 用 `LIKE '时序集_自动备份_%'` 找每日快照，
 * 但 SQL 里的 `_` 是「任意一个字符」的通配符 —— `时序集_自动备份.json`（最新那份）
 * 也命中；又因为 `'.'(0x2E) < '_'(0x5F)`，按名字排序它排最前，
 * 于是攒满 10 份每日快照后，`dropLast(10)` 每次删掉的正好是它：
 * 写完最新备份 → 立刻被删，设置页还在说「这是最新的一份」。
 *
 * 这里不碰 MediaStore，只测「该删哪些」的决策。
 */
class AutoBackupPruneTest {

    private val latest = "时序集_自动备份.json"

    private fun daily(days: List<String>): List<Pair<String, Long>> =
        days.mapIndexed { i, d -> "时序集_自动备份_$d.json" to (i + 1).toLong() }

    @Test
    fun 最新那份额外传入也不会被删() {
        val names = daily((1..10).map { "2026-11-%02d".format(it) }) + (latest to 99L)
        val del = AutoBackup.dailyToDelete(names, keep = 10, latest = latest)
        assertEquals(emptyList<Long>(), del)
    }

    @Test
    fun 超出保留份数时从最早的开始删_且绝不删最新那份() {
        // 12 份每日 + 最新 1 份，保留 10 → 删最早 2 份
        val names = daily((1..12).map { "2026-11-%02d".format(it) }) + (latest to 99L)
        val del = AutoBackup.dailyToDelete(names, keep = 10, latest = latest)
        assertEquals(listOf(1L, 2L), del)
        assertFalse(99L in del)
    }

    @Test
    fun 万一上游把最新那份也查出来_仍然硬性排除() {
        val names = daily(listOf("2026-11-01")) + (latest to 7L)
        val del = AutoBackup.dailyToDelete(names, keep = 0, latest = latest)
        assertEquals(listOf(1L), del)
    }

    @Test
    fun 只有最新那份时什么都不删() {
        assertEquals(emptyList<Long>(), AutoBackup.dailyToDelete(listOf(latest to 1L), 10, latest))
    }

    @Test
    fun 换名规则_保留扩展名且不与系统去重写法冲突() {
        assertEquals("时序集_自动备份(2).json", AutoBackup.bumpName("时序集_自动备份.json", 2))
        assertEquals("时序集_自动备份(6).json", AutoBackup.bumpName("时序集_自动备份.json", 6))
        // 传进来没有 .json 后缀时（当前调用方不会这样传），仍然产出一个 .json 名字，不会拼出 "x(3)"
        assertEquals("x(3).json", AutoBackup.bumpName("x", 3))
    }

    /**
     * 7.5.5（复核报告 2.4）：两份名字必须都能推出来，且**都不等于正式名**——
     * 两阶段提交靠它们把「旧那份」和「新那份」同时留在目录里。
     */
    @Test fun `临时名与留证名互不相同且都带 json 后缀`() {
        val target = "时序集_自动备份.json"
        val part = AutoBackup.partNameOf(target)
        val prev = AutoBackup.prevNameOf(target)
        assertEquals("时序集_自动备份.part.json", part)
        assertEquals("时序集_自动备份.上一版.json", prev)
        assertFalse(part == target || prev == target || part == prev)
        // 换名后的目标（重装 uid 变了的情况）同样推得出来
        assertEquals("时序集_自动备份(2).上一版.json", AutoBackup.prevNameOf("时序集_自动备份(2).json"))
    }
}
