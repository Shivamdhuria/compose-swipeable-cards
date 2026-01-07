package com.spartapps.swipeablecards.ui.pagecurl

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Page curl shader using AGSL (Android Graphics Shading Language).
 * Implements cylinder mapping approach for realistic page curl effect.
 *
 * Based on: https://andrewhungblog.wordpress.com/2018/04/29/page-curl-shader-breakdown/
 *
 * Requires Android 13+ (API 33)
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object PageCurlShader {

    /**
     * Simple test shader - tint red to verify shader is running
     */
    private const val SHADER_SRC_TEST = """
        uniform shader iChannel0;

        vec4 main(vec2 fragCoord) {
            vec4 color = iChannel0.eval(fragCoord);
            // Tint red to verify shader is working
            color.r = color.r * 0.5 + 0.5;
            return color;
        }
    """

    /**
     * Page curl shader implementing cylinder mapping for book-like page flip effect.
     *
     * Uniforms:
     * - iChannel0: Source content shader
     * - iResolution: Canvas resolution (width, height)
     * - iMouse: Current drag position in pixels
     * - iMouseClick: Initial click/touch position in pixels
     * - radius: Curl cylinder radius (normalized, 0.1 = 10% of height)
     * - showCurlAxis: Whether to show the curl axis for debugging (1.0 = true, 0.0 = false)
     */
    private const val SHADER_SRC = """
        uniform shader iChannel0;   // Current page content
        uniform vec2 iResolution;
        uniform vec2 iMouse;        // Current drag position (xy)
        uniform vec2 iMouseClick;   // Initial click position (zw in ShaderToy)
        uniform float radius;
        uniform float showCurlAxis; // 1.0 to show axis, 0.0 to hide

        const float PI = 3.14159265359;

        vec4 main(vec2 fragCoord) {
            float aspect = iResolution.x / iResolution.y;

            // Early exit: if no drag has started, show original content
            vec2 dirVec = abs(iMouseClick) - iMouse;
            if (length(dirVec) < 1.0) {
                return iChannel0.eval(fragCoord);
            }

            // Flip Y to match ShaderToy coordinate system (Y=0 at bottom)
            vec2 flippedCoord = vec2(fragCoord.x, iResolution.y - fragCoord.y);
            vec2 flippedMouse = vec2(iMouse.x, iResolution.y - iMouse.y);
            vec2 flippedClick = vec2(iMouseClick.x, iResolution.y - iMouseClick.y);

            // Normalize to aspect-corrected coordinates (0 to aspect, 0 to 1)
            vec2 uv = flippedCoord * vec2(aspect, 1.0) / iResolution.xy;
            vec2 mouse = flippedMouse * vec2(aspect, 1.0) / iResolution.xy;

            // Direction from current mouse to click position (exactly like ShaderToy)
            vec2 mouseDir = normalize(abs(flippedClick) - flippedMouse);

            // Origin: where mouseDir line intersects left edge (x=0)
            vec2 origin = clamp(
                mouse - mouseDir * mouse.x / mouseDir.x,
                0.0,
                1.0
            );

            // Distance of mouse along direction from origin
            float mouseDist = clamp(
                length(mouse - origin) +
                (aspect - (abs(flippedClick.x) / iResolution.x) * aspect) / mouseDir.x,
                0.0,
                aspect / mouseDir.x
            );

            if (mouseDir.x < 0.0) {
                mouseDist = distance(mouse, origin);
            }

            // Project current pixel onto curl direction
            float proj = dot(uv - origin, mouseDir);
            float dist = proj - mouseDist;

            // Point on curl axis closest to this pixel
            vec2 linePoint = uv - dist * mouseDir;

            // === THREE REGIONS ===

            if (dist > radius) {
                // REGION 1: Beyond the curl - show shadow fading onto next page
                float shadowDist = dist - radius;
                float shadowFalloff = radius * 0.5;  // Shadow extends this far

                if (shadowDist < shadowFalloff) {
                    float shadowStrength = (1.0 - shadowDist / shadowFalloff) * 0.25;
                    return vec4(0.0, 0.0, 0.0, shadowStrength);
                }

                // Beyond shadow - fully transparent
                return vec4(0.0, 0.0, 0.0, 0.0);
            }
            else if (dist >= 0.0) {
                // REGION 2: On the cylinder curl
                float theta = asin(dist / radius);
                vec2 p2 = linePoint + mouseDir * (PI - theta) * radius;  // back of page
                vec2 p1 = linePoint + mouseDir * theta * radius;         // front of page

                // Check if p2 (back) is in bounds
                bool showBack = (p2.x <= aspect && p2.y <= 1.0 && p2.x > 0.0 && p2.y > 0.0);

                if (showBack) {
                    // Back of page is pure white
                    return vec4(1.0, 1.0, 1.0, 1.0);
                }

                // Front of page on the curl
                vec2 tc = p1 * vec2(1.0 / aspect, 1.0);
                tc.y = 1.0 - tc.y;
                vec4 color = iChannel0.eval(tc * iResolution);

                // Shadow on front half of curl - based on distance to white back edge
                // Calculate how far p2 is outside bounds (same method as REGION 3)
                float edgeDist = 0.0;
                if (p2.x > aspect) edgeDist = max(edgeDist, p2.x - aspect);
                if (p2.x < 0.0) edgeDist = max(edgeDist, -p2.x);
                if (p2.y > 1.0) edgeDist = max(edgeDist, p2.y - 1.0);
                if (p2.y < 0.0) edgeDist = max(edgeDist, -p2.y);

                // Shadow fades based on distance from where back would appear
                float shadowRadius = radius;
                if (edgeDist < shadowRadius) {
                    float shadowStrength = (1.0 - edgeDist / shadowRadius) * 0.15;
                    color.rgb *= 1.0 - shadowStrength;
                }

                return color;
            }
            else {
                // REGION 3: Behind the curl (flat part of curled page)
                vec2 p = linePoint + mouseDir * (abs(dist) + PI * radius);

                // Check if this point maps to valid page area (back of folded page)
                bool inBounds = (p.x <= aspect && p.y <= 1.0 && p.x > 0.0 && p.y > 0.0);

                if (inBounds) {
                    // Back of folded page is white
                    return vec4(1.0, 1.0, 1.0, 1.0);
                }

                // Front page content visible
                vec2 tc = uv * vec2(1.0 / aspect, 1.0);
                tc.y = 1.0 - tc.y;
                vec4 color = iChannel0.eval(tc * iResolution);

                // Shadow near the folded paper edge
                // Calculate distance from the edge of the folded paper
                float edgeDist = 0.0;
                if (p.x > aspect) edgeDist = max(edgeDist, p.x - aspect);
                if (p.x < 0.0) edgeDist = max(edgeDist, -p.x);
                if (p.y > 1.0) edgeDist = max(edgeDist, p.y - 1.0);
                if (p.y < 0.0) edgeDist = max(edgeDist, -p.y);

                // Shadow fades based on distance from folded paper edge
                // At edgeDist=0, shadow = 0.15 (matches REGION 2 at dist=0)
                float shadowRadius = radius;
                if (edgeDist < shadowRadius) {
                    float shadowStrength = (1.0 - edgeDist / shadowRadius) * 0.15;
                    color.rgb *= 1.0 - shadowStrength;
                }

                // Draw curl axis for debugging
                if (showCurlAxis > 0.5) {
                    float distToAxis = abs(dist);
                    float axisThickness = 0.003; // Line thickness in normalized coords
                    if (distToAxis < axisThickness) {
                        // Red line along the curl axis
                        return vec4(1.0, 0.0, 0.0, 1.0);
                    }
                }

                return color;
            }
        }
    """

    /**
     * Creates a new RuntimeShader instance with the page curl shader code.
     *
     * @param useTestShader If true, uses simple red tint shader for debugging
     * @return RuntimeShader instance ready to have uniforms set
     */
    fun create(useTestShader: Boolean = false): RuntimeShader =
        RuntimeShader(if (useTestShader) SHADER_SRC_TEST else SHADER_SRC)

    /**
     * Helper to set resolution uniform
     */
    fun RuntimeShader.setResolution(width: Float, height: Float) {
        setFloatUniform("iResolution", width, height)
    }

    /**
     * Helper to set mouse position (current drag position)
     * Coordinates are in pixels
     */
    fun RuntimeShader.setMouse(x: Float, y: Float) {
        setFloatUniform("iMouse", x, y)
    }

    /**
     * Helper to set initial click position
     * Coordinates are in pixels
     */
    fun RuntimeShader.setMouseClick(x: Float, y: Float) {
        setFloatUniform("iMouseClick", x, y)
    }

    /**
     * Helper to set curl radius
     * Radius is in normalized coordinates (0.0 - 0.3 typical range)
     */
    fun RuntimeShader.setRadius(radius: Float) {
        setFloatUniform("radius", radius)
    }

    /**
     * Helper to set whether to show the curl axis
     * @param show true to show the axis, false to hide it
     */
    fun RuntimeShader.setShowCurlAxis(show: Boolean) {
        setFloatUniform("showCurlAxis", if (show) 1.0f else 0.0f)
    }
}
