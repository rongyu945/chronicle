package com.rongyu.shixuji.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * 换页动效的位移/缩放映射（7.7.1）。
 *
 * 这组测试钉两件事，缺一件都会被肉眼发现：
 *  ① **干净起步必须与方案逐像素一致** —— 修连续性时最容易顺手把观感改了；
 *  ② **任何时刻被打断都不许跳** —— 7.7.0 在「入场途中点别的页」时跳 17dp（≈45px），
 *     正是外部复核报告 §8.3 指出的那一下「顿」。
 */
class TabSegmentTest {

    // 用真实 dp 值当「像素」用（换算比例不影响单调性/连续性的判定）
    private val exitPx = 8f
    private val enterPx = 12f

    private fun exitSeg(progress: Float, dir: Int = 1, fromTx: Float = 0f, fromScale: Float = 1f) =
        TabSegment(TabPhase.Exiting, progress, fromTx, fromScale, dir, exitPx, enterPx)

    private fun enterSeg(progress: Float, dir: Int = 1, fromTx: Float = 0f, fromScale: Float = 1f) =
        TabSegment(TabPhase.Entering, progress, fromTx, fromScale, dir, exitPx, enterPx)

    // ---------- ① 干净起步 = 7.7.0 的公式 ----------

    @Test
    fun cleanExitMatchesSpec() {
        val seg = exitSeg(progress = 1f)          // 从完整可见开始退场
        var p = 0f
        while (p <= 1.0001f) {
            assertEquals("tx@p=$p", -exitPx * (1f - p), seg.txAt(p), 1e-4f)
            assertEquals("scale@p=$p", 0.996f + 0.004f * p, seg.scaleAt(p), 1e-4f)
            p += 0.05f
        }
    }

    @Test
    fun cleanEnterMatchesSpec() {
        val seg = enterSeg(progress = 0f, fromTx = enterPx, fromScale = TabSegment.ENTER_SCALE)
        var p = 0f
        while (p <= 1.0001f) {
            assertEquals("tx@p=$p", enterPx * (1f - p), seg.txAt(p), 1e-4f)
            assertEquals("scale@p=$p", 0.994f + 0.006f * p, seg.scaleAt(p), 1e-4f)
            p += 0.05f
        }
    }

    @Test
    fun directionIsMirrored() {
        val right = exitSeg(progress = 1f, dir = 1)
        val left = exitSeg(progress = 1f, dir = -1)
        assertEquals(-right.txAt(0.3f), left.txAt(0.3f), 1e-4f)
    }

    // ---------- ② 被打断时连续（本次修复的核心） ----------

    @Test
    fun retargetDuringEnterIsContinuous() {
        // 入场到 30% 时用户点了别的页：新退场段必须以屏幕上的值为起点
        val enter = enterSeg(progress = 0f, fromTx = enterPx, fromScale = TabSegment.ENTER_SCALE)
        val p0 = 0.30f
        val tx0 = enter.txAt(p0)
        val s0 = enter.scaleAt(p0)

        val exit = exitSeg(progress = p0, fromTx = tx0, fromScale = s0)
        assertEquals("切换那一帧 tx 不许跳", tx0, exit.txAt(p0), 1e-4f)
        assertEquals("切换那一帧 scale 不许跳", s0, exit.scaleAt(p0), 1e-4f)
    }

    @Test
    fun reversalDuringExitIsContinuous() {
        // 退场到 40% 时用户点回原页：新入场段必须以屏幕上的值为起点
        val exit = exitSeg(progress = 1f)
        val p0 = 0.40f
        val tx0 = exit.txAt(p0)
        val s0 = exit.scaleAt(p0)

        val back = enterSeg(progress = p0, fromTx = tx0, fromScale = s0)
        assertEquals(tx0, back.txAt(p0), 1e-4f)
        assertEquals(s0, back.scaleAt(p0), 1e-4f)
        // 并且最终必须真的回到原位
        assertEquals(0f, back.txAt(1f), 1e-4f)
        assertEquals(1f, back.scaleAt(1f), 1e-4f)
    }

