package com.rongyu.shixuji.ui.motion

import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WeekMotionMathTest {
    private fun near(actual: Float, expected: Float, epsilon: Float = 0.01f) {
        assertTrue(abs(actual - expected) <= epsilon, "$actual != $expected")
    }

    @Test
    fun `intra-week day swipe moves exactly one cell`() {
        val thursday = LocalDate.of(2026, 10, 15)
        val f = WeekMotionMath.frame(thursday, SwipeUnit.DAY, -1f, 350f)
        assertTrue(!f.crossingWeek)
        assertEquals(LocalDate.of(2026, 10, 16), f.targetDate)
        near(f.markerCenterX, 225f)
    }

    @Test
    fun `Sunday to Monday is continuous across the seam`() {
        val sunday = LocalDate.of(2026, 10, 18)
        val begin = WeekMotionMath.frame(sunday, SwipeUnit.DAY, 0f, 350f)
        val middle = WeekMotionMath.frame(sunday, SwipeUnit.DAY, -0.5f, 350f)
        val end = WeekMotionMath.frame(sunday, SwipeUnit.DAY, -1f, 350f)
        near(begin.markerCenterX, 325f)
        near(middle.markerCenterX, 175f)
        near(end.markerCenterX, 25f)
        near(end.neighborWeekShiftX!!, 0f)
        assertEquals(LocalDate.of(2026, 10, 19), end.targetDate)
    }

    @Test
    fun `Monday to Sunday is continuous in reverse`() {
        val monday = LocalDate.of(2026, 10, 19)
        val middle = WeekMotionMath.frame(monday, SwipeUnit.DAY, 0.5f, 350f)
        val end = WeekMotionMath.frame(monday, SwipeUnit.DAY, 1f, 350f)
        near(middle.markerCenterX, 175f)
        near(end.markerCenterX, 325f)
        near(end.neighborWeekShiftX!!, 0f)
        assertEquals(LocalDate.of(2026, 10, 18), end.targetDate)
    }

    @Test
    fun `week swipe keeps selected weekday column fixed`() {
        val thursday = LocalDate.of(2026, 10, 15)
        for (fraction in listOf(-1f, -0.8f, -0.5f, 0f, 0.5f, 0.8f, 1f)) {
            val frame = WeekMotionMath.frame(thursday, SwipeUnit.WEEK, fraction, 350f)
            near(frame.markerCenterX, 175f)
        }
    }

    @Test
    fun `date arithmetic survives leap day and DST weekends`() {
        assertEquals(
            2L,
            WeekMotionMath.daysBetween(
                LocalDate.of(2024, 2, 28),
                LocalDate.of(2024, 3, 1)
            )
        )
        assertEquals(
            2L,
            WeekMotionMath.daysBetween(
                LocalDate.of(2026, 3, 7),
                LocalDate.of(2026, 3, 9)
            )
        )
    }

    @Test
    fun `all weekdays and fractional positions stay inside the viewport`() {
        val start = LocalDate.of(2026, 10, 12)
        for (day in 0L..6L) {
            for (unit in SwipeUnit.values()) {
                for (width in listOf(320f, 360f, 1080f)) {
                    for (step in -20..20) {
                        val fraction = step / 20f
                        val frame = WeekMotionMath.frame(
                            start.plusDays(day), unit, fraction, width
                        )
                        assertTrue(
                            frame.markerCenterX >= -0.01f &&
                                frame.markerCenterX <= width + 0.01f
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `invalid geometry is rejected`() {
        val date = LocalDate.of(2026, 10, 15)
        assertFailsWith<IllegalArgumentException> {
            WeekMotionMath.frame(date, SwipeUnit.DAY, 0f, 0f)
        }
        assertFailsWith<IllegalArgumentException> {
            WeekMotionMath.frame(date, SwipeUnit.DAY, Float.NaN, 350f)
        }
    }
}
