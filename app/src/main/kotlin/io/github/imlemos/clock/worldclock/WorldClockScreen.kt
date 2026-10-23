package io.github.imlemos.clock.worldclock

import androidx.compose.foundation.layout.Box
import io.github.imlemos.clock.core.designsystem.rememberHaptics
import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.ui.formatTimeOfDay
import io.github.imlemos.clock.alarm.ui.timeParts
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.designsystem.component.DotTime
import io.github.imlemos.clock.core.designsystem.component.EmptyState
import io.github.imlemos.clock.core.designsystem.component.OptionsSheet
import io.github.imlemos.clock.core.designsystem.component.SheetOption
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import io.github.imlemos.clock.core.scheduling.WorldClockMath
import io.github.imlemos.clock.core.zones.ZoneNames
import io.github.imlemos.clock.core.zones.ZonePickerDialog
import io.github.imlemos.clock.navigation.TabScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class WorldClockViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val zones: StateFlow<List<ZoneId>?> = settings.worldClockZones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(zone: ZoneId) = update { if (zone in it) it else it + zone }

    fun remove(zone: ZoneId) = update { it - zone }

    /** Moves [zone] one place up ([up]) or down. */
    fun move(zone: ZoneId, up: Boolean) = update { list ->
        val index = list.indexOf(zone)
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in list.indices) list else list.toMutableList().apply { add(target, removeAt(index)) }
    }

    private fun update(transform: (List<ZoneId>) -> List<ZoneId>) {
        viewModelScope.launch { settings.setWorldClockZones(transform(zones.value.orEmpty())) }
    }
}

/**
 * The world clock (docs/PLANO.md 5.1): the time here and in each chosen city, with the difference,
 * day or night, and the date when it is another day. [addRequests] grows each time "+" is tapped.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorldClockScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    addRequests: Int,
    onNewAlarm: (ZoneId) -> Unit,
    viewModel: WorldClockViewModel = hiltViewModel(),
) {
    val zones by viewModel.zones.collectAsStateWithLifecycle()
    val colors = ClockTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    var picking by rememberSaveable { mutableStateOf(false) }
    var handledRequests by rememberSaveable { mutableIntStateOf(addRequests) }
    LaunchedEffect(addRequests) {
        if (addRequests != handledRequests) {
            handledRequests = addRequests
            picking = true
        }
    }
    var options by remember { mutableStateOf<ZoneId?>(null) }
    // Ticks at each minute boundary.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(60_000L - now.toEpochMilli() % 60_000L)
        }
    }
    val here = ZoneId.systemDefault()

    TabScreen(stringResource(R.string.world_title), contentPadding, onOpenSettings) {
        val list = zones ?: return@TabScreen
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "here") { HereCard(now.atZone(here), locale) }
            if (list.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        digits = stringResource(R.string.world_empty_digits),
                        title = stringResource(R.string.world_empty_title),
                        body = stringResource(R.string.world_empty_body),
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                }
            }
            items(list, key = { it.id }) { zone ->
                Box(Modifier.animateItem()) { CityCard(zone, here, now, locale, onClick = { options = zone }) }
            }
        }
    }

    if (picking) {
        ZonePickerDialog(
            title = stringResource(R.string.world_add_city),
            selected = null,
            noZoneLabel = null,
            noZoneSummary = null,
            onSelect = { zone ->
                zone?.let(viewModel::add)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
    options?.let { zone ->
        val city = ZoneNames.city(zone, locale)
        val index = zones.orEmpty().indexOf(zone)
        OptionsSheet(
            title = city,
            options = buildList {
                add(SheetOption(stringResource(R.string.world_new_alarm, city), R.drawable.ic_alarm) { options = null; onNewAlarm(zone) })
                if (index > 0) add(SheetOption(stringResource(R.string.world_move_up), R.drawable.ic_move_up) { viewModel.move(zone, up = true); options = null })
                if (index in 0 until zones.orEmpty().lastIndex) {
                    add(SheetOption(stringResource(R.string.world_move_down), R.drawable.ic_move_down) { viewModel.move(zone, up = false); options = null })
                }
                add(SheetOption(stringResource(R.string.world_remove), R.drawable.ic_delete, destructive = true) { viewModel.remove(zone); options = null })
            },
            onDismiss = { options = null },
        )
    }
}

/** The phone's own time, large. */
@Composable
private fun HereCard(now: ZonedDateTime, locale: java.util.Locale) {
    val colors = ClockTheme.colors
    val context = LocalContext.current
    val time = TimeOfDay(now.hour, now.minute)
    val parts = timeParts(context, time)
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DotTime(
            hours = parts.hours,
            minutes = parts.minutes,
            dayPeriod = parts.dayPeriod,
            contentDescription = formatTimeOfDay(context, time),
            style = ClockTheme.typography.timeDisplay,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.world_here, ZoneNames.city(now.zone, locale)) + " · " +
                DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(now),
            style = ClockTheme.typography.body,
            color = colors.contentMuted,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CityCard(zone: ZoneId, here: ZoneId, now: Instant, locale: java.util.Locale, onClick: () -> Unit) {
    val colors = ClockTheme.colors
    val context = LocalContext.current
    val resources = LocalResources.current
    val local = now.atZone(zone)
    val time = TimeOfDay(local.hour, local.minute)
    val parts = timeParts(context, time)
    val day = WorldClockMath.isDaytime(zone, now)
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .combinedClickable(onClickLabel = stringResource(R.string.layout_options), onClick = onClick, onLongClick = {
                haptics.longPress()
                onClick()
            })
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (day) R.drawable.ic_day else R.drawable.ic_night),
                    contentDescription = stringResource(if (day) R.string.world_day else R.string.world_night),
                    tint = colors.contentMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(ZoneNames.city(zone, locale), style = ClockTheme.typography.title, color = colors.content, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                describeDifference(resources, WorldClockMath.dayOffset(zone, here, now), WorldClockMath.differenceMinutes(zone, here, now)),
                style = ClockTheme.typography.caption,
                color = colors.contentMuted,
            )
        }
        Spacer(Modifier.width(12.dp))
        DotTime(
            hours = parts.hours,
            minutes = parts.minutes,
            dayPeriod = parts.dayPeriod,
            contentDescription = formatTimeOfDay(context, time),
            style = ClockTheme.typography.timeLarge,
            color = colors.content,
        )
    }
}

/** "Today · 5 h ahead", "Tomorrow · 9 h ahead", "Today · same time". */
private fun describeDifference(resources: Resources, dayOffset: Int, minutes: Int): String {
    val day = resources.getString(
        when {
            dayOffset > 0 -> R.string.world_tomorrow
            dayOffset < 0 -> R.string.world_yesterday
            else -> R.string.world_today
        },
    )
    if (minutes == 0) return day + " · " + resources.getString(R.string.world_same_time)
    val hours = abs(minutes) / 60
    val rest = abs(minutes) % 60
    val amount = when {
        hours == 0 -> resources.getString(R.string.duration_minutes, rest)
        rest == 0 -> resources.getString(R.string.duration_hours, hours)
        else -> resources.getString(R.string.duration_hours_minutes, hours, rest)
    }
    return day + " · " + resources.getString(if (minutes > 0) R.string.world_ahead else R.string.world_behind, amount)
}