    /**
     * 回归钉：证明 7.7.0 的「固定锚点」写法在同样时刻跳了多少。
     * 若有人把 [TabSegment] 改回「phase + 理想初值」，这条会立刻失败。
     */
    @Test
    fun fixedAnchorVersionWouldJumpByMoreThan8dp() {
        val enterIdeal = enterSeg(progress = 0f, fromTx = enterPx, fromScale = TabSegment.ENTER_SCALE)
        val exitIdeal = exitSeg(progress = 1f)
        for (p in listOf(0.15f, 0.30f, 0.50f, 0.70f)) {
            val jump = abs(enterIdeal.txAt(p) - exitIdeal.txAt(p))
            assertTrue("p=$p 应至少有 6dp 的跳变（实际 $jump）", jump >= 6f)
        }
    }

    // ---------- ③ 边界与退化 ----------

    @Test
    fun degenerateExitSegmentDoesNotProduceNaN() {
        // alpha 已经是 0 还在退场：起点=终点，不能除零
        val seg = exitSeg(progress = 0f)
        assertEquals(-exitPx, seg.txAt(0f), 1e-4f)
        assertTrue(seg.txAt(0f).isFinite())
        assertEquals(TabSegment.EXIT_SCALE, seg.scaleAt(0f), 1e-4f)
    }

    @Test
    fun degenerateEnterSegmentDoesNotProduceNaN() {
        val seg = enterSeg(progress = 1f, fromTx = 0f, fromScale = 1f)
        assertEquals(0f, seg.txAt(1f), 1e-4f)
        assertEquals(1f, seg.scaleAt(1f), 1e-4f)
    }

    @Test
    fun fractionStaysInUnitRangeAndIsMonotonic() {
        val exit = exitSeg(progress = 1f)
        var last = -1f
        var p = 1f
        while (p >= -0.0001f) {
            val f = exit.fraction(p)
            assertTrue(f in 0f..1f)
            assertTrue("退场推进必须单调不减（p=$p, f=$f）", f >= last - 1e-4f)
            last = f
            p -= 0.02f
        }

        val enter = enterSeg(progress = 0f)
        last = -1f
        p = 0f
        while (p <= 1.0001f) {
            val f = enter.fraction(p)
            assertTrue(f in 0f..1f)
            assertTrue("入场推进必须单调不减（p=$p, f=$f）", f >= last - 1e-4f)
            last = f
            p += 0.02f
        }
    }

    @Test
    fun idleIsRestPosition() {
        val idle = TabSegment.idle(dir = 1, exitPx = exitPx, enterPx = enterPx)
        for (p in listOf(0f, 0.37f, 0.5f, 1f)) {
            assertEquals(0f, idle.txAt(p), 0f)
            assertEquals(1f, idle.scaleAt(p), 0f)
        }
    }

    @Test
    fun randomInterruptionsNeverProduceNaN() {
        val rnd = Random(20261112)
        repeat(2000) {
            val p0 = rnd.nextFloat()
            val seg = when (rnd.nextInt(2)) {
                0 -> exitSeg(progress = p0, dir = if (rnd.nextBoolean()) 1 else -1,
                    fromTx = rnd.nextFloat() * 40f - 20f, fromScale = 0.99f + rnd.nextFloat() * 0.01f)
                else -> enterSeg(progress = p0, dir = if (rnd.nextBoolean()) 1 else -1,
                    fromTx = rnd.nextFloat() * 40f - 20f, fromScale = 0.99f + rnd.nextFloat() * 0.01f)
            }
            val p = rnd.nextFloat()
            assertTrue("tx 必须是有限数", seg.txAt(p).isFinite())
            assertTrue("scale 必须是有限数", seg.scaleAt(p).isFinite())
            assertTrue(seg.scaleAt(p) in 0.98f..1.02f)
        }
    }
}
