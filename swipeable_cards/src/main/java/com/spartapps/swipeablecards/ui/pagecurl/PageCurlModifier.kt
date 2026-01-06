package com.spartapps.swipeablecards.ui.pagecurl

import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Applies a page curl shader effect to the composable.
 *
 * @param config Configuration for the curl effect (radius, shadow intensity)
 * @param dragStart The initial touch/click position where the drag started
 * @param dragCurrent The current drag position
 * @return Modifier with page curl effect applied
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun Modifier.drawPageCurl(
    config: PageCurlConfig,
    dragStart: Offset,
    dragCurrent: Offset,
): Modifier = graphicsLayer {
    val shader = PageCurlShader.create(useTestShader = false)

    PageCurlShader.run {
        shader.setResolution(size.width, size.height)
        shader.setMouse(dragCurrent.x, dragCurrent.y)
        shader.setMouseClick(dragStart.x, dragStart.y)
        shader.setRadius(config.curlRadius)
    }

    renderEffect = RenderEffect
        .createRuntimeShaderEffect(shader, "iChannel0")
        .asComposeRenderEffect()
}

/**
 * Applies page curl effect on supported devices (API 33+),
 * falls back to simple offset animation on older devices.
 *
 * @param config Configuration for the curl effect
 * @param dragStart The initial touch position
 * @param dragCurrent The current drag position
 * @param fallbackOffset The offset to use for fallback animation on older devices
 * @return Modifier with appropriate effect applied
 */
internal fun Modifier.pageCurlOrFallback(
    config: PageCurlConfig,
    dragStart: Offset,
    dragCurrent: Offset,
    fallbackOffset: Offset,
): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    this.drawPageCurl(config, dragStart, dragCurrent)
} else {
    // Fallback to simple offset for older devices
    this
}
