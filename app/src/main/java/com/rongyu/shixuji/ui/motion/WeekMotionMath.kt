package com.rongyu.shixuji.ui.motion

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Pure Kotlin geometry for one global selection marker.
 *
 * Coordinate convention:
 *   offsetFraction < 0  => finger moved left => navigate forward
 *   offsetFraction > 0  => finger moved right => navigate backward
 *
 * This file has no Compose dependency and can be unit-tested on the JVM.
 */
enum class SwipeUnit(val stepDays: Long) {
    DAY(1L),
    WEEK(7L)
}

data class WeekMotionFrame(
    val currentWeekStart: LocalDate,
    val targetDate: LocalDate,
    val markerCenterX: Float,
    val currentWeekShiftX: Float,
    val neighborWeekStart: LocalDate?,
    val neighborWeekShiftX: Float?,
    val crossingWeek: Boolean
)

object WeekMotionMath {
    fun startOfWeek(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())

    fun daysBetween(from: LocalDate, to: LocalDate): Long =
        ChronoUnit.DAYS.between(from, to)

    /**
     * The marker is drawn exactly once in screen coordinates.
     * During a cross-week day swipe, the calendar track moves by one week
     * while the selected day advances by one cell *inside* that track.
     * During a week swipe, the marker stays in the same weekday column.
     */
    fun frame(
        baseDate: LocalDate,
        unit: SwipeUnit,
        offsetFraction: Float,
        widthPx: Float
    ): WeekMotionFrame {
        require(widthPx.isFinite() && widthPx > 0f) {
            "widthPx must be a finite, positive number"
        }
        require(offsetFraction.isFinite()) {
            "offsetFraction must be finite"
        }

        val fraction = offsetFraction.coerceIn(-1f, 1f)
        val direction = when {
            fraction < 0f -> 1L
            fraction > 0f -> -1L
            else -> 0L
        }
        val progress = abs(fraction)
        val currentWeekStart = startOfWeek(baseDate)
        val targetDate = baseDate.plusDays(direction * unit.stepDays)
        val neighborWeekStart = startOfWeek(targetDate)
        val crossingWeek = direction != 0L && neighborWeekStart != currentWeekStart
        val shift = if (crossingWeek) fraction * widthPx else 0f

        val cellPx = widthPx / 7f
        val baseIndex = baseDate.dayOfWeek.value - 1
        val selectedIndexOnTrack =
            baseIndex.toFloat() + 0.5f + direction.toFloat() * progress * unit.stepDays.toFloat()
        val markerX = selectedIndexOnTrack * cellPx + shift

        return WeekMotionFrame(
            currentWeekStart = currentWeekStart,
            targetDate = targetDate,
            markerCenterX = markerX,
            currentWeekShiftX = shift,
            neighborWeekStart = if (crossingWeek) neighborWeekStart else null,
            neighborWeekShiftX = if (crossingWeek) shift + direction.toFloat() * widthPx else null,
            crossingWeek = crossingWeek
        )
    }
}
