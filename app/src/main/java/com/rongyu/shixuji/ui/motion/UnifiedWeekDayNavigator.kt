package com.rongyu.shixuji.ui.motion

import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A single timeline for day pages, the seven-day strip and ONE red marker.
 *
 * Integration:
 *   - Parent owns [selectedDate] and updates it in [onSelectedDateChange].
 *   - Header buttons and "Today" update that same parent date directly.
 *   - [dayContent] should use LazyColumn and stable item keys where applicable.
 *   - Remove the old independent stripNav / stripOutDayMs / stripOutOff.
 *
 * This component is a self-contained replacement for the WEEK-level navigation
 * region, not for the app's month/year navigation or its business data layer.
 */
@Composable
fun UnifiedWeekDayNavigator(
    selectedDate: LocalDate,
    onSelectedDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    eventDates: Set<LocalDate> = emptySet(),
    today: LocalDate = LocalDate.now(),
    accentColor: Color? = null,
    reducedMotion: Boolean = false,
    /**
     * 视觉上已经落位（已提交）的那一天，供宿主页面用它取「当前中心页」的东西
     * （列表滚动位置、大标题折叠、汇总数字……）。不要拿它当业务日期写库：
     * 业务日期仍然由 [selectedDate] 决定。
     */
    onAnchorDateChange: (LocalDate) -> Unit = {},
    header: @Composable (LocalDate) -> Unit = {},
    dayContent: @Composable (LocalDate) -> Unit
) {
    var anchorDate by remember { mutableStateOf(selectedDate) }
    var swipeUnit by remember { mutableStateOf(SwipeUnit.DAY) }
    var offsetFraction by remember { mutableFloatStateOf(0f) }
    var outerAlpha by remember { mutableFloatStateOf(1f) }
    var dayAlpha by remember { mutableFloatStateOf(1f) }
    var markerIndexOverride by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var lastHandledRequest by remember { mutableStateOf(selectedDate) }

    val active = remember { ActiveAnimation() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()

        // 把「视觉已提交」的那一天报给宿主：宿主的列表滚动状态、标题折叠、汇总数字
        // 都按这一天取，不要按「已请求但还没滑到位」的目标日取，否则表头/汇总会先变。
        LaunchedEffect(anchorDate) { onAnchorDateChange(anchorDate) }

        suspend fun animateOffset(target: Float, velocity: Float, gesture: Boolean) {
            if (reducedMotion) {
                offsetFraction = target
                return
            }
            if (gesture) {
                // Only a RELEASE uses a velocity-preserving spring.
                animate(
                    initialValue = offsetFraction,
                    targetValue = target,
                    initialVelocity = velocity / widthPx,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) { value, _ ->
                    offsetFraction = value.coerceIn(-1f, 1f)
                }
            } else {
                // A button click has no incoming velocity: use a short tween.
                if (abs(target - offsetFraction) < 0.0005f) {
                    offsetFraction = target
                    return
                }
                val distanceFraction =
                    abs(target - offsetFraction).coerceIn(0f, 1f)
                val duration = (230f * distanceFraction)
                    .roundToInt().coerceIn(80, 230)
                animate(
                    initialValue = offsetFraction,
                    targetValue = target,
                    animationSpec = tween(duration, easing = FastOutSlowInEasing)
                ) { value, _ ->
                    offsetFraction = value
                }
            }
        }

        suspend fun settle(velocity: Float) {
            if (widthPx <= 0f) return
            val flingThreshold = minOf(widthPx * 1.1f, with(density) { 900.dp.toPx() })
            val direction = when {
                abs(velocity) >= flingThreshold ->
                    if (velocity < 0f) 1 else -1
                abs(offsetFraction) >= 0.23f ->
                    if (offsetFraction < 0f) 1 else -1
                else -> 0
            }

            animateOffset(-direction.toFloat(), velocity, gesture = true)

            if (direction != 0) {
                val target = anchorDate.plusDays(direction.toLong() * swipeUnit.stepDays)
                // Commit the new date and reset the offset atomically.
                Snapshot.withMutableSnapshot {
                    anchorDate = target
                    offsetFraction = 0f
                }
                onSelectedDateChange(target)
            } else {
                offsetFraction = 0f
            }
        }

        suspend fun navigate(target: LocalDate) {
            if (widthPx <= 0f) return

            // An updated request can arrive in the middle of an adjacent
            // animation. Finish the visible partial step when the new target
            // is beyond it; otherwise reverse back to the committed anchor.
            // Never teleport offsetFraction to zero while the region is visible.
            if (!reducedMotion && abs(offsetFraction) > 0.001f) {
                val movingDir = if (offsetFraction < 0f) 1 else -1
                val movingTarget =
                    anchorDate.plusDays(movingDir.toLong() * swipeUnit.stepDays)
                val finishStep = if (movingDir > 0) {
                    target >= movingTarget
                } else {
                    target <= movingTarget
                }
                animateOffset(
                    target = if (finishStep) -movingDir.toFloat() else 0f,
                    velocity = 0f,
                    gesture = false
                )
                if (finishStep) {
                    Snapshot.withMutableSnapshot {
                        anchorDate = movingTarget
                        offsetFraction = 0f
                    }
                }
            }

            val diff = WeekMotionMath.daysBetween(anchorDate, target)
            if (diff == 0L) {
                // Also handles "return to the original day" while a previous
                // programmatic animation was only partially completed.
                offsetFraction = 0f
                markerIndexOverride = null
                dayAlpha = 1f
                if (!reducedMotion && outerAlpha < 0.999f) {
                    animate(
                        initialValue = outerAlpha,
                        targetValue = 1f,
                        animationSpec = tween(120, easing = LinearOutSlowInEasing)
                    ) { value, _ -> outerAlpha = value }
                } else {
                    outerAlpha = 1f
                }
                return
            }

            if (reducedMotion) {
                Snapshot.withMutableSnapshot {
                    anchorDate = target
                    offsetFraction = 0f
                    outerAlpha = 1f
                    dayAlpha = 1f
                    markerIndexOverride = null
                }
                return
            }

            when {
                (abs(diff) == 1L || abs(diff) == 7L) &&
                    markerIndexOverride == null -> {
                    swipeUnit = if (abs(diff) == 7L) SwipeUnit.WEEK else SwipeUnit.DAY
                    markerIndexOverride = null
                    outerAlpha = 1f
                    dayAlpha = 1f
                    animateOffset(
                        target = if (diff > 0) -1f else 1f,
                        velocity = 0f,
                        gesture = false
                    )
                    Snapshot.withMutableSnapshot {
                        anchorDate = target
                        offsetFraction = 0f
                    }
                }

                WeekMotionMath.startOfWeek(anchorDate) ==
                    WeekMotionMath.startOfWeek(target) -> {
                    // Same-week multi-day tap: glide the single marker while
                    // the DAY CONTENT gently crossfades. No fake page sweep.
                    swipeUnit = SwipeUnit.DAY
                    offsetFraction = 0f
                    outerAlpha = 1f
                    val fromIndex = markerIndexOverride
                        ?: (anchorDate.dayOfWeek.value - 1).toFloat()
                    val toIndex = (target.dayOfWeek.value - 1).toFloat()
                    markerIndexOverride = fromIndex

                    coroutineScope {
                        launch {
                            animate(
                                initialValue = fromIndex,
                                targetValue = toIndex,
                                animationSpec = tween(230, easing = FastOutSlowInEasing)
                            ) { value, _ -> markerIndexOverride = value }
                        }
                        launch {
                            animate(
                                initialValue = dayAlpha,
                                targetValue = 0f,
                                animationSpec = tween(105, easing = FastOutLinearInEasing)
                            ) { value, _ -> dayAlpha = value }
                            Snapshot.withMutableSnapshot {
                                anchorDate = target
                                offsetFraction = 0f
                            }
                            animate(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = tween(160, easing = LinearOutSlowInEasing)
                            ) { value, _ -> dayAlpha = value }
                        }
                    }
                    markerIndexOverride = null
                    dayAlpha = 1f
                }

                else -> {
                    // Far jump: make the swap while the region is invisible.
                    // Avoid animating through dozens of unrendered dates.
                    animate(
                        initialValue = outerAlpha,
                        targetValue = 0f,
                        animationSpec = tween(115, easing = FastOutLinearInEasing)
                    ) { value, _ -> outerAlpha = value }
                    Snapshot.withMutableSnapshot {
                        anchorDate = target
                        swipeUnit = SwipeUnit.DAY
                        offsetFraction = 0f
                        dayAlpha = 1f
                        markerIndexOverride = null
                    }
                    animate(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = tween(175, easing = LinearOutSlowInEasing)
                    ) { value, _ -> outerAlpha = value }
                }
            }
        }

        suspend fun beginGesture(unit: SwipeUnit) {
            // ★ 判「能不能换轴」必须看「有没有动画还在跑」，不能看 offsetFraction 是否≈0：
            //   快速手势（300ms 滑一整页）的第一批 delta 会在 onDragStarted 之前就落进
            //   offsetFraction（实测已经到 -0.028 ≈ 30px），于是 `abs(offset) <= 0.001` 不成立，
            //   周条上的快滑就会被当成「切一天」—— 在周条上快滑一次，日期只 +1 天、周条不动。
            //   active.job 非空 ⟺ 真有一段动画/落位在跑，这才是原型注释里想表达的条件。
            val wasAnimating = active.job != null
            dragging = true
            active.job?.cancelAndJoin()
            active.job = null
            // Touch takes precedence over a programmatic request that was
            // still animating. Reset the parent's pending target to the
            // visually committed anchor before accepting fresh drag input.
            if (selectedDate != anchorDate) {
                lastHandledRequest = anchorDate
                onSelectedDateChange(anchorDate)
            }
            // A new gesture must not inherit a half-faded jump.
            outerAlpha = 1f
            dayAlpha = 1f
            markerIndexOverride = null
            if (swipeUnit == unit || !wasAnimating) {
                swipeUnit = unit
            }
            // If a different region grabs an animation mid-flight, preserve
            // its current axis until settle. Reinterpreting a partial DAY
            // offset as WEEK would teleport the calendar content.
        }

        suspend fun endGesture(velocity: Float) {
            active.job?.cancelAndJoin()
            dragging = false
            val job = scope.launch {
                val self = currentCoroutineContext().job
                try {
                    settle(velocity)
                } finally {
                    if (active.job === self) active.job = null
                }
            }
            active.job = job
        }

        val dayDragState = rememberDraggableState { delta ->
            if (widthPx > 0f) {
                offsetFraction = (offsetFraction + delta / widthPx).coerceIn(-1f, 1f)
            }
        }
        val weekDragState = rememberDraggableState { delta ->
            if (widthPx > 0f) {
                offsetFraction = (offsetFraction + delta / widthPx).coerceIn(-1f, 1f)
            }
        }

        // 外部选择（表头 ‹ ›、「今」、点日期）和触摸手势共用同一套运动状态。
        // 最新目标取消旧动画。
        LaunchedEffect(selectedDate, dragging, widthPx, reducedMotion) {
            if (dragging || widthPx <= 0f) return@LaunchedEffect

            val isNewRequest = selectedDate != lastHandledRequest
            // After a drag release, the old parent date is still the
            // committed anchor until settle finishes. Do not cancel settle.
            if (!isNewRequest && selectedDate == anchorDate) {
                return@LaunchedEffect
            }
            lastHandledRequest = selectedDate
            if (selectedDate == anchorDate &&
                abs(offsetFraction) < 0.001f &&
                outerAlpha >= 0.999f &&
                dayAlpha >= 0.999f &&
                markerIndexOverride == null
            ) {
                return@LaunchedEffect
            }
            active.job?.cancelAndJoin()
            val self = currentCoroutineContext().job
            active.job = self
            try {
                navigate(selectedDate)
            } finally {
                if (active.job === self) active.job = null
            }
        }

        Column(Modifier.fillMaxSize()) {
            // Header receives the VISUALLY committed date, not the early
            // requested target. This avoids a header/list mismatch.
            header(anchorDate)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = outerAlpha }
            ) {
                SmoothWeekStrip(
                    baseDate = anchorDate,
                    unit = swipeUnit,
                    offsetFraction = {
                        offsetFraction
                    },
                    markerIndexOverride = { markerIndexOverride },
                    eventDates = eventDates,
                    today = today,
                    accentColor = accentColor,
                    onDayClick = onSelectedDateChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        // 和原周条同一视觉：左右各留 16dp（7 格各 ≈142px），
                        // draggable 放在 padding 之前 —— 拖动热区仍是整行，不受内边距影响
                        .draggable(
                            state = weekDragState,
                            orientation = Orientation.Horizontal,
                            onDragStarted = { beginGesture(SwipeUnit.WEEK) },
                            onDragStopped = { velocity -> endGesture(velocity) }
                        )
                        .padding(horizontal = 16.dp)
                )

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .graphicsLayer { alpha = dayAlpha }
                        .draggable(
                            state = dayDragState,
                            orientation = Orientation.Horizontal,
                            onDragStarted = { beginGesture(SwipeUnit.DAY) },
                            onDragStopped = { velocity -> endGesture(velocity) }
                        )
                ) {
                    for (off in -1..1) {
                        val pageDate = anchorDate.plusDays(off.toLong() * swipeUnit.stepDays)
                        androidx.compose.runtime.key(pageDate) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = (off.toFloat() + offsetFraction) * widthPx
                                    }
                            ) {
                                dayContent(pageDate)
                            }
                        }
                    }
                }
            }
        }
    }
}

