package io.github.imlemos.clock.alarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.audio.AlarmVolume
import io.github.imlemos.clock.core.designsystem.component.ClockSwitch
import io.github.imlemos.clock.core.designsystem.component.DotTime
import io.github.imlemos.clock.core.designsystem.component.EmptyState
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.navigation.TabScreen

@Composable
fun AlarmsScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenReliability: () -> Unit,
    viewModel: AlarmListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reliabilityOk by viewModel.reliabilityOk.collectAsStateWithLifecycle()
    val volumeWarning by viewModel.volumeWarning.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshReliability()
        onPauseOrDispose { }
    }
    // The volume can change with the hardware keys while the list is open.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refreshVolume()
                delay(VOLUME_POLL_MS)
            }
        }
    }
    val showVolumeWarning = volumeWarning != AlarmVolume.Level.OK && state.items.any { it.nextRing != null }
    val bannerCount = listOf(!reliabilityOk, showVolumeWarning).count { it }
    TabScreen(stringResource(R.string.nav_alarms), PaddingValues(), onOpenSettings) {
        Column(Modifier.align(Alignment.TopCenter).zIndex(1f)) {
            if (!reliabilityOk) {
                WarningBanner(
                    title = stringResource(R.string.reliability_banner_title),
                    body = stringResource(R.string.reliability_banner_body),
                    onClick = onOpenReliability,
                )
            }
            if (showVolumeWarning) {
                WarningBanner(
                    title = stringResource(
                        if (volumeWarning == AlarmVolume.Level.ZERO) R.string.volume_zero_title else R.string.volume_low_title,
                    ),
                    body = stringResource(
                        if (volumeWarning == AlarmVolume.Level.ZERO) R.string.volume_zero_body else R.string.volume_low_body,
                    ),
                    onClick = viewModel::raiseVolume,
                )
            }
        }
        if (state.items.isEmpty()) {
            EmptyState(
                digits = stringResource(R.string.alarms_empty_digits),
                title = stringResource(R.string.alarms_empty_title),
                body = stringResource(R.string.alarms_empty_body),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(contentPadding),
            )
        } else {
            val listState = rememberLazyListState()
            // A new alarm that rings first lands at index 0; Lazy lists keep the previous first item
            // in view, so bring the new one into sight.
            val firstId = state.items.firstOrNull()?.alarm?.id
            LaunchedEffect(firstId) {
                if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp + 96.dp * bannerCount,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.alarm.id }) { item ->
                    AlarmCard(
                        item = item,
                        state = state,
                        onClick = { onEditAlarm(item.alarm.id) },
                        onToggle = { viewModel.setEnabled(item.alarm.id, it) },
                        onSkip = { viewModel.setSkipNext(item.alarm.id, !item.skipped) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(
    item: AlarmListItem,
    state: AlarmListState,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val colors = ClockTheme.colors
    val alarm = item.alarm
    val parts = timeParts(context, alarm.time)
    val enabled = item.nextRing != null
    val locale = LocalConfiguration.current.locales[0]
    val resources = LocalResources.current
    val today = state.now?.toLocalDate() ?: java.time.LocalDate.now()
    val nextText = when {
        item.nextRing != null && state.now != null -> describeNextRing(resources, state.now, item.nextRing, locale)
        else -> stringResource(R.string.alarm_off)
    }
    val subtitle = describeRecurrence(resources, alarm.recurrence, locale, today) + " · " + nextText
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .clickable(onClickLabel = stringResource(R.string.action_edit_alarm), onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            DotTime(
                hours = parts.hours,
                minutes = parts.minutes,
                dayPeriod = parts.dayPeriod,
                contentDescription = formatTimeOfDay(context, alarm.time),
                color = if (enabled) colors.content else colors.contentMuted,
            )
            Spacer(Modifier.height(8.dp))
            if (alarm.label.isNotBlank()) {
                Text(alarm.label, style = ClockTheme.typography.body, color = colors.content, maxLines = 1)
            }
            Text(subtitle, style = ClockTheme.typography.caption, color = colors.contentMuted)
            if (item.canSkip) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(if (item.skipped) R.string.alarm_unskip else R.string.alarm_skip_next),
                    style = ClockTheme.typography.label,
                    color = colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onSkip)
                        .padding(vertical = 6.dp),
                )
                if (item.skipped) {
                    Text(stringResource(R.string.alarm_next_skipped), style = ClockTheme.typography.caption, color = colors.content)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        ClockSwitch(checked = alarm.enabled, onCheckedChange = onToggle)
    }
}

private const val VOLUME_POLL_MS = 1_500L

@Composable
private fun WarningBanner(title: String, body: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ClockTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.background, shape)
            .border(1.dp, colors.accent, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = colors.accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = ClockTheme.typography.body, color = colors.content)
            Text(body, style = ClockTheme.typography.caption, color = colors.contentMuted)
        }
    }
}
