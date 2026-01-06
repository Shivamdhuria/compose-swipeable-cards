package com.spartapps.swipeablecards.ui.lazy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import com.spartapps.swipeablecards.state.SwipeableCardsState
import com.spartapps.swipeablecards.ui.SwipeableCardDirection
import com.spartapps.swipeablecards.ui.SwipeableCardsFactors
import com.spartapps.swipeablecards.ui.SwipeableCardsProperties
import kotlinx.coroutines.launch

/**
 * A composable that displays a stack of cards with page curl flip effect.
 *
 * Cards can be swiped left or right to flip through them like pages in a book.
 * Uses lazy composition for efficient rendering of large datasets.
 *
 * @param T The type of data backing the cards
 * @param modifier Modifier for the card stack container
 * @param state State holder controlling current card index and navigation
 * @param properties Configuration for thresholds, padding, and curl effect
 * @param factors Calculation factors for card positioning and scaling
 * @param onSwipe Callback when a card is swiped. Receives the item and swipe direction.
 * @param content DSL scope for defining card items
 *
 * Example:
 * ```
 * val state = rememberSwipeableCardsState()
 *
 * LazySwipeableCards(
 *     state = state,
 *     onSwipe = { item, direction ->
 *         println("Swiped ${item.name} to $direction")
 *     }
 * ) {
 *     items(myItems) { item, index ->
 *         Card { Text(item.name) }
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> LazySwipeableCards(
    modifier: Modifier = Modifier,
    state: SwipeableCardsState,
    properties: SwipeableCardsProperties = SwipeableCardsProperties(),
    factors: SwipeableCardsFactors = SwipeableCardsFactors(),
    onSwipe: (T, SwipeableCardDirection) -> Unit,
    content: LazySwipeableCardsScope<T>.() -> Unit,
) {
    val itemProvider = rememberItemProvider(
        state = state,
        properties = properties,
        factors = factors,
        onSwipe = onSwipe,
        customLazyListScope = content,
    )

    val indexes by state.visibleCardIndexes

    val animatables = remember {
        mutableStateMapOf<Int, Animatable<Offset, *>>()
    }

    LaunchedEffect(indexes) {
        indexes.forEach { index ->
            animatables.putIfAbsent(index, Animatable(Offset.Zero, Offset.VectorConverter))
        }

        animatables.forEach { index, animatable ->
            launch {
                animatable.animateTo(
                    targetValue = factors.cardOffsetCalculation(index, state, properties),
                    animationSpec = tween()
                )
            }
        }
    }

    LazyLayout(
        modifier = modifier
            .onGloballyPositioned { state.onSizeChange(it.size) }
            .padding(
                end = properties.padding,
                top = properties.padding.div(2)
            ),
        itemProvider = { itemProvider },
    ) { constraints ->

        val indexesWithPlaceables = indexes.associateWith {
            measure(it, constraints)
        }

        val maxHeight = indexesWithPlaceables.values
            .flatMap { it }
            .maxOfOrNull { it.height } ?: 0

        layout(width = constraints.maxWidth, height = maxHeight) {
            indexesWithPlaceables.forEach { (index, placeables) ->
                val item = itemProvider.getItem(index)
                item?.let {
                    placeables.forEach { placeable ->
                        placeable.placeRelative(
                            position = animatables[index]?.value?.round() ?: IntOffset.Zero,
                            zIndex = -index.toFloat(),
                        )
                    }
                }
            }
        }
    }
}
