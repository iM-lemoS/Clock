package io.github.imlemos.clock.stopwatch

import io.github.imlemos.clock.core.designsystem.rememberHaptics
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.component.DotDigits
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.scheduling.StopwatchMath
import io.github.imlemos.clock.core.scheduling.StopwatchRun
import io.github.imlemos.clock.navigation.TabScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DecimalFormatSymbols
import javax.inject.Inject

@HiltViewModel
class StopwatchViewModel @Inject constructor(private val controller: StopwatchController) : ViewModel() {
    val run: StateFlow<StopwatchRun> = controller.run.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StopwatchRun())

    fun start() = viewModelScope.launch { controller.start() }

    fun pause() = viewModelScope.launch { controller.pause() }

    fun lap() = viewModelScope.launch { controller.lap() }

    fun reset() = viewModelScope.launch { controller.reset() }
}

/** The stopwatch (docs/PLANO.md 5): start, pause, laps (fastest and slowest marked), reset. */
@Composable
fun StopwatchScreen(contentPadding: PaddingValues, onOpenSettings: () -> Unit, viewModel: StopwatchViewModel = hiltViewModel()) {
    val run by viewModel.run.collectAsStateWithLifecycle()
    val colors = ClockTheme.colors
    val separator = DecimalFormatSymbols.getInstance(LocalConfiguration.current.locales[0]).decimalSeparator
    // Redraws every frame while running, for the hundredths.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(run.running) {
        now = SystemClock.elapsedRealtime()
        while (run.running) withFrameMillis { now = SystemClock.elapsedRealtime() }
    }
    val total = StopwatchMath.elapsed(run, now)
    val extremes = StopwatchMath.extremes(run)

    TabScreen(stringResource(R.string.nav_stopwatch), contentPadding, onOpenSettings) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            DotDigits(
                formatStopwatch(total, separator),
                style = ClockTheme.typography.timeDisplay,
                color = colors.content,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(32.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: lap while running, reset while paused (nothing at zero).
                when {
                    run.running -> RoundButton(R.drawable.ic_flag, stringResource(R.string.stopwatch_lap), accent = false, onClick = viewModel::lap)
                    total > 0 -> RoundButton(R.drawable.ic_replay, stringResource(R.string.stopwatch_reset), accent = false, onClick = viewModel::reset)
                    else -> Spacer(Modifier.size(80.dp))
                }
                if (run.running) {
                    RoundButton(R.drawable.ic_pause, stringResource(R.string.stopwatch_pause), accent = true, onClick = viewModel::pause)
                } else {
                    RoundButton(
                        R.drawable.ic_play,
                        stringResource(if (total > 0) R.string.stopwatch_resume else R.string.stopwatch_start),
                        accent = true,
                        onClick = viewModel::start,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            val laps = StopwatchMath.laps(run)
            // The lap in progress is shown on top while running.
            val current = if (run.running && laps.isNotEmpty()) total - run.laps.last() else null
            if (laps.isEmpty() && current == null) {
                Text(stringResource(R.string.stopwatch_empty_title), style = ClockTheme.typography.caption, color = colors.contentMuted)
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
                current?.let {
                    item(key = "current") { LapRow(laps.size + 1, it, total, note = null, highlight = false, separator = separator) }
                }
                items(laps, key = { it.number }) { lap ->
                    val note = when (lap.number) {
                        extremes?.first -> stringResource(R.string.stopwatch_fastest)
                        extremes?.second -> stringResource(R.string.stopwatch_slowest)
                        else -> null
                    }
                    LapRow(
                        lap.number, lap.durationMillis, lap.totalMillis, note,
                        highlight = lap.number == extremes?.second, separator = separator, modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LapRow(number: Int, duration: Long, total: Long, note: String?, highlight: Boolean, separator: Char, modifier: Modifier = Modifier) {
    val colors = ClockTheme.colors
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.stopwatch_lap_number, number), style = ClockTheme.typography.body, color = colors.content)
            note?.let { Text(it, style = ClockTheme.typography.caption, color = if (highlight) colors.accent else colors.contentMuted) }
        }
        Text(formatStopwatch(duration, separator), style = ClockTheme.typography.body, color = if (highlight) colors.accent else colors.content)
        Spacer(Modifier.width(20.dp))
        Text(formatStopwatch(total, separator), style = ClockTheme.typography.body, color = colors.contentMuted)
    }
}

@Composable
private fun RoundButton(icon: Int, label: String, accent: Boolean, onClick: () -> Unit) {
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    IconButton(
        // The main action (start, pause, stop) confirms; reset and lap tick.
        onClick = {
            if (accent) haptics.confirm() else haptics.tick()
            onClick()
        },
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(if (accent) colors.accent else colors.surfaceRaised, CircleShape)
            .semantics { contentDescription = label },
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (accent) colors.onAccent else colors.content, modifier = Modifier.size(36.dp))
    }
}
