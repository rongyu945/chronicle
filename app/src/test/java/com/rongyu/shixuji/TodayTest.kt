package com.rongyu.shixuji

import com.rongyu.shixuji.ui.msUntilNextMidnight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 跨午夜刷新的时间计算（7.5.2 审查 5.7）。
 *
 * 页面里的「今天」现在靠 `delay(msUntilNextMidnight())` 而不是每分钟轮询，
 * 所以这个函数必须真的指向**下一个本地午夜**，并且沾上夏令时也不能算成 24 小时以外的怪值。
 */
class TodayTest {

    @Test fun `算出来的是下一个本地午夜`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 11, 23, 30, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val delay = msUntilNextMidnight(now)
        assertEquals(30L * 60 * 1000, delay)

        val landed = Calendar.getInstance().apply { timeInMillis = now + delay }
        assertEquals(12, landed.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, landed.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, landed.get(Calendar.MINUTE))
    }

    @Test fun `刚过午夜时睡满一整天`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 11, 0, 0, 1); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val delay = msUntilNextMidnight(now)
        assertTrue("应当是接近 24 小时，实际 $delay", delay in 23L * 3600_000..25L * 3600_000)
    }

    @Test fun `夹在 1 秒到 25 小时之间`() {
        // 时钟被往前拨、或者系统时间跳到很久以前，也不能算出 0 / 负数（会忙等）
        assertTrue(msUntilNextMidnight(0L) >= 1_000L)
        assertTrue(msUntilNextMidnight(Long.MAX_VALUE / 2) <= 25L * 3600_000)
    }
}
