package com.spartapps.swipeablecards.state

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntSize
import com.spartapps.swipeablecards.ui.SwipeableCardDirection
import com.spartapps.swipeablecards.ui.SwipeableCardsDefaults
import com.spartapps.swipeablecards.ui.pagecurl.CurlState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val TAG = "SwipeableCardsState"

/**
 * Direction of the curl animation.
 */
internal enum class CurlDirection {
    /** Left swipe - curl page away (forward) */
    FORWARD,
    /** Right swipe - uncurl previous page (backward) */
    BACKWARD
}

/**
 * Manages the state of a SwipeableCards stack.
 *
 * This class maintains the current position in the card stack and handles navigation
 * between cards while enforcing boundaries and tracking navigation possibilities.
 * Also manages all page curl animation state and logic.
 */
class SwipeableCardsState(
    val visibleCardsInStack: Int = SwipeableCardsDefaults.VISIBLE_CARDS_IN_STACK,
    initialCardIndex: Int = 0,
    private val itemCount: () -> Int,
) {

    /**
     * The size of the container holding the swipeable cards.
     * Used for calculating proper animation boundaries and card positioning.
     */
    var size by mutableStateOf(IntSize.Zero)
        private set

    /**
     * Stores the current drag offsets for each card by index.
     * Used to track and animate multiple cards independently.
     */
    val dragOffsets = mutableStateMapOf<Int, Offset>()

    /**
     * The index of the currently displayed top card.
     * Read-only from outside the class, modified through navigation methods.
     */
    var currentCardIndex by mutableIntStateOf(initialCardIndex)
        private set

    /**
     * Tracks cards that are currently in a swiping animation.
     * Used to maintain proper rendering order during transitions.
     */
    val swipingVisibleCards = mutableStateListOf<Int>()

    /**
     * Indicates whether backwards navigation is possible (true if not at first card).
     */
    var canSwipeBack = derivedStateOf { currentCardIndex > 0 }
        private set

    /**
     * Tracks whether a backward swipe gesture is currently in progress.
     * When true, the previous card will be rendered for the uncurl animation.
     */
    internal var isBackwardSwipe by mutableStateOf(false)

    // ========== Curl Animation State ==========

    /**
     * Current state of the curl animation.
     */
    internal var curlState by mutableStateOf(CurlState.Idle)
        private set

    /**
     * Direction of the current curl (forward = left swipe, backward = right swipe).
     */
    internal var curlDirection by mutableStateOf<CurlDirection?>(null)
        private set

    /**
     * Accumulated horizontal drag distance in pixels.
     */
    internal var horizontalDrag by mutableFloatStateOf(0f)
        private set

    /**
     * Container width for curl calculations.
     */
    internal var containerWidth by mutableFloatStateOf(0f)

    /**
     * Curl drag start position (for shader).
     */
    internal var curlDragStart by mutableStateOf(Offset.Zero)

    /**
     * Curl drag current position (for shader).
     */
    internal var curlDragCurrent by mutableStateOf(Offset.Zero)

    /**
     * Animatable for drag start position.
     */
    internal val dragStartAnimatable = Animatable(Offset.Zero, Offset.VectorConverter)

    /**
     * Animatable for drag current position.
     */
    internal val dragCurrentAnimatable = Animatable(Offset.Zero, Offset.VectorConverter)

    /**
     * Tracks if haptic feedback was triggered for current gesture.
     */
    private var firstHaptic by mutableStateOf(true)

    val visibleCardIndexes = derivedStateOf {
        val baseRange = if (isBackwardSwipe && currentCardIndex > 0) {
            // Include previous card during backward swipe for uncurl animation
            (currentCardIndex - 1..minOf(currentCardIndex + visibleCardsInStack - 1, itemCount() - 1))
        } else {
            (currentCardIndex..minOf(currentCardIndex + visibleCardsInStack - 1, itemCount() - 1))
        }
        val result = baseRange.toList() + swipingVisibleCards
        Log.d(TAG, "📋 VISIBLE CARDS - isBackwardSwipe=$isBackwardSwipe, currentIndex=$currentCardIndex, visible=$result")
        result
    }

    internal fun onDragOffsetChange(
        index: Int,
        offset: Offset,
    ) {
        dragOffsets[index] = offset
    }

    internal fun onSizeChange(size: IntSize) {
        this.size = size
    }

    /**
     * Goes back to the previous card in the stack.
     * Has no effect if already at the first card.
     * Updates [canSwipeBack] based on the new position.
     */
    fun goBack() {
        swipingVisibleCards.remove(currentCardIndex)
        if (currentCardIndex > 0) {
            currentCardIndex--
            dragOffsets.remove(currentCardIndex)
            swipingVisibleCards.remove(currentCardIndex)
        }
        isBackwardSwipe = false
        curlDragStart = Offset.Zero
        curlDragCurrent = Offset.Zero
    }

    /**
     * Moves to the next card in the stack.
     * Has no effect if already at the last card.
     * Updates [canSwipeBack] based on the new position.
     */
    fun moveNext() {
        swipingVisibleCards.remove(currentCardIndex - 1)
        if (currentCardIndex < itemCount()) {
            currentCardIndex++
        }
        isBackwardSwipe = false
        curlDragStart = Offset.Zero
        curlDragCurrent = Offset.Zero
    }

    /**
     * Programmatically swipes the current top card in the specified direction.
     * This will animate the card off-screen and advance to the next card.
     *
     * @param direction The direction to swipe the card ([SwipeableCardDirection.Left] or [SwipeableCardDirection.Right]).
     */
    fun swipe(direction: SwipeableCardDirection) {
        val targetX = when (direction) {
            SwipeableCardDirection.Left -> -size.width.toFloat() * 1.5f
            SwipeableCardDirection.Right -> size.width.toFloat() * 1.5f
        }

        swipingVisibleCards.add(currentCardIndex)
        dragOffsets[currentCardIndex] = Offset(targetX, 0f)
        moveNext()
    }

    /**
     * Sets the current card index in the stack.
     * Clears any drag offsets associated with the previous card.
     * If the index is out of bounds, the method does nothing.
     * @param index The index to set as the current card.
     */
    fun setCurrentIndex(index: Int) {
        if (index in 0..<itemCount()) {
            currentCardIndex = index
            dragOffsets.clear()
        }
    }

    // ========== Curl Animation Methods ==========

    /**
     * Called when drag gesture starts.
     */
    internal suspend fun onCurlDragStart(startOffset: Offset) {
        curlState = CurlState.Dragging
        curlDirection = null
        horizontalDrag = 0f
        // Use actual touch position for asymmetric curl effect
        val startPos = startOffset
        dragStartAnimatable.snapTo(startPos)
        dragCurrentAnimatable.snapTo(startPos)
        curlDragStart = startPos
        curlDragCurrent = startPos
    }

    /**
     * Called during drag gesture.
     */
    internal suspend fun onCurlDrag(
        dragAmount: Offset,
        draggingAcceleration: Float,
        isRtl: Boolean,
        thresholdPx: Float,
        enableHapticFeedback: Boolean,
        haptic: HapticFeedback?
    ) {
        val acceleratedX = dragAmount.x * draggingAcceleration
        horizontalDrag += if (isRtl) -acceleratedX else acceleratedX

        // Detect direction after accumulating some drag (10px threshold)
        if (curlDirection == null && horizontalDrag.absoluteValue > 10f) {
            curlDirection = if (horizontalDrag < 0) {
                CurlDirection.FORWARD
            } else {
                if (canSwipeBack.value) {
                    CurlDirection.BACKWARD
                } else {
                    null
                }
            }

            // Notify that backward swipe started
            if (curlDirection == CurlDirection.BACKWARD) {
                isBackwardSwipe = true
                Log.d(TAG, "🔙 BACKWARD SWIPE STARTED - currentIndex=$currentCardIndex, previousCard=${currentCardIndex - 1}")
            } else if (curlDirection == CurlDirection.FORWARD) {
                Log.d(TAG, "➡️ FORWARD SWIPE STARTED - currentIndex=$currentCardIndex")
            }
        }

        when (curlDirection) {
            CurlDirection.FORWARD -> {
                // Forward curl: start from actual touch position, move left
                val curlX = (dragStartAnimatable.value.x + horizontalDrag).coerceIn(0f, containerWidth)
                val newPos = Offset(
                    x = curlX,
                    y = dragStartAnimatable.value.y + dragAmount.y
                )
                dragCurrentAnimatable.snapTo(newPos)
                curlDragCurrent = newPos
            }
            CurlDirection.BACKWARD -> {
                // Backward curl: start from touch position, drag current follows finger
                val uncurlX = (dragStartAnimatable.value.x + horizontalDrag).coerceIn(0f, containerWidth)
                val newPos = Offset(
                    x = uncurlX,
                    y = dragStartAnimatable.value.y + dragAmount.y
                )
                dragCurrentAnimatable.snapTo(newPos)
                curlDragCurrent = newPos
                Log.d(TAG, "🔙 BACKWARD DRAG - uncurlX=$uncurlX, dragStart=${dragStartAnimatable.value}, dragCurrent=$newPos")
            }
            null -> {
                // Direction not yet determined or blocked
                if (!canSwipeBack.value && horizontalDrag > 0) {
                    horizontalDrag = 0f
                }
            }
        }

        // Haptic feedback
        if (enableHapticFeedback && haptic != null) {
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

    /**
     * Called when drag gesture ends.
     */
    internal fun onCurlDragEnd(
        thresholdPx: Float,
        onSwipeLeft: () -> Unit,
        onSwipeRight: () -> Unit
    ) {
        when (curlDirection) {
            CurlDirection.FORWARD -> {
                val draggedLeft = horizontalDrag < -thresholdPx
                if (draggedLeft) {
                    curlState = CurlState.Completing
                    // Will call onSwipeLeft after animation
                } else {
                    curlState = CurlState.Resetting
                }
            }
            CurlDirection.BACKWARD -> {
                val draggedRight = horizontalDrag > thresholdPx
                if (draggedRight) {
                    curlState = CurlState.Completing
                    // Will call onSwipeRight after animation
                } else {
                    curlState = CurlState.Resetting
                    isBackwardSwipe = false
                }
            }
            null -> {
                curlState = CurlState.Resetting
            }
        }
        firstHaptic = true
    }

    /**
     * Runs curl animation based on current state.
     * Should be called from LaunchedEffect(curlState).
     */
    internal suspend fun runCurlAnimation(
        scope: CoroutineScope,
        onSwipeLeft: () -> Unit,
        onSwipeRight: () -> Unit
    ) {
        when (curlState) {
            CurlState.Completing -> {
                when (curlDirection) {
                    CurlDirection.FORWARD -> {
                        // Animate to fully curled - move beyond left edge based on curl origin
                        dragCurrentAnimatable.animateTo(
                            targetValue = Offset(-containerWidth * 0.2f, dragCurrentAnimatable.value.y),
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 250f)
                        ) {
                            curlDragCurrent = this.value
                        }
                        onSwipeLeft()
                    }
                    CurlDirection.BACKWARD -> {
                        // Animate to fully uncurled - move beyond right edge
                        dragCurrentAnimatable.animateTo(
                            targetValue = Offset(containerWidth * 1.2f, dragCurrentAnimatable.value.y),
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                        ) {
                            curlDragCurrent = this.value
                        }
                        onSwipeRight()
                    }
                    null -> {}
                }

                // Reset state
                dragStartAnimatable.snapTo(Offset.Zero)
                dragCurrentAnimatable.snapTo(Offset.Zero)
                curlDragStart = Offset.Zero
                curlDragCurrent = Offset.Zero
                horizontalDrag = 0f
                curlState = CurlState.Idle
                curlDirection = null
            }

            CurlState.Resetting -> {
                // Animate back to start position
                dragCurrentAnimatable.animateTo(
                    targetValue = dragStartAnimatable.value,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
                ) {
                    if (isBackwardSwipe) {
                        curlDragStart = dragStartAnimatable.value
                        curlDragCurrent = this.value
                    }
                }

                // Reset state
                dragStartAnimatable.snapTo(Offset.Zero)
                dragCurrentAnimatable.snapTo(Offset.Zero)
                curlDragStart = Offset.Zero
                curlDragCurrent = Offset.Zero
                horizontalDrag = 0f
                curlState = CurlState.Idle
                curlDirection = null
            }

            else -> { /* Idle or Dragging */ }
        }
    }
}
