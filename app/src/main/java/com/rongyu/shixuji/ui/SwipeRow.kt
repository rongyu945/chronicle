package com.rongyu.shixuji.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 左滑露出操作按钮的行容器。
 * actions：按钮文字 + 底色（从右往左排）；onAction 回调下标。
 */
@Composable
fun SwipeRow(
    actions: List<Pair<String, Color>>,
    onAction: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val revealPx = with(LocalDensity.current) { (actions.size * 78).dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(18.dp)

    Box(modifier.clip(shape)) {
        // 后面的操作按钮：只在划开时才画出来（否则会从半透明的行底下透出红/灰色）
        Row(
            Modifier.matchParentSize().graphicsLayer {
                alpha = if (revealPx > 0f) (-offsetX.value / revealPx).coerceIn(0f, 1f) else 0f
            },
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEachIndexed { i, (label, color) ->
                Box(
                    Modifier.width(78.dp).fillMaxHeight().background(color)
                        .clickable { onAction(i) },
                    contentAlignment = Alignment.Center
                ) { Text(label, color = Color.White, fontSize = 14.sp) }
            }
        }
        // 前景内容：横向拖动
        Box(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f))
                        }
                    },
                    onDragStopped = {
                        scope.launch {
                            offsetX.animateTo(if (offsetX.value < -revealPx / 2) -revealPx else 0f, tween(200))
                        }
                    }
                )
        ) { content() }
    }
}