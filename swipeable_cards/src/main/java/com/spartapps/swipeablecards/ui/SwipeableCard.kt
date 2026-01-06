package com.spartapps.swipeablecards.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.spartapps.swipeablecards.ui.pagecurl.PageCurlCard

/**
 * A swipeable card with page curl effect.
 *
 * This composable wraps [PageCurlCard] and integrates with the swipeable cards system.
 */
@Composable
internal fun SwipeableCard(
    modifier: Modifier = Modifier,
    properties: SwipeableCardsProperties,
    draggable: Boolean,
    scale: Float,
    onSwipe: (SwipeableCardDirection) -> Unit,
    content: @Composable () -> Unit,
) {
    PageCurlCard(
        modifier = modifier.scale(scale),
        config = properties.pageCurlConfig,
        swipeThreshold = properties.swipeThreshold,
        draggingAcceleration = properties.draggingAcceleration,
        enableHapticFeedback = properties.enableHapticFeedbackOnThreshold,
        draggable = draggable,
        onSwipeLeft = { onSwipe(SwipeableCardDirection.Left) },
        onSwipeRight = { onSwipe(SwipeableCardDirection.Right) },
        content = content,
    )
}
