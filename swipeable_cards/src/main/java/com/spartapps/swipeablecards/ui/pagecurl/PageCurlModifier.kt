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
 * @param curlAxis The curl axis defining the curl effect
 * @return Modifier with page curl effect applied
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun Modifier.drawPageCurl(
    config: PageCurlConfig,
    curlAxis: CurlAxis,
): Modifier = graphicsLayer {
    val shader = PageCurlShader.create(useTestShader = false)

    PageCurlShader.run {
        shader.setResolution(size.width, size.height)
        shader.setCurlAxis(curlAxis)
        shader.setRadius(config.curlRadius)
        shader.setShowCurlAxis(config.showCurlAxis)
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
 * @param curlAxis The curl axis defining the curl effect
 * @param fallbackOffset The offset to use for fallback animation on older devices
 * @return Modifier with appropriate effect applied
 */
internal fun Modifier.pageCurlOrFallback(
    config: PageCurlConfig,
    curlAxis: CurlAxis,
    fallbackOffset: Offset,
): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    this.drawPageCurl(config, curlAxis)
} else {
    // Fallback to simple offset for older devices
    this
}
