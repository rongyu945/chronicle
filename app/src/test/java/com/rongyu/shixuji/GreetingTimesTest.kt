package com.rongyu.shixuji

import com.rongyu.shixuji.data.GreetingEdge
import com.rongyu.shixuji.data.GreetingTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 问候时段边界模型的回归测试（7.2.5）。
 *
 * 这几条是欢迎页「什么时候出现」的唯一判据，错了的表现是「整点那一刻两个问候都出来」
 * 或「某一段时间欢迎页再也不出现」，肉眼很难一次试出来，所以钉成单测：
 *  ① 左闭右开：起点那一分钟算进来、终点那一分钟不算；
 *  ② 三段之间是空档，空档返回 null（直接进主界面）；
 *  ③ 相邻段允许贴合，重叠/反向必须被 sanitized() 打回默认值。
 */
class GreetingTimesTest {

    private val t = GreetingTimes()   // 0–7 早 / 12–13 午 / 21–24 晚

    @Test
    fun morningIsLeftClosedRightOpen() {
        assertEquals(GreetingTimes.GREETING_MORNING, t.greetingAt(0))
        assertEquals(GreetingTimes.GREETING_MORNING, t.greetingAt(419))
        // 07:00 整已经不是早上了
        assertNull(t.greetingAt(420))
    }

    @Test
    fun noonIsOneHourWindow() {
        assertEquals(GreetingTimes.GREETING_NOON, t.greetingAt(720))
        assertEquals(GreetingTimes.GREETING_NOON, t.greetingAt(779))
        assertNull(t.greetingAt(780))
    }

    @Test
    fun nightRunsToEndOfDay() {
        assertEquals(GreetingTimes.GREETING_NIGHT, t.greetingAt(1260))
        // 1439 是当天最后一分钟；1440 不存在，但传进来要按最后一分钟算
        assertEquals(GreetingTimes.GREETING_NIGHT, t.greetingAt(1439))
        assertEquals(GreetingTimes.GREETING_NIGHT, t.greetingAt(1440))
    }

    @Test
    fun gapsReturnNull() {
        assertNull(t.greetingAt(600))    // 10:00
        assertNull(t.greetingAt(1080))   // 18:00
    }

    @Test
    fun adjacentSegmentsMayTouch() {
        // 早安 0–12、午安 12–13：中间不留空隙是合法设置
        val touching = GreetingTimes(morningEnd = 720, noonStart = 720, noonEnd = 780, nightStart = 1260)
        assertTrue(touching.isValid())
        assertEquals(GreetingTimes.GREETING_MORNING, touching.greetingAt(719))
        assertEquals(GreetingTimes.GREETING_NOON, touching.greetingAt(720))
    }

    @Test
    fun overlapAndReverseAreRejected() {
        // 午安越过晚安起点
        assertFalse(GreetingTimes(morningEnd = 420, noonStart = 720, noonEnd = 1300, nightStart = 1260).isValid())
        // 午安反向
        assertFalse(GreetingTimes(morningEnd = 420, noonStart = 780, noonEnd = 720, nightStart = 1260).isValid())
        // 早安越过午安起点
        assertFalse(GreetingTimes(morningEnd = 800, noonStart = 720, noonEnd = 780, nightStart = 1260).isValid())
        // 空段
        assertFalse(GreetingTimes(morningEnd = 0, noonStart = 720, noonEnd = 780, nightStart = 1260).isValid())
    }

    @Test
    fun sanitizedFallsBackToDefaultOnBadValues() {
        val bad = GreetingTimes(morningEnd = 420, noonStart = 720, noonEnd = 1300, nightStart = 1260)
        assertEquals(GreetingTimes.DEFAULT, bad.sanitized())
    }

    @Test
    fun sanitizedSnapsToStep() {
        val odd = GreetingTimes(morningEnd = 423, noonStart = 721, noonEnd = 783, nightStart = 1262)
        val fixed = odd.sanitized()
        assertTrue(fixed.isValid())
        assertEquals(0, fixed.morningEnd % GreetingTimes.STEP)
        assertEquals(0, fixed.noonStart % GreetingTimes.STEP)
        assertEquals(0, fixed.noonEnd % GreetingTimes.STEP)
        assertEquals(0, fixed.nightStart % GreetingTimes.STEP)
    }

