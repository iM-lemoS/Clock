package io.github.imlemos.clock.timer.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.component.DotDigits
import io.github.imlemos.clock.core.designsystem.component.DotGrid
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.glass.GlassSurface
import io.github.imlemos.clock.core.designsystem.glass.glassBackdropSource
import io.github.imlemos.clock.core.designsystem.glass.rememberGlassBackdrop
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.timer.formatDuration
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/** "Time's up": how long ago it ended, the timers' names, Stop and +1 min. */
@Composable
fun TimerRingingScreen(timers: List<RingingTimer>, onStop: () -> Unit, onAddMinute: () -> Unit) {
    val colors = ClockTheme.colors
    val backdrop = rememberGlassBackdrop()
    val since = timers.minOf { it.since }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(250)
        }
    }
    val stopLabel = stringResource(R.string.timer_stop)
    val addLabel = stringResource(R.string.timer_add_minute)
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(stopLabel) { onStop(); true },
                    CustomAccessibilityAction(addLabel) { onAddMinute(); true },
                )
            },
    ) {
        DotGrid(Modifier.fillMaxSize().glassBackdropSource(backdrop), fadeStart = 0f)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.timer_done_title), style = ClockTheme.typography.headline, color = colors.content)
            Spacer(Modifier.height(16.dp))
            // Counts up from the end, like a clock running past zero.
            DotDigits("-" + formatDuration(Duration.between(since, now).toMillis().coerceAtLeast(0)), style = ClockTheme.typography.timeDisplay, color = colors.accent)
            Spacer(Modifier.height(24.dp))
            timers.forEach { timer ->
                Text(timer.name, style = ClockTheme.typography.title, color = colors.content, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1.3f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CapsuleShape)
                        .clickable(role = Role.Button, onClick = onAddMinute)
                        .heightIn(min = 72.dp),
                ) {
                    Text(addLabel, style = ClockTheme.typography.title, color = colors.content)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(CapsuleShape)
                        .background(colors.accent, CapsuleShape)
                        .clickable(role = Role.Button, onClick = onStop)
                        .heightIn(min = 72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stopLabel, style = ClockTheme.typography.title, color = colors.onAccent)
                }
            }
        }
    }
}
