package io.github.imlemos.clock.timer

import io.github.imlemos.clock.core.designsystem.rememberHaptics
import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.component.DotDigits
import io.github.imlemos.clock.core.designsystem.component.EmptyState
import io.github.imlemos.clock.core.designsystem.component.OptionsSheet
import io.github.imlemos.clock.core.designsystem.component.SheetOption
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.scheduling.TimerMath
import io.github.imlemos.clock.core.scheduling.TimerStatus
import io.github.imlemos.clock.navigation.TabScreen
import io.github.imlemos.clock.timer.data.Timer
import kotlinx.coroutines.delay

/** Saved timers (docs/PLANO.md 4): several can run at once; each card counts down live. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimersScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onEditTimer: (Long) -> Unit,
    viewModel: TimersViewModel = hiltViewModel(),
) {
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    var options by remember { mutableStateOf<Timer?>(null) }
    // One clock for every card, ticking only while something runs.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val anyRunning = timers.orEmpty().any { it.status == TimerStatus.RUNNING || it.status == TimerStatus.RINGING }
    LaunchedEffect(anyRunning) {
        while (anyRunning) {
            now = SystemClock.elapsedRealtime()
            delay(100)
        }
        now = SystemClock.elapsedRealtime()
    }

    TabScreen(stringResource(R.string.nav_timers), contentPadding, onOpenSettings) {
        val list = timers ?: return@TabScreen
        if (list.isEmpty()) {
            EmptyState(
                digits = stringResource(R.string.timers_empty_digits),
                title = stringResource(R.string.timers_empty_title),
                body = stringResource(R.string.timers_empty_body),
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { timer ->
                    TimerCard(
                        modifier = Modifier.animateItem(),
                        timer = timer,
                        now = now,
                        onClick = { if (timer.status == TimerStatus.IDLE) onEditTimer(timer.id) else options = timer },
                        onLongClick = { options = timer },
                        onStart = { viewModel.start(timer.id) },
                        onPause = { viewModel.pause(timer.id) },
                        onReset = { viewModel.reset(timer.id) },
                        onAddMinute = { viewModel.addMinute(timer.id) },
                        onStop = { viewModel.stop(timer.id) },
                    )
                }
            }
        }
    }

    options?.let { timer ->
        OptionsSheet(
            title = timer.displayName(),
            options = listOf(
                SheetOption(stringResource(R.string.timer_edit), R.drawable.ic_edit) { options = null; onEditTimer(timer.id) },
                SheetOption(stringResource(R.string.timer_delete), R.drawable.ic_delete, destructive = true) {
                    options = null
                    viewModel.delete(timer.id)
                },
            ),
            onDismiss = { options = null },
        )
    }
}

@Composable
fun Timer.displayName(): String = name.ifBlank { stringResource(R.string.timer_default_name, formatDuration(run.durationMillis)) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerCard(
    timer: Timer,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onAddMinute: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    val left = TimerMath.remaining(timer.run, now)
    val running = timer.status == TimerStatus.RUNNING
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .combinedClickable(
                onClickLabel = stringResource(R.string.timer_edit),
                onLongClickLabel = stringResource(R.string.layout_options),
                onClick = onClick,
                onLongClick = {
                    haptics.longPress()
                    onLongClick()
                },
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(timer.displayName(), style = ClockTheme.typography.body, color = colors.content, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                DotDigits(
                    formatDuration(left),
                    style = ClockTheme.typography.timeLarge,
                    color = if (timer.status == TimerStatus.IDLE) colors.contentMuted else colors.content,
                )
                val status = when (timer.status) {
                    TimerStatus.PAUSED -> stringResource(R.string.timer_status_paused)
                    TimerStatus.RINGING -> stringResource(R.string.timer_done_title)
                    else -> null
                }
                status?.let { Text(it, style = ClockTheme.typography.caption, color = colors.accent) }
            }
            if (timer.status != TimerStatus.IDLE && timer.status != TimerStatus.RINGING) {
                RoundButton(R.drawable.ic_replay, stringResource(R.string.timer_reset), accent = false, onClick = onReset)
                Spacer(Modifier.width(8.dp))
            }
            if (timer.status == TimerStatus.RINGING) {
                RoundButton(R.drawable.ic_close, stringResource(R.string.timer_stop), accent = true, onClick = onStop)
            } else if (running) {
                RoundButton(R.drawable.ic_pause, stringResource(R.string.timer_pause), accent = true, onClick = onPause)
            } else {
                RoundButton(R.drawable.ic_play, stringResource(R.string.timer_start), accent = true, onClick = onStart)
            }
        }
        if (timer.status != TimerStatus.IDLE) {
            Spacer(Modifier.height(14.dp))
            // How much is left, as a thin bar.
            val fraction = (left.toFloat() / timer.run.durationMillis.coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(colors.outline)) {
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(colors.accent))
            }
            if (running) {
                Text(
                    stringResource(R.string.timer_add_minute),
                    style = ClockTheme.typography.label,
                    color = colors.accent,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable {
                            haptics.tick()
                            onAddMinute()
                        }
                        .padding(vertical = 6.dp),
                )
            }
        }
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
            .size(56.dp)
            .clip(CircleShape)
            .background(if (accent) colors.accent else colors.surfaceRaised, CircleShape),
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = if (accent) colors.onAccent else colors.content, modifier = Modifier.size(28.dp))
    }
}

