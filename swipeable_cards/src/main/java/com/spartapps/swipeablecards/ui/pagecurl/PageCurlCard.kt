package com.spartapps.swipeablecards.ui.pagecurl

import android.util.Log
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.spartapps.swipeablecards.state.SwipeableCardsState
import kotlinx.coroutines.launch

private const val TAG = "PageCurlCard"

/**
 * A card composable with page curl effect for book-like page flipping.
 *
 * This is now a simple presentational component that delegates all logic to SwipeableCardsState.
 *
 * @param modifier Modifier for the card container
 * @param config Configuration for the curl effect (radius, shadow)
 * @param swipeThreshold Horizontal distance required to trigger a swipe
 * @param draggingAcceleration Multiplier for drag sensitivity
 * @param enableHapticFeedback Whether to trigger haptic feedback when threshold is crossed
 * @param draggable Whether the card can be dragged
 * @param state The swipeable cards state managing all animation logic
 * @param cardIndex The index of this card in the stack
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
    state: SwipeableCardsState,
    cardIndex: Int,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val thresholdPx = with(LocalDensity.current) { swipeThreshold.toPx() }

    // Check if this card should show the curl effect
    val isPreviousCardInBackwardSwipe = state.isBackwardSwipe && cardIndex == state.currentCardIndex - 1
    val isCurrentCard = cardIndex == state.currentCardIndex

    // Determine which drag positions to use:
    // - Forward swipe (or undetermined): show curl on current card
    // - Backward swipe: show curl ONLY on previous card, NOT on current card
    val shouldShowCurl = if (state.isBackwardSwipe) {
        isPreviousCardInBackwardSwipe
    } else {
        isCurrentCard
    }

    val effectiveDragStart = if (shouldShowCurl) {
        state.curlDragStart
    } else {
        Offset.Zero
    }

    val effectiveDragCurrent = if (shouldShowCurl) {
        state.curlDragCurrent
    } else {
        Offset.Zero
    }

    Log.d(TAG, "🎴 CARD $cardIndex - isBackwardSwipe=${state.isBackwardSwipe}, isPrevious=$isPreviousCardInBackwardSwipe, isCurrent=$isCurrentCard, shouldShowCurl=$shouldShowCurl, dragStart=$effectiveDragStart, dragCurrent=$effectiveDragCurrent")

    // Run animations based on state changes
    LaunchedEffect(state.curlState) {
        if (isCurrentCard) {
            state.runCurlAnimation(
                scope = this,
                onSwipeLeft = onSwipeLeft,
                onSwipeRight = onSwipeRight
            )
        }
    }

    Box(
        modifier = modifier
            .pageCurlOrFallback(
                config = config,
                dragStart = effectiveDragStart,
                dragCurrent = effectiveDragCurrent,
                fallbackOffset = Offset.Zero,
            )
            .then(
                if (draggable && isCurrentCard && state.curlState in listOf(CurlState.Idle, CurlState.Dragging)) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                scope.launch {
                                    state.containerWidth = size.width.toFloat()
                                    state.onCurlDragStart(startOffset)
                                }
                            },
                            onDragEnd = {
                                state.onCurlDragEnd(
                                    thresholdPx = thresholdPx,
                                    onSwipeLeft = {}, // Will be called from animation
                                    onSwipeRight = {} // Will be called from animation
                                )
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    state.onCurlDrag(
                                        dragAmount = dragAmount,
                                        draggingAcceleration = draggingAcceleration,
                                        isRtl = isRtl,
                                        thresholdPx = thresholdPx,
                                        enableHapticFeedback = enableHapticFeedback,
                                        haptic = if (enableHapticFeedback) haptic else null
                                    )
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