    @Test
    fun morningStartIsAdjustable() {
        // 7.6.1 起早安起点可调（原来钉死 00:00）。正文口径：左闭右开 [start, end)
        val t = GreetingTimes(morningStart = 60, morningEnd = 480)
        assertNull("00:59 还没开始", t.greetingAt(59))
        assertEquals(GreetingTimes.GREETING_MORNING, t.greetingAt(60))
        assertEquals(GreetingTimes.GREETING_MORNING, t.greetingAt(479))
        assertNull("08:00 整已经不是早安", t.greetingAt(480))
        // 起点之前的空档也要能被 draw 层正确识别（gapBefore 就是给一日时序用的）
        assertEquals(60, t.gapBefore(GreetingTimes.GREETING_MORNING))
    }

    @Test
    fun nightEndIsAdjustable() {
        // 7.6.1 起晚安终点可调（原来钉死 24:00）
        val t = GreetingTimes(nightStart = 1260, nightEnd = 1380)
        assertEquals(GreetingTimes.GREETING_NIGHT, t.greetingAt(1379))
        assertNull("23:00 整已经不是晚安", t.greetingAt(1380))
        assertNull("夜里剩下的空档直接进主界面", t.greetingAt(1439))
        assertEquals(60, t.gapAfter(GreetingTimes.GREETING_NIGHT))   // 1440 - 1380
        assertTrue(t.isValid())
    }

    @Test
    fun fromPrefsFallsBackWhenKeysAreMissing() {
        // 老库升级上来只有 dark / songti 几个键，四个时段键全缺 → 必须回到默认，
        // 否则时段会读成 0，欢迎页整天都不出现
        assertEquals(GreetingTimes.DEFAULT, GreetingTimes.fromPrefs(mapOf("dark" to "1")))
        assertEquals(GreetingTimes.DEFAULT, GreetingTimes.fromPrefs(null))
    }

    @Test
    fun prefsRoundTrip() {
        val src = GreetingTimes(morningEnd = 480, noonStart = 690, noonEnd = 795, nightStart = 1290)
        assertEquals(src, GreetingTimes.fromPrefs(GreetingTimes.toPrefs(src)))
    }

    // ─── 7.5.0：边界编辑收口到数据层 ─────────────────────────
    // 拖动、滚轮、入口行三个入口都只调 updateEdge。这组测试盯的就是
    // 「不管外部传什么，结果一定合法、一定落在 5 分钟格上、只动一个字段」。

    private val edges = listOf(
        GreetingEdge.MORNING_START, GreetingEdge.MORNING_END, GreetingEdge.NOON_START,
        GreetingEdge.NOON_END, GreetingEdge.NIGHT_START, GreetingEdge.NIGHT_END
    )

    @Test
    fun updateEdgeClampsToLegalRange() {
        // 早间结束不能越过午间开始、也不能把早安压到 5 分钟以下
        assertEquals(t.noonStart, t.updateEdge(GreetingEdge.MORNING_END, 5000).morningEnd)
        assertEquals(GreetingTimes.STEP, t.updateEdge(GreetingEdge.MORNING_END, -30).morningEnd)
        // 午间开始不能早于早安结束、也不能吃掉午间自身
        assertEquals(t.morningEnd, t.updateEdge(GreetingEdge.NOON_START, 0).noonStart)
        assertEquals(t.noonEnd - GreetingTimes.STEP, t.updateEdge(GreetingEdge.NOON_START, 2000).noonStart)
        // 午间结束夹在「午间至少 5 分钟」与「夜间开始」之间
        assertEquals(t.noonStart + GreetingTimes.STEP, t.updateEdge(GreetingEdge.NOON_END, 0).noonEnd)
        assertEquals(t.nightStart, t.updateEdge(GreetingEdge.NOON_END, 2000).noonEnd)
        // 夜间开始最晚只能到 24:00 前 5 分钟，最早不能早于午间结束
        assertEquals(GreetingTimes.DAY - GreetingTimes.STEP, t.updateEdge(GreetingEdge.NIGHT_START, 5000).nightStart)
        assertEquals(t.noonEnd, t.updateEdge(GreetingEdge.NIGHT_START, 0).nightStart)
    }

    @Test
    fun updateEdgeSnapsToFiveMinutesAndTouchesOneFieldOnly() {
        // 727 落在 725 / 730 正中间偏下一格：统一四舍五入 → 725
        val a = t.updateEdge(GreetingEdge.NOON_START, 727)
        assertEquals(725, a.noonStart)
        assertEquals(t.morningEnd, a.morningEnd)
        assertEquals(t.noonEnd, a.noonEnd)
        assertEquals(t.nightStart, a.nightStart)
        // 每个结果都要仍然是合法整体（不能出现「夹完反而反向」）
        edges.forEach { e ->
            assertTrue(t.updateEdge(e, 1).isValid())
            assertTrue(t.updateEdge(e, GreetingTimes.DAY - 1).isValid())
        }
    }

