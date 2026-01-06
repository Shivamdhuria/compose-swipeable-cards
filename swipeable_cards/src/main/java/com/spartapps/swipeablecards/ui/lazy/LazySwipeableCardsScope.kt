package com.spartapps.swipeablecards.ui.lazy

import androidx.compose.runtime.Composable

/**
 * Scope for building lazy swipeable cards content.
 */
interface LazySwipeableCardsScope<T> {

    /**
     * Adds multiple items to the card stack.
     *
     * @param items The list of items to add
     * @param itemContent Composable that renders each card. Receives the item and its index.
     */
    fun addItems(
        items: List<T>,
        itemContent: @Composable (T, Int) -> Unit,
    )

    /**
     * Adds a single item to the card stack.
     *
     * @param item The item to add
     * @param itemContent Composable that renders the card. Receives the item and its index.
     */
    fun addItem(
        item: T,
        itemContent: @Composable (T, Int) -> Unit,
    )
}

internal class LazySwipeableCardsScopeImpl<T> : LazySwipeableCardsScope<T> {

    private val _items = mutableListOf<LazyCardItemContent<T>>()
    val items: List<LazyCardItemContent<T>> = _items

    override fun addItems(
        items: List<T>,
        itemContent: @Composable (T, Int) -> Unit,
    ) {
        items.forEach {
            _items.add(LazyCardItemContent(it, itemContent))
        }
    }

    override fun addItem(
        item: T,
        itemContent: @Composable (T, Int) -> Unit,
    ) {
        _items.add(LazyCardItemContent(item, itemContent))
    }
}

/**
 * Adds multiple items to the card stack.
 *
 * @param items The list of items to display as cards
 * @param itemContent Composable that renders each card content
 */
inline fun <reified T> LazySwipeableCardsScope<T>.items(
    items: List<T>,
    noinline itemContent: @Composable (T, Int) -> Unit
) = addItems(items, itemContent)

/**
 * Adds a single item to the card stack.
 *
 * @param item The item to display as a card
 * @param itemContent Composable that renders the card content
 */
inline fun <reified T> LazySwipeableCardsScope<T>.item(
    item: T,
    noinline itemContent: @Composable (T, Int) -> Unit
) = addItem(item, itemContent)
