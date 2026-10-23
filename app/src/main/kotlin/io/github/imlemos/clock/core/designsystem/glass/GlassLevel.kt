package io.github.imlemos.clock.core.designsystem.glass

import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf

/** How floating layers (navigation capsule, "+", sheets, menus) are rendered. */
enum class GlassLevel {
    /** Blur + edge refraction + specular rim. Needs AGSL runtime shaders (Android 13+). */
    Full,

    /** Blur and tint only. Android 12 has RenderEffect blur but no runtime shaders. */
    BlurOnly,

    /** Opaque surface with a hairline outline: "Reduce transparency", or the system asked for fewer effects. */
    Solid,
}

/**
 * Picks the richest level the device and the user's settings allow.
 *
 * @param reduceTransparency The user's "Reduce transparency" setting.
 * @param systemReducesEffects The system disabled blurs (battery saver, OEM or developer setting).
 */
fun resolveGlassLevel(
    reduceTransparency: Boolean,
    systemReducesEffects: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): GlassLevel = when {
    reduceTransparency || systemReducesEffects -> GlassLevel.Solid
    sdkInt >= Build.VERSION_CODES.TIRAMISU -> GlassLevel.Full
    else -> GlassLevel.BlurOnly
}

val LocalGlassLevel = staticCompositionLocalOf { GlassLevel.Solid }