    @Test
    fun optionsOnlyContainLegalSteppedValues() {
        edges.forEach { e ->
            val opts = t.optionsOf(e)
            val range = t.rangeOf(e)
            assertTrue("$e 选项不应为空", opts.isNotEmpty())
            opts.forEach { v ->
                assertEquals("$e 的 $v 不在 5 分钟格上", 0, v % GreetingTimes.STEP)
                assertTrue("$e 的 $v 越界 $range", v in range)
            }
            // 当前值一定在选项里，滚轮打开时才能定位到正中
            assertTrue("$e 当前值不在选项里", t.valueOf(e) in opts)
            // 升序、无重复
            assertEquals(opts.sorted().distinct(), opts)
        }
    }

    @Test
    fun optionsEqualUpdateEdgeOutputs() {
        // 滚轮只允许选到「updateEdge 不会改写」的值 —— 这是两套口径一致的关键
        edges.forEach { e ->
            t.optionsOf(e).forEach { v ->
                assertEquals(v, t.updateEdge(e, v).valueOf(e))
            }
        }
    }

    @Test
    fun everyEndpointHasAnEdgeNow() {
        // 7.6.1：八个端点全部可拖 —— 早安起点与晚安终点不再是 null
        assertEquals(GreetingEdge.MORNING_START, GreetingEdge.of(GreetingTimes.GREETING_MORNING, true))
        assertEquals(GreetingEdge.MORNING_END, GreetingEdge.of(GreetingTimes.GREETING_MORNING, false))
        assertEquals(GreetingEdge.NOON_START, GreetingEdge.of(GreetingTimes.GREETING_NOON, true))
        assertEquals(GreetingEdge.NOON_END, GreetingEdge.of(GreetingTimes.GREETING_NOON, false))
        assertEquals(GreetingEdge.NIGHT_START, GreetingEdge.of(GreetingTimes.GREETING_NIGHT, true))
        assertEquals(GreetingEdge.NIGHT_END, GreetingEdge.of(GreetingTimes.GREETING_NIGHT, false))
        assertTrue(edges.all { it.editable })
    }

    @Test
    fun morningStartAndNightEndAreEditable() {
        // 早安起点往后挪：00:00–06:00 变成「不显示欢迎页」
        val late = GreetingTimes.DEFAULT.updateEdge(GreetingEdge.MORNING_START, 360)
        assertEquals(360, late.morningStart)
        assertNull(late.greetingAt(0))
        assertEquals(GreetingTimes.GREETING_MORNING, late.greetingAt(360))
        // 晚安终点往前挪：23:00 之后不再显示晚安
        val early = GreetingTimes.DEFAULT.updateEdge(GreetingEdge.NIGHT_END, 1380)
        assertEquals(1380, early.nightEnd)
        assertNull(early.greetingAt(1380))
        assertEquals(GreetingTimes.GREETING_NIGHT, early.greetingAt(1379))
        // 两头都能收，长度与空档跟着变
        assertEquals(60, late.lengthOf(GreetingTimes.GREETING_MORNING))   // 06:00–07:00
        assertEquals(360, late.gapBefore(GreetingTimes.GREETING_MORNING))  // 00:00–06:00 空白
        assertEquals(120, early.lengthOf(GreetingTimes.GREETING_NIGHT))    // 21:00–23:00 = 120 分钟
    }

    @Test
    fun firstAndLastEdgeRespectNeighbours() {
        // 早安起点不能越过早安结束前 5 分钟
        assertEquals(t.morningEnd - GreetingTimes.STEP, t.updateEdge(GreetingEdge.MORNING_START, 5000).morningStart)
        assertEquals(0, t.updateEdge(GreetingEdge.MORNING_START, -30).morningStart)
        // 晚安终点不能吃掉晚安最后 5 分钟，也不能超过 24:00
        assertEquals(t.nightStart + GreetingTimes.STEP, t.updateEdge(GreetingEdge.NIGHT_END, 0).nightEnd)
        assertEquals(GreetingTimes.DAY, t.updateEdge(GreetingEdge.NIGHT_END, 5000).nightEnd)
    }

    @Test
    fun snapToStepRoundsHalfUp() {
        assertEquals(420, GreetingTimes.snapToStep(422))
        assertEquals(420, GreetingTimes.snapToStep(422))            // 422 更靠 420
        assertEquals(425, GreetingTimes.snapToStep(427))            // 427 更靠 425
        assertEquals(425, GreetingTimes.snapToStep(420 + 3))
        assertEquals(GreetingTimes.DAY, GreetingTimes.snapToStep(1438))
        assertEquals(0, GreetingTimes.snapToStep(-10))
    }
}
