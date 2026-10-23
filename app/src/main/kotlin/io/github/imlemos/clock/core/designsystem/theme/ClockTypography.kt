package io.github.imlemos.clock.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.imlemos.clock.R

/** Variable fonts bundled in res/font (licences in licenses/fonts). */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight, vararg extra: FontVariation.Setting) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), *extra),
)

/** Doto: dot-matrix digits, used only for times. ROND=100 gives round dots. */
@OptIn(ExperimentalTextApi::class)
val DotoFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.SemiBold, FontWeight.Bold, FontWeight.Black).map { weight ->
        variableFont(R.font.doto, weight, FontVariation.Setting("ROND", 100f))
    },
)

/** JetBrains Mono: every other piece of text. */
val MonoFamily = FontFamily(
    listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.Bold).map { weight ->
        variableFont(R.font.jetbrains_mono, weight)
    },
)

@Immutable
data class ClockTypography(
    /** Hero time (stopwatch, alarm screen). */
    val timeDisplay: TextStyle,
    /** Time on list cards. */
    val timeLarge: TextStyle,
    val timeSmall: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

private val TrimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

fun clockTypography(): ClockTypography {
    val time = TextStyle(fontFamily = DotoFamily, fontWeight = FontWeight.Bold, lineHeightStyle = TrimmedLineHeight)
    val mono = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Normal)
    return ClockTypography(
        timeDisplay = time.copy(fontSize = 80.sp, lineHeight = 80.sp, letterSpacing = (-0.02).em),
        timeLarge = time.copy(fontSize = 48.sp, lineHeight = 48.sp),
        timeSmall = time.copy(fontSize = 24.sp, lineHeight = 24.sp),
        headline = mono.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
        title = mono.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        body = mono.copy(fontSize = 14.sp, lineHeight = 20.sp),
        label = mono.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.04.em),
        caption = mono.copy(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.04.em),
    )
}

/** Material components read MaterialTheme.typography; route them to the mono family. */
internal fun ClockTypography.toMaterial(): Typography = Typography(
    displayLarge = timeDisplay,
    displayMedium = timeLarge,
    displaySmall = timeSmall,
    headlineLarge = headline,
    headlineMedium = headline,
    headlineSmall = title,
    titleLarge = title,
    titleMedium = title.copy(fontSize = 16.sp),
    titleSmall = label,
    bodyLarge = body.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = label.copy(fontSize = 14.sp),
    labelMedium = label,
    labelSmall = caption,
)

internal val LocalClockTypography = staticCompositionLocalOf { clockTypography() }
