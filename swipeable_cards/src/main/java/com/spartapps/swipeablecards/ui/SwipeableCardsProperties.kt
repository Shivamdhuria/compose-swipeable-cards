package com.spartapps.swipeablecards.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spartapps.swipeablecards.ui.pagecurl.PageCurlConfig

/**
 * Default values for [SwipeableCardsProperties].
 */
object SwipeableCardsDefaults {
    /** Number of cards visible in the stack at once. */
    const val VISIBLE_CARDS_IN_STACK = 3

    /** Whether to prevent dragging cards below the top card. */
    const val LOCK_BELOW_CARD_DRAGGING = true

    /** Whether to trigger haptic feedback when swipe threshold is crossed. */
    const val ENABLE_HAPTIC_FEEDBACK_ON_THRESHOLD = true

    /** Multiplier for drag gesture sensitivity. */
    const val DRAGGING_ACCELERATION = 1f

    /** Vertical/horizontal offset between stacked cards. */
    val STACKED_CARDS_OFFSET = 0.dp

    /** Horizontal distance required to trigger a page flip. */
    val SWIPE_THRESHOLD = 200.dp

    /** Padding around the card stack container. */
    val PADDING = 0.dp
}

/**
 * Configuration properties for the swipeable cards component.
 *
 * @property padding Padding applied to the card stack container.
 * @property swipeThreshold Horizontal drag distance required to trigger a page flip.
 * @property lockBelowCardDragging When true, only the top card can be dragged.
 * @property enableHapticFeedbackOnThreshold Triggers haptic feedback when threshold is crossed.
 * @property stackedCardsOffset Visual offset between cards in the stack.
 * @property draggingAcceleration Multiplier for drag gesture sensitivity.
 * @property pageCurlConfig Configuration for the page curl shader effect.
 */
data class SwipeableCardsProperties(
    val padding: Dp = SwipeableCardsDefaults.PADDING,
    val swipeThreshold: Dp = SwipeableCardsDefaults.SWIPE_THRESHOLD,
    val lockBelowCardDragging: Boolean = SwipeableCardsDefaults.LOCK_BELOW_CARD_DRAGGING,
    val enableHapticFeedbackOnThreshold: Boolean = SwipeableCardsDefaults.ENABLE_HAPTIC_FEEDBACK_ON_THRESHOLD,
    val stackedCardsOffset: Dp = SwipeableCardsDefaults.STACKED_CARDS_OFFSET,
    val draggingAcceleration: Float = SwipeableCardsDefaults.DRAGGING_ACCELERATION,
    val pageCurlConfig: PageCurlConfig = PageCurlConfig(),
)
