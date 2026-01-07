package com.spartapps.swipeablecards.state

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import com.spartapps.swipeablecards.ui.pagecurl.CurlAxis
import com.spartapps.swipeablecards.ui.pagecurl.CurlState
import com.spartapps.swipeablecards.ui.pagecurl.normalized
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
     * Accumulated vertical drag distance in pixels.
     */
    internal var verticalDrag by mutableFloatStateOf(0f)
        private set

    /**
     * Container width for curl calculations.
     */
    internal var containerWidth by mutableFloatStateOf(0f)

    /**
     * Container height for curl calculations.
     */
    internal var containerHeight by mutableFloatStateOf(0f)

    /**
     * Current curl axis (calculated in Kotlin, passed to shader).
     */
    internal var curlAxis by mutableStateOf(CurlAxis.ZERO)
        private set

    /**
     * Animatable for curl axis (for completion/reset animations).
     */
    internal val curlAxisAnimatable = Animatable(CurlAxis.ZERO, CurlAxis.VectorConverter)

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
        verticalDrag = 0f
        // Use actual touch position for asymmetric curl effect
        val startPos = startOffset
        dragStartAnimatable.snapTo(startPos)
        dragCurrentAnimatable.snapTo(startPos)
        curlDragStart = startPos
        curlDragCurrent = startPos

        // Initialize curl axis to zero (no curl)
        curlAxis = CurlAxis.ZERO
        curlAxisAnimatable.snapTo(CurlAxis.ZERO)
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
        verticalDrag += dragAmount.y

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
                // Forward curl: current position = start + accumulated drag
                val newPos = Offset(
                    x = (dragStartAnimatable.value.x + horizontalDrag).coerceIn(0f, containerWidth),
                    y = dragStartAnimatable.value.y + verticalDrag
                )
                dragCurrentAnimatable.snapTo(newPos)
                curlDragCurrent = newPos

                // Calculate curl axis from drag positions
                curlAxis = calculateCurlAxis(
                    dragStart = dragStartAnimatable.value,
                    dragCurrent = newPos,
                    isBackward = false
                )
                curlAxisAnimatable.snapTo(curlAxis)
            }
            CurlDirection.BACKWARD -> {
                // Backward curl: current position = start + accumulated drag
                val newPos = Offset(
                    x = (dragStartAnimatable.value.x + horizontalDrag).coerceIn(0f, containerWidth),
                    y = dragStartAnimatable.value.y + verticalDrag
                )
                dragCurrentAnimatable.snapTo(newPos)
                curlDragCurrent = newPos
                Log.d(TAG, "🔙 BACKWARD DRAG - dragStart=${dragStartAnimatable.value}, dragCurrent=$newPos")

                // Calculate curl axis from drag positions (backward swipe)
                curlAxis = calculateCurlAxis(
                    dragStart = dragStartAnimatable.value,
                    dragCurrent = newPos,
                    isBackward = true
                )
                curlAxisAnimatable.snapTo(curlAxis)
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
        Log.d(TAG, "🛑 DRAG END - direction=$curlDirection, horizontalDrag=$horizontalDrag, threshold=$thresholdPx, curlAxis=$curlAxis")
        when (curlDirection) {
            CurlDirection.FORWARD -> {
                val draggedLeft = horizontalDrag < -thresholdPx
                if (draggedLeft) {
                    Log.d(TAG, "✅ Threshold crossed - COMPLETING")
                    curlState = CurlState.Completing
                    // Will call onSwipeLeft after animation
                } else {
                    Log.d(TAG, "❌ Threshold NOT crossed - RESETTING")
                    curlState = CurlState.Resetting
                }
            }
            CurlDirection.BACKWARD -> {
                val draggedRight = horizontalDrag > thresholdPx
                if (draggedRight) {
                    Log.d(TAG, "✅ Threshold crossed - COMPLETING")
                    curlState = CurlState.Completing
                    // Will call onSwipeRight after animation
                } else {
                    Log.d(TAG, "❌ Threshold NOT crossed - RESETTING")
                    curlState = CurlState.Resetting
                    // Don't set isBackwardSwipe=false here! It will be set after animation in runCurlAnimation
                }
            }
            null -> {
                Log.d(TAG, "⚠️ No direction detected - RESETTING")
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
                        // Complete curl by moving axis to left edge
                        // Direction should be horizontal (1, 0) to create vertical curl axis
                        val currentOriginY = curlAxis.origin.y
                        val targetAxis = CurlAxis(
                            origin = Offset(0f, currentOriginY),
                            direction = Offset(1f, 0f),  // Horizontal direction -> vertical curl axis
                            distance = containerWidth / containerHeight * 0.05f
                        )

                        curlAxisAnimatable.animateTo(
                            targetValue = targetAxis,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 250f)
                        ) {
                            curlAxis = this.value
                            // Also update old drag positions for backward compatibility
                            curlDragCurrent = Offset(containerWidth * 0.05f, containerHeight * currentOriginY)
                            curlDragStart = Offset(containerWidth * 1.5f, containerHeight * currentOriginY)
                        }
                        onSwipeLeft()
                    }
                    CurlDirection.BACKWARD -> {
                        // Complete unfold by moving distance to full width
                        // Origin stays at left edge, distance moves all the way right
                        val currentOriginY = curlAxis.origin.y
                        val aspect = containerWidth / containerHeight
                        val targetAxis = CurlAxis(
                            origin = Offset(0f, currentOriginY),  // Origin stays at left edge
                            direction = Offset(1f, 0f),  // Horizontal pointing right
                            distance = aspect * 1.1f  // Full distance across (slightly beyond for complete unfold)
                        )

                        curlAxisAnimatable.animateTo(
                            targetValue = targetAxis,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                        ) {
                            curlAxis = this.value
                            // Also update old drag positions for backward compatibility
                            curlDragCurrent = Offset(containerWidth * 0.95f, containerHeight * currentOriginY)
                            curlDragStart = Offset(-containerWidth * 0.5f, containerHeight * currentOriginY)
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
                curlAxis = CurlAxis.ZERO
                curlAxisAnimatable.snapTo(CurlAxis.ZERO)
                horizontalDrag = 0f
                verticalDrag = 0f
                curlState = CurlState.Idle
                curlDirection = null
            }

            CurlState.Resetting -> {
                Log.d(TAG, "🔄 RESETTING - curlDirection=$curlDirection, currentAxis=$curlAxis, horizontalDrag=$horizontalDrag")

                // Save direction before resetting
                val wasBackwardSwipe = curlDirection == CurlDirection.BACKWARD

                when (curlDirection) {
                    CurlDirection.FORWARD -> {
                        // For forward swipe reset: move curl past right edge to flatten
                        // Origin stays at left edge, but distance moves beyond page width
                        val aspect = containerWidth / containerHeight
                        val targetAxis = CurlAxis(
                            origin = Offset(0f, curlAxis.origin.y),
                            direction = Offset(1f, 0f),  // Keep horizontal
                            distance = aspect * 1.5f  // Move curl way past right edge
                        )

                        Log.d(TAG, "🔄 FORWARD RESET: moving curl from distance=${curlAxis.distance} to ${aspect * 1.5f} (off-screen)")
                        curlAxisAnimatable.animateTo(
                            targetValue = targetAxis,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
                        ) {
                            curlAxis = this.value
                            curlDragCurrent = dragStartAnimatable.value
                        }
                    }
                    CurlDirection.BACKWARD -> {
                        // For backward swipe reset: move curl back to left edge (fold it back)
                        // Keep isBackwardSwipe=true during animation so curl stays on previous page
                        // Use tween instead of spring to avoid overshoot that causes glitches
                        val targetAxis = CurlAxis(
                            origin = Offset(0f, curlAxis.origin.y),
                            direction = Offset(1f, 0f),
                            distance = 0f  // Move to left edge to hide previous page
                        )

                        Log.d(TAG, "🔄 BACKWARD RESET: moving curl from distance=${curlAxis.distance} to 0 (fold back)")
                        curlAxisAnimatable.animateTo(
                            targetValue = targetAxis,
                            animationSpec = tween(durationMillis = 150)  // Fast tween, no overshoot
                        ) {
                            curlAxis = this.value
                            curlDragCurrent = dragStartAnimatable.value
                            curlDragStart = dragStartAnimatable.value
                        }
                    }
                    null -> {
                        // No direction determined - just clear everything
                        curlAxis = CurlAxis.ZERO
                    }
                }

                // Reset state
                dragStartAnimatable.snapTo(Offset.Zero)
                dragCurrentAnimatable.snapTo(Offset.Zero)
                curlDragStart = Offset.Zero
                curlDragCurrent = Offset.Zero
                curlAxis = CurlAxis.ZERO
                curlAxisAnimatable.snapTo(CurlAxis.ZERO)
                horizontalDrag = 0f
                verticalDrag = 0f
                curlState = CurlState.Idle
                curlDirection = null

                // For backward reset, hide previous page only after ALL state is reset
                // This prevents glitching on the current card during recomposition
                if (wasBackwardSwipe) {
                    isBackwardSwipe = false
                }
            }

            else -> { /* Idle or Dragging */ }
        }
    }

    /**
     * Calculates the curl axis from drag positions.
     *
     * For forward swipes: Matches the shader's calculation logic exactly.
     * For backward swipes: Creates a simple vertical axis that moves right based on drag distance.
     *
     * @param dragStart Initial touch position in pixels
     * @param dragCurrent Current drag position in pixels
     * @param isBackward Whether this is a backward swipe (dragging right to show previous page)
     * @return CurlAxis with normalized coordinates
     */
    internal fun calculateCurlAxis(
        dragStart: Offset,
        dragCurrent: Offset,
        isBackward: Boolean = false
    ): CurlAxis {
        if (containerWidth <= 0f || containerHeight <= 0f) {
            return CurlAxis.ZERO
        }

        val aspect = containerWidth / containerHeight

        // BACKWARD SWIPE: Simple vertical axis that moves from left to right
        if (isBackward) {
            // Calculate how far we've dragged right (0.0 to 1.0+)
            val dragDistanceX = (dragCurrent.x - dragStart.x) / containerWidth
            val normalizedDragX = dragDistanceX.coerceIn(0f, 1f)

            // Y position based on initial touch point (middle of page if no specific touch point)
            val normalizedY = ((containerHeight - dragStart.y) / containerHeight).coerceIn(0f, 1f)

            // For unfold: origin stays at left edge, but distance increases as we drag right
            // This makes the paper unfold from left to right
            val originX = 0f  // Origin stays at left edge
            val distance = (normalizedDragX * aspect).coerceIn(0f, aspect)

            return CurlAxis(
                origin = Offset(originX, normalizedY),
                direction = Offset(1f, 0f),  // Horizontal pointing right (unfold direction)
                distance = distance  // How far from left edge the curl has moved
            )
        }

        // FORWARD SWIPE: Complex calculation matching shader logic
        // Convert to normalized coordinates (0-1), with Y flipped (shader uses Y-up)
        // and aspect-corrected X
        val mouse = Offset(
            x = (dragCurrent.x / containerWidth) * aspect,
            y = (containerHeight - dragCurrent.y) / containerHeight
        )
        val click = Offset(
            x = (dragStart.x / containerWidth) * aspect,
            y = (containerHeight - dragStart.y) / containerHeight
        )

        // Apply absolute value to flipped click (matches shader: abs(flippedClick))
        val absFlippedClick = Offset(
            x = kotlin.math.abs(click.x),
            y = kotlin.math.abs(click.y)
        )

        // Direction from current mouse to click position (matches shader)
        val dirVec = absFlippedClick - mouse
        val mouseDir = dirVec.normalized()

        if (mouseDir.x == 0f || mouseDir.x < 0f) {
            // Vertical drag or invalid direction - create simple axis
            return CurlAxis(
                origin = Offset(0f, mouse.y.coerceIn(0f, 1f)),
                direction = Offset(1f, 0f),
                distance = mouse.x.coerceAtLeast(0f)
            )
        }

        // Origin: where mouseDir line intersects left edge (x=0)
        // Solve: mouse + t * mouseDir where x = 0
        // 0 = mouse.x + t * mouseDir.x
        // t = -mouse.x / mouseDir.x
        val t = -mouse.x / mouseDir.x
        val originY = (mouse.y + mouseDir.y * t).coerceIn(0f, 1f)
        val origin = Offset(0f, originY)

        // Distance of mouse along direction from origin
        val baseDistance = (mouse - origin).getDistance()
        val clickOffsetTerm = (aspect - absFlippedClick.x) / mouseDir.x
        val maxDist = aspect / mouseDir.x

        // Only coerce if we have a valid range
        val mouseDist = if (maxDist > 0f) {
            (baseDistance + clickOffsetTerm).coerceIn(0f, maxDist)
        } else {
            baseDistance
        }

        return CurlAxis(
            origin = origin,
            direction = mouseDir,
            distance = mouseDist
        )
    }
}
