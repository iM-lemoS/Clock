package io.github.imlemos.clock.core.designsystem.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.FloatingNavBar
import io.github.imlemos.clock.core.designsystem.component.GlassIconButton
import io.github.imlemos.clock.core.designsystem.component.NavBarItem
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.theme.AccentPalette
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/**
 * Debug-only catalog: the glass components over colourful scrolling content, with the level
 * forced to each option. Reached from Settings > Developer in debug builds.
 */
@Composable
fun GlassLabScreen(onBack: () -> Unit) {
    val automatic = LocalGlassLevel.current
    var forced by rememberSaveable { mutableStateOf<GlassLevel?>(null) }
    val backdrop = rememberGlassBackdrop()
    val colors = ClockTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        ClockTopBar(
            title = stringResource(R.string.settings_glass_lab),
            navigationIcon = { TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) },
        )
        LevelSelector(automatic = automatic, forced = forced, onSelect = { forced = it })

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 12.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .glassBackdropSource(backdrop),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 200.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(AccentPalette.entries.size * 2) { index ->
                    SampleCard(index)
                }
            }
            CompositionLocalProvider(LocalGlassLevel provides (forced ?: automatic)) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlassIconButton(backdrop, R.drawable.ic_add, stringResource(R.string.action_add_alarm), onClick = {})
                    FloatingNavBar(
                        backdrop = backdrop,
                        items = listOf(
                            NavBarItem(0, R.drawable.ic_alarm, stringResource(R.string.nav_alarms)),
                            NavBarItem(1, R.drawable.ic_hourglass_empty, stringResource(R.string.nav_timers)),
                            NavBarItem(2, R.drawable.ic_timer, stringResource(R.string.nav_stopwatch)),
                        ),
                        selectedKey = 0,
                        onSelect = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelSelector(automatic: GlassLevel, forced: GlassLevel?, onSelect: (GlassLevel?) -> Unit) {
    val colors = ClockTheme.colors
    val options = listOf(
        null to stringResource(R.string.glass_level_auto, stringResource(automatic.label)),
        GlassLevel.Full to stringResource(GlassLevel.Full.label),
        GlassLevel.BlurOnly to stringResource(GlassLevel.BlurOnly.label),
        GlassLevel.Solid to stringResource(GlassLevel.Solid.label),
    )
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (level, label) ->
            val isSelected = level == forced
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(CapsuleShape)
                    .border(1.dp, colors.outline, CapsuleShape)
                    .background(if (isSelected) colors.content else colors.background, CapsuleShape)
                    .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(level) })
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = ClockTheme.typography.caption,
                    color = if (isSelected) colors.background else colors.content,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun SampleCard(index: Int) {
    val palette = AccentPalette.entries
    val a = palette[index % palette.size].color
    val b = palette[(index + 2) % palette.size].color
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(a, b))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Stripes and dots give the refraction something to bend.
            val step = 22.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(ClockStripe, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 4.dp.toPx())
                x += step
            }
        }
        Text(
            text = stringResource(R.string.glass_lab_digits),
            style = ClockTheme.typography.timeLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(20.dp),
        )
    }
}

private val ClockStripe = Color.White.copy(alpha = 0.25f)

private val GlassLevel.label: Int
    get() = when (this) {
        GlassLevel.Full -> R.string.glass_level_full
        GlassLevel.BlurOnly -> R.string.glass_level_blur
        GlassLevel.Solid -> R.string.glass_level_solid
    }
