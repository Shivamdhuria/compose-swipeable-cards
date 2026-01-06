package com.spartapps.swipeablecards.ui.lazy

import androidx.compose.runtime.Composable

/**
 * Holds the item data and content composable for a lazy swipeable card.
 *
 * @param T The type of data for the card
 * @property item The data item
 * @property itemContent The composable that renders the card content.
 *                       Receives the item and its index in the list.
 */
data class LazyCardItemContent<T>(
    val item: T,
    val itemContent: @Composable (T, Int) -> Unit
)
