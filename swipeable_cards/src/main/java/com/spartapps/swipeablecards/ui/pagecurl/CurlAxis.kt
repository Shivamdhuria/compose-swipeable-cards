package com.spartapps.swipeablecards.ui.pagecurl

import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Represents the curl axis for page curl effect.
 *
 * @property origin The intersection point with the left edge (normalized 0-1 coordinates)
 * @property direction The normalized direction vector
 * @property distance The distance along the axis from the origin (normalized)
 */
internal data class CurlAxis(
    val origin: Offset,
    val direction: Offset,
    val distance: Float
) {
    companion object {
        /**
         * A curl axis with all values set to zero (no curl).
         */
        val ZERO = CurlAxis(Offset.Zero, Offset.Zero, 0f)

        /**
         * Vector converter for use with Compose Animatable.
         * Converts between CurlAxis and a 4D animation vector.
         * Direction is stored as an angle (radians).
         */
        val VectorConverter = TwoWayConverter<CurlAxis, AnimationVector4D>(
            convertToVector = { axis ->
                val angle = atan2(axis.direction.y, axis.direction.x)
                AnimationVector4D(
                    axis.origin.x,
                    axis.origin.y,
                    angle,
                    axis.distance
                )
            },
            convertFromVector = { vector ->
                val direction = Offset(cos(vector.v3), sin(vector.v3))
                CurlAxis(
                    origin = Offset(vector.v1, vector.v2),
                    direction = direction,
                    distance = vector.v4
                )
            }
        )
    }
}

/**
 * Helper extension to normalize an Offset as a direction vector.
 */
internal fun Offset.normalized(): Offset {
    val length = sqrt(x * x + y * y)
    return if (length > 0f) {
        Offset(x / length, y / length)
    } else {
        Offset.Zero
    }
}
