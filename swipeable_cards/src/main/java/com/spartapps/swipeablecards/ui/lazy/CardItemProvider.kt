package com.spartapps.swipeablecards.ui.lazy

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.spartapps.swipeablecards.state.SwipeableCardsState
import com.spartapps.swipeablecards.ui.SwipeableCard
import com.spartapps.swipeablecards.ui.SwipeableCardDirection
import com.spartapps.swipeablecards.ui.SwipeableCardsFactors
import com.spartapps.swipeablecards.ui.SwipeableCardsProperties

@Composable
internal fun <T> rememberItemProvider(
    state: SwipeableCardsState,
    properties: SwipeableCardsProperties,
    factors: SwipeableCardsFactors,
    onSwipe: (T, SwipeableCardDirection) -> Unit,
    customLazyListScope: LazySwipeableCardsScope<T>.() -> Unit
): CardItemProvider<T> {
    val customLazyListScopeState = rememberUpdatedState(customLazyListScope)

    return remember {
        CardItemProvider(
            itemsState = derivedStateOf {
                val layoutScope =
                    LazySwipeableCardsScopeImpl<T>().apply(customLazyListScopeState.value)
                layoutScope.items
            },
            state = state,
            properties = properties,
            factors = factors,
            onSwipe = onSwipe,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal class CardItemProvider<T>(
    private val itemsState: State<List<LazyCardItemContent<T>>>,
    private val state: SwipeableCardsState,
    private val properties: SwipeableCardsProperties,
    private val factors: SwipeableCardsFactors,
    private val onSwipe: (T, SwipeableCardDirection) -> Unit,
) : LazyLayoutItemProvider {

    override val itemCount: Int
        get() = itemsState.value.size

    @Composable
    override fun Item(index: Int, key: Any) {
        val item = itemsState.value.getOrNull(index)
        val scale = factors.scaleFactor(index, state, properties)
        val isDraggable = if (properties.lockBelowCardDragging) {
            index == state.currentCardIndex
        } else {
            true
        }

        SwipeableCard(
            properties = properties,
            draggable = isDraggable,
            scale = scale,
            onSwipe = { direction ->
                state.moveNext()
                item?.let { cardItem -> onSwipe(cardItem.item, direction) }
            },
        ) {
            item?.itemContent?.invoke(item.item, index)
        }
    }

    fun getItem(index: Int): T? {
        return itemsState.value.getOrNull(index)?.item
    }
}
