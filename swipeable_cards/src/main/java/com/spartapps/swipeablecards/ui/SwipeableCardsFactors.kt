package com.spartapps.swipeablecards.ui

import androidx.compose.ui.geometry.Offset
import com.spartapps.swipeablecards.state.SwipeableCardsState

/**
 * Configuration for calculation factors that affect card behavior in the stack.
 *
 * @property scaleFactor Lambda that calculates the scaling factor for each card.
 *                       Allows dynamic resizing based on card index, state, or properties.
 *                       Default keeps all cards at the same scale (1f).
 *
 * @property cardOffsetCalculation Lambda that calculates the position offset for each card.
 *                                Creates the stacked card visual effect by offsetting cards
 *                                based on their position from the top of the stack.
 */
data class SwipeableCardsFactors(
    val scaleFactor: (
        index: Int,
        state: SwipeableCardsState,
        properties: SwipeableCardsProperties,
    ) -> Float = { _, _, _ -> 1f },

    val cardOffsetCalculation: (
        index: Int,
        state: SwipeableCardsState,
        properties: SwipeableCardsProperties,
    ) -> Offset = { index, state, properties ->
        val offsetValue = properties.stackedCardsOffset.value *
            (state.visibleCardsInStack - 1 - (index - state.currentCardIndex))
        Offset(offsetValue, -offsetValue)
    },
)
