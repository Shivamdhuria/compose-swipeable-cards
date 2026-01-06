package com.spartapps.swipeablecards.ui.pagecurl

/**
 * Configuration for the page curl effect.
 *
 * @property curlRadius The radius of the curl cylinder in normalized coordinates.
 *                      Typical range is 0.05 to 0.3. Lower values create tighter curls.
 *                      Default is 0.15f.
 * @property shadowIntensity The intensity of shadows cast by the curling page.
 *                           Range 0.0 to 1.0. Default is 0.25f.
 */
data class PageCurlConfig(
    val curlRadius: Float = DEFAULT_CURL_RADIUS,
    val shadowIntensity: Float = DEFAULT_SHADOW_INTENSITY,
) {
    companion object {
        const val DEFAULT_CURL_RADIUS = 0.15f
        const val DEFAULT_SHADOW_INTENSITY = 0.25f
    }
}
