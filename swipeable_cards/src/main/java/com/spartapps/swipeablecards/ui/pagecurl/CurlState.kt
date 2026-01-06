package com.spartapps.swipeablecards.ui.pagecurl

/**
 * Represents the current state of the page curl animation.
 */
internal enum class CurlState {
    /** No curl effect, card is at rest in its default position. */
    Idle,

    /** User is actively dragging the card, curl follows finger. */
    Dragging,

    /** Threshold was crossed, animating to fully curled (100%) before dismissing. */
    Completing,

    /** Threshold was not crossed, animating back to flat (0% curl). */
    Resetting
}