private class ActiveAnimation {
    var job: Job? = null
}

private data class WeekCell(
    val date: LocalDate,
    val number: String,
    val weekday: String
)

private fun cellsForWeek(start: LocalDate): List<WeekCell> {
    val labels = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    return (0..6).map { i ->
        val date = start.plusDays(i.toLong())
        WeekCell(date, date.dayOfMonth.toString(), labels[i])
    }
}

/**
 * Draws exactly one selection circle, regardless of the number of visible
 * calendar weeks. Offset is read in the draw phase, not during composition.
 */
@Composable
fun SmoothWeekStrip(
    baseDate: LocalDate,
    unit: SwipeUnit,
    offsetFraction: () -> Float,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    markerIndexOverride: () -> Float? = { null },
    eventDates: Set<LocalDate> = emptySet(),
    today: LocalDate = LocalDate.now(),
    accentColor: Color? = null
) {
    val currentStart = remember(baseDate) { WeekMotionMath.startOfWeek(baseDate) }
    val current = remember(currentStart) { cellsForWeek(currentStart) }
    val previous = remember(currentStart) { cellsForWeek(currentStart.minusWeeks(1)) }
    val next = remember(currentStart) { cellsForWeek(currentStart.plusWeeks(1)) }

    val primary = accentColor ?: MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val paleToday = primary.copy(alpha = 0.18f)
    val numberArgb = onSurface.toArgb()
    val weekdayArgb = secondaryText.toArgb()
    val whiteArgb = Color.White.toArgb()

    val textPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            textAlign = AndroidPaint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }
    val markerClip = remember { AndroidPath() }

    Box(modifier.clipToBounds()) {
        Canvas(Modifier.matchParentSize()) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas

            val frame = WeekMotionMath.frame(
                baseDate = baseDate,
                unit = unit,
                offsetFraction = offsetFraction(),
                widthPx = size.width
            )
            val cellWidth = size.width / 7f
            val circleRadius = 16.dp.toPx()
            val centerY = 37.dp.toPx()
            val markerX = markerIndexOverride()?.let {
                (it + 0.5f) * cellWidth
            } ?: frame.markerCenterX

            val neighbor = when (frame.neighborWeekStart) {
                currentStart.minusWeeks(1) -> previous
                currentStart.plusWeeks(1) -> next
                else -> null
            }

            // Backgrounds and event dots are drawn first.
            fun drawDecorations(cells: List<WeekCell>, shift: Float) {
                cells.forEachIndexed { i, day ->
                    val x = (i + 0.5f) * cellWidth + shift
                    if (x < -cellWidth || x > size.width + cellWidth) return@forEachIndexed
                    if (day.date == today && abs(markerX - x) > circleRadius * 0.7f) {
                        drawCircle(
                            color = paleToday,
                            radius = circleRadius,
                            center = Offset(x, centerY)
                        )
                    }
                    if (day.date in eventDates) {
                        drawCircle(
                            color = primary,
                            radius = 2.dp.toPx(),
                            center = Offset(x, 61.dp.toPx())
                        )
                    }
                }
            }

            drawDecorations(current, frame.currentWeekShiftX)
            if (neighbor != null && frame.neighborWeekShiftX != null) {
                drawDecorations(neighbor, frame.neighborWeekShiftX)
            }

            // The ONLY selected marker. It is not a child of any week page.
            drawCircle(
                color = primary,
                radius = circleRadius,
                center = Offset(markerX, centerY)
            )

            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                val weekBaseline = 14.dp.toPx()
                val numberBaseline = 42.dp.toPx()
                val weekdayTextPx = 11.sp.toPx()
                val numberTextPx = 14.sp.toPx()

                fun drawLabels(
                    cells: List<WeekCell>,
                    shift: Float,
                    whiteOnly: Boolean
                ) {
                    cells.forEachIndexed { i, day ->
                        val x = (i + 0.5f) * cellWidth + shift
                        if (x < -cellWidth || x > size.width + cellWidth) {
                            return@forEachIndexed
                        }
                        textPaint.alpha = 255

                        if (!whiteOnly) {
                            textPaint.textSize = weekdayTextPx
                            textPaint.color = weekdayArgb
                            canvas.drawText(day.weekday, x, weekBaseline, textPaint)
                        }

                        textPaint.textSize = numberTextPx
                        textPaint.color = if (whiteOnly) whiteArgb else numberArgb
                        canvas.drawText(day.number, x, numberBaseline, textPaint)
                    }
                }

                // Draw normal text first.
                drawLabels(current, frame.currentWeekShiftX, whiteOnly = false)
                if (neighbor != null && frame.neighborWeekShiftX != null) {
                    drawLabels(neighbor, frame.neighborWeekShiftX, whiteOnly = false)
                }

                // Exact circle clipping prevents white digits from "leaking"
                // outside the red marker at intermediate drag positions.
                markerClip.rewind()
                markerClip.addCircle(
                    markerX, centerY, circleRadius, AndroidPath.Direction.CW
                )
                val saveCount = canvas.save()
                canvas.clipPath(markerClip)
                drawLabels(current, frame.currentWeekShiftX, whiteOnly = true)
                if (neighbor != null && frame.neighborWeekShiftX != null) {
                    drawLabels(neighbor, frame.neighborWeekShiftX, whiteOnly = true)
                }
                canvas.restoreToCount(saveCount)
            }
        }

        // Separate accessible hit targets. Do not read animation state in
        // composition; check it only when a tap actually occurs.
        Row(Modifier.matchParentSize()) {
            current.forEach { day ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .semantics {
                            contentDescription = buildString {
                                append("${day.date.year}年${day.date.monthValue}月${day.date.dayOfMonth}日")
                                if (day.date == today) append("，今天")
                                if (day.date in eventDates) append("，有事项")
                            }
                            selected = day.date == baseDate
                        }
                        .clickable {
                            if (abs(offsetFraction()) < 0.02f) {
                                onDayClick(day.date)
                            }
                        }
                )
            }
        }
    }
}
