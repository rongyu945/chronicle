package com.rongyu.shixuji

import com.rongyu.shixuji.ui.contentAlpha
import com.rongyu.shixuji.ui.itemLocalProgress
import com.rongyu.shixuji.ui.itemStartProgress
import com.rongyu.shixuji.ui.sectionGapDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分节单一进度模型的纯函数回归测试（6.9.31）。
 *
 * 这三条映射决定了整套动画的语义，出错时肉眼很难立刻看出来（表现为「某几条收不干净」
 * 或「展开时最后一条差一点」），所以钉成单测：
 *  ① 错峰起点：条数/索引变化时不能越界，单条必须无错峰；
 *  ② 局部进度：p=1 时**所有**条目必须到 1；p 在起点之前必须严格 0；
 *  ③ 透明度：两端必须精确 0 / 1（差一点就会留下半透明残影或文字提前出现）。
 */
class SectionRevealMathTest {

    @Test
    fun singleItemHasNoStagger() {
        assertEquals(0f, itemStartProgress(0, 1, 0.28f), 0f)
        assertEquals(0f, itemStartProgress(0, 0, 0.28f), 0f)
    }

    @Test
    fun staggerStartGrowsWithIndexAndIsBounded() {
        val n = 5
        val span = 0.28f
        val starts = (0 until n).map { itemStartProgress(it, n, span) }
        assertEquals(0f, starts.first(), 1e-6f)
        assertEquals(span, starts.last(), 1e-6f)
        assertTrue(starts.zipWithNext().all { (a, b) -> b > a })
        // 越界索引不能炸
        assertEquals(0f, itemStartProgress(-3, n, span), 1e-6f)
        assertEquals(span, itemStartProgress(99, n, span), 1e-6f)
    }

    @Test
    fun localProgressHitsExactlyOneAtTheEnd() {
        val n = 7
        val span = 0.28f
        for (i in 0 until n) {
            val start = itemStartProgress(i, n, span)
            assertEquals("第 $i 条在 p=1 时必须完全展开", 1f, itemLocalProgress(1f, start), 1e-6f)
            assertEquals("第 $i 条在 p=0 时必须完全收起", 0f, itemLocalProgress(0f, start), 1e-6f)
            if (start > 0f) {
                assertEquals("起点之前必须纹丝不动", 0f, itemLocalProgress(start, start), 0f)
            }
        }
    }

    @Test
    fun topItemLeadsWhenExpandingAndTrailsWhenCollapsing() {
        val n = 4
        val span = 0.28f
        // 展开：p=0.2 时第一条已经动了，最后一条还没开始
        val p = 0.2f
        val top = itemLocalProgress(p, itemStartProgress(0, n, span))
        val bottom = itemLocalProgress(p, itemStartProgress(n - 1, n, span))
        assertTrue("展开应自上而下", top > bottom)
        assertTrue(bottom == 0f)
        // 收起：p=0.9 时……最后一条剩余进度更小（先收完）
        val p2 = 0.2f
        val top2 = itemLocalProgress(p2, itemStartProgress(0, n, span))
        val bottom2 = itemLocalProgress(p2, itemStartProgress(n - 1, n, span))
        assertTrue("收起应自下而上（底部先到底）", bottom2 < top2)
    }

    @Test
    fun contentAlphaIsZeroThenOneAtBothEnds() {
        assertEquals(0f, contentAlpha(0f), 0f)
        assertEquals(0f, contentAlpha(0.05f), 0f)      // 太矮，不能让字露出来
        assertEquals(1f, contentAlpha(0.72f), 1e-6f)
        assertEquals(1f, contentAlpha(1f), 0f)
        // 单调不减
        var prev = -1f
        for (i in 0..100) {
            val a = contentAlpha(i / 100f)
            assertTrue("透明度必须单调", a >= prev)
            prev = a
        }
    }

    @Test
    fun sectionGapInterpolatesBetweenBothStates() {
        assertEquals(4f, sectionGapDp(0f, 4f, 12f), 1e-6f)
        assertEquals(12f, sectionGapDp(1f, 4f, 12f), 1e-6f)
        assertEquals(8f, sectionGapDp(0.5f, 4f, 12f), 1e-6f)
    }

    @Test
    fun NaNAndOutOfRangeInputsAreSafe() {
        assertEquals(0f, itemLocalProgress(Float.NaN, 0f), 0f)
        assertEquals(0f, contentAlpha(Float.NaN), 0f)
        assertEquals(1f, itemLocalProgress(5f, 0f), 1f)   // 越界夹到 1
        assertEquals(0f, itemLocalProgress(-5f, 0f), 0f)
        assertEquals(1f, contentAlpha(3f), 1f)
    }
}
