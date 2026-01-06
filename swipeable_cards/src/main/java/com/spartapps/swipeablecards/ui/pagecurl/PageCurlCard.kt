package com.spartapps.swipeablecards.ui.pagecurl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.runBlocking
import kotlin.math.absoluteValue

/**
 * A card composable with page curl effect for book-like page flipping.
 *
 * @param modifier Modifier for the card container
 * @param config Configuration for the curl effect (radius, shadow)
 * @param swipeThreshold Horizontal distance required to trigger a swipe
 * @param draggingAcceleration Multiplier for drag sensitivity
 * @param enableHapticFeedback Whether to trigger haptic feedback when threshold is crossed
 * @param draggable Whether the card can be dragged
 * @param onSwipeLeft Called when card is swiped left (page flip forward)
 * @param onSwipeRight Called when card is swiped right (page flip backward)
 * @param content The card content
 */
@Composable
internal fun PageCurlCard(
    modifier: Modifier = Modifier,
    config: PageCurlConfig = PageCurlConfig(),
    swipeThreshold: Dp,
    draggingAcceleration: Float = 1f,
    enableHapticFeedback: Boolean = true,
    draggable: Boolean = true,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val thresholdPx = with(LocalDensity.current) { swipeThreshold.toPx() }

    var curlState by remember { mutableStateOf(CurlState.Idle) }
    var pendingDirection by remember { mutableStateOf<SwipeDirection?>(null) }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    var firstHaptic by remember { mutableStateOf(true) }

    val dragStart = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val dragCurrent = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // Handle curl state transitions
    LaunchedEffect(curlState) {
        when (curlState) {
            CurlState.Completing -> {
                pendingDirection?.let { direction ->
                    // Animate to fully curled (left edge)
                    dragCurrent.animateTo(
                        targetValue = Offset(0f, dragCurrent.value.y),
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                    )

                    // Notify and reset
                    when (direction) {
                        SwipeDirection.Left -> onSwipeLeft()
                        SwipeDirection.Right -> onSwipeRight()
                    }

                    dragStart.snapTo(Offset.Zero)
                    dragCurrent.snapTo(Offset.Zero)
                    horizontalDrag = 0f
                    curlState = CurlState.Idle
                    pendingDirection = null
                }
            }

            CurlState.Resetting -> {
                // Animate back to flat
                dragCurrent.animateTo(
                    targetValue = dragStart.value,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
                )

                dragStart.snapTo(Offset.Zero)
                dragCurrent.snapTo(Offset.Zero)
                horizontalDrag = 0f
                curlState = CurlState.Idle
            }

            else -> { /* Idle or Dragging - handled by gesture */ }
        }
    }

    Box(
        modifier = modifier
            .pageCurlOrFallback(
                config = config,
                dragStart = dragStart.value,
                dragCurrent = dragCurrent.value,
                fallbackOffset = Offset.Zero,
            )
            .then(
                if (draggable && curlState in listOf(CurlState.Idle, CurlState.Dragging)) {
                    Modifier.pointerInput(Unit) {
                        containerWidth = size.width.toFloat()

                        detectDragGestures(
                            onDragStart = { startOffset ->
                                curlState = CurlState.Dragging
                                horizontalDrag = 0f
                                val startPos = Offset(size.width.toFloat(), startOffset.y)
                                runBlocking {
                                    dragStart.snapTo(startPos)
                                    dragCurrent.snapTo(startPos)
                                }
                            },
                            onDragEnd = {
                                val draggedLeft = horizontalDrag < -thresholdPx
                                val draggedRight = horizontalDrag > thresholdPx

                                when {
                                    draggedLeft -> {
                                        pendingDirection = SwipeDirection.Left
                                        curlState = CurlState.Completing
                                    }
                                    draggedRight -> {
                                        pendingDirection = SwipeDirection.Right
                                        curlState = CurlState.Completing
                                    }
                                    else -> {
                                        curlState = CurlState.Resetting
                                    }
                                }
                                firstHaptic = true
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()

                                val acceleratedX = dragAmount.x * draggingAcceleration
                                horizontalDrag += if (isRtl) -acceleratedX else acceleratedX

                                val curlX = (containerWidth + horizontalDrag).coerceIn(0f, containerWidth)
                                val newPos = Offset(
                                    x = curlX,
                                    y = dragStart.value.y + dragAmount.y * 0.2f
                                )
                                runBlocking { dragCurrent.snapTo(newPos) }

                                if (enableHapticFeedback) {
                                    if (horizontalDrag.absoluteValue > thresholdPx) {
                                        if (firstHaptic) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            firstHaptic = false
                                        }
                                    } else {
                                        firstHaptic = true
                                    }
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        content()
    }
}

/** Internal swipe direction for page curl. */
private enum class SwipeDirection { Left, Right }
