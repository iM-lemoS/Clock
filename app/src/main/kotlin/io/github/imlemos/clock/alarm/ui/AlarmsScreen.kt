package io.github.imlemos.clock.alarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.zones.ZoneNames
import io.github.imlemos.clock.alarm.model.AlarmGroup
import io.github.imlemos.clock.core.audio.AlarmVolume
import io.github.imlemos.clock.core.designsystem.component.ClockSwitch
import io.github.imlemos.clock.core.designsystem.component.DotTime
import io.github.imlemos.clock.core.designsystem.component.EmptyState
import io.github.imlemos.clock.core.designsystem.component.OptionsDialog
import io.github.imlemos.clock.core.designsystem.component.TextInputDialog
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.navigation.LocalShellChrome
import io.github.imlemos.clock.navigation.TabScreen
import kotlinx.coroutines.delay

@Composable
fun AlarmsScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenReliability: () -> Unit,
    onBatchEdit: (List<Long>) -> Unit,
    viewModel: AlarmListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reliabilityOk by viewModel.reliabilityOk.collectAsStateWithLifecycle()
    val volumeWarning by viewModel.volumeWarning.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<ListDialog?>(null) }
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

    // Multi-select: back leaves selection, and the glass action bar replaces the navigation.
    BackHandler(enabled = state.selecting) { viewModel.clearSelection() }
    val chrome = LocalShellChrome.current
    val selecting = state.selecting
    DisposableEffect(selecting) {
        chrome.actionBar = if (selecting) {
            {
                SelectionActions(
                    onEnable = { viewModel.enableSelected(true) },
                    onDisable = { viewModel.enableSelected(false) },
                    onMove = { dialog = ListDialog.MoveToGroup },
                    onEdit = { onBatchEdit(viewModel.takeSelection()) },
                    onDelete = viewModel::deleteSelected,
                )
            }
        } else {
            null
        }
        onDispose { chrome.actionBar = null }
    }

    val showVolumeWarning = volumeWarning != AlarmVolume.Level.OK && state.anyActive
    val bannerCount = listOf(!reliabilityOk, showVolumeWarning).count { it }
    TabScreen(
        title = if (selecting) {
            pluralStringResource(R.plurals.selected_count, state.selection.size, state.selection.size)
        } else {
            stringResource(R.string.nav_alarms)
        },
        contentPadding = PaddingValues(),
        onOpenSettings = onOpenSettings,
        actions = if (selecting) {
            { TopBarIconButton(R.drawable.ic_close, stringResource(R.string.action_close), viewModel::clearSelection) }
        } else {
            null
        },
    ) {
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
        if (state.isEmpty) {
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
            val firstKey = state.entries.firstOrNull()?.key
            LaunchedEffect(firstKey) {
                if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
            }
            val onAlarmClick: (Long) -> Unit = { id -> if (selecting) viewModel.toggleSelection(id) else onEditAlarm(id) }
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
                state.entries.forEach { entry ->
                    when (entry) {
                        is ListEntry.Single -> item(key = entry.key) {
                            AlarmCard(entry.item, state, selecting, onAlarmClick, viewModel)
                        }
                        is ListEntry.Group -> {
                            item(key = entry.key) {
                                GroupCard(
                                    entry = entry,
                                    state = state,
                                    onToggleExpanded = { viewModel.toggleExpanded(entry.group) },
                                    onSwitch = { viewModel.setGroupEnabled(entry.group.id, it) },
                                    onLongClick = { dialog = ListDialog.GroupMenu(entry.group) },
                                )
                            }
                            if (entry.group.expanded) {
                                items(entry.items, key = { "alarm-${it.alarm.id}" }) { item ->
                                    AlarmCard(item, state, selecting, onAlarmClick, viewModel, Modifier.padding(start = 20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (val current = dialog) {
        ListDialog.MoveToGroup -> MoveToGroupDialog(
            groups = state.groups,
            onPick = { groupId ->
                viewModel.moveSelected(groupId)
                dialog = null
            },
            onCreate = { name ->
                viewModel.moveSelectedToNewGroup(name)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is ListDialog.GroupMenu -> GroupMenuDialog(
            group = current.group,
            onRename = { dialog = ListDialog.Rename(current.group) },
            onDelete = { dialog = ListDialog.DeleteGroup(current.group) },
            onDismiss = { dialog = null },
        )
        is ListDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.group_rename),
            label = stringResource(R.string.group_name),
            initial = current.group.name,
            maxLength = MAX_GROUP_NAME,
            onConfirm = { name ->
                viewModel.renameGroup(current.group.id, name)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is ListDialog.DeleteGroup -> DeleteGroupDialog(
            group = current.group,
            onDelete = { withAlarms ->
                viewModel.deleteGroup(current.group.id, withAlarms)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private sealed interface ListDialog {
    data object MoveToGroup : ListDialog
    data class GroupMenu(val group: AlarmGroup) : ListDialog
    data class Rename(val group: AlarmGroup) : ListDialog
    data class DeleteGroup(val group: AlarmGroup) : ListDialog
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(
    item: AlarmListItem,
    state: AlarmListState,
    selecting: Boolean,
    onClick: (Long) -> Unit,
    viewModel: AlarmListViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val colors = ClockTheme.colors
    val alarm = item.alarm
    val parts = timeParts(context, alarm.time)
    val ringing = item.nextRing != null
    val selected = alarm.id in state.selection
    val locale = LocalConfiguration.current.locales[0]
    val today = state.now?.toLocalDate() ?: java.time.LocalDate.now()
    val nextText = when {
        item.groupOff -> stringResource(R.string.group_off)
        item.nextRing != null && state.now != null -> describeNextRing(resources, state.now, item.nextRing, locale)
        else -> stringResource(R.string.alarm_off)
    }
    val subtitle = describeRecurrence(resources, alarm.recurrence, locale, today) + " · " + nextText
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .border(2.dp, if (selected) colors.accent else colors.surface, shape)
            .semantics { this.selected = selected }
            .combinedClickable(
                onClickLabel = stringResource(if (selecting) R.string.action_select else R.string.action_edit_alarm),
                onLongClickLabel = stringResource(R.string.action_select),
                onClick = { onClick(alarm.id) },
                onLongClick = { viewModel.toggleSelection(alarm.id) },
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            DotTime(
                hours = parts.hours,
                minutes = parts.minutes,
                dayPeriod = parts.dayPeriod,
                contentDescription = formatTimeOfDay(context, alarm.time),
                color = if (ringing) colors.content else colors.contentMuted,
            )
            Spacer(Modifier.height(8.dp))
            if (alarm.label.isNotBlank()) {
                Text(alarm.label, style = ClockTheme.typography.body, color = colors.content, maxLines = 1)
            }
            Text(subtitle, style = ClockTheme.typography.caption, color = colors.contentMuted)
            item.zone?.let { zone ->
                val city = ZoneNames.city(zone, locale)
                Text(
                    text = item.hereTime?.let { here ->
                        val alarmDate = here.withZoneSameInstant(zone).toLocalDate()
                        stringResource(R.string.zone_with_here, city, formatHereTime(context, here, alarmDate, locale))
                    } ?: city,
                    style = ClockTheme.typography.caption,
                    color = colors.content,
                )
            }
            if (item.canSkip && !selecting) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(if (item.skipped) R.string.alarm_unskip else R.string.alarm_skip_next),
                    style = ClockTheme.typography.label,
                    color = colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { viewModel.setSkipNext(alarm.id, !item.skipped) }
                        .padding(vertical = 6.dp),
                )
                if (item.skipped) {
                    Text(stringResource(R.string.alarm_next_skipped), style = ClockTheme.typography.caption, color = colors.content)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        if (!selecting) {
            ClockSwitch(checked = alarm.enabled, onCheckedChange = { viewModel.setEnabled(alarm.id, it) })
        }
    }
}

/** A group card: same size as an alarm card, with its next alarm in full (docs/PLANO.md 3.8). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(
    entry: ListEntry.Group,
    state: AlarmListState,
    onToggleExpanded: () -> Unit,
    onSwitch: (Boolean) -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = ClockTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val group = entry.group
    val shape = RoundedCornerShape(28.dp)
    val nextLine = entry.next?.nextRing?.let { next ->
        val day = java.time.format.DateTimeFormatter
            .ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
            .format(next)
        stringResource(R.string.group_next, "$day, ${formatAlarmTime(context, entry.next.alarm.time, entry.next.zone, locale)}")
    } ?: stringResource(if (group.enabled) R.string.group_none_active else R.string.group_off)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .combinedClickable(
                onClickLabel = stringResource(if (group.expanded) R.string.group_collapse else R.string.group_expand),
                onLongClickLabel = stringResource(R.string.group_options),
                onClick = onToggleExpanded,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = colors.contentMuted,
            modifier = Modifier
                .size(28.dp)
                .rotate(if (group.expanded) 0f else -90f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(group.name, style = ClockTheme.typography.title, color = colors.content, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(
                pluralStringResource(R.plurals.group_alarm_count, entry.items.size, entry.items.size),
                style = ClockTheme.typography.caption,
                color = colors.contentMuted,
            )
            Text(nextLine, style = ClockTheme.typography.caption, color = if (entry.next != null) colors.content else colors.contentMuted)
        }
        Spacer(Modifier.width(12.dp))
        if (!state.selecting) ClockSwitch(checked = group.enabled, onCheckedChange = onSwitch)
    }
}

@Composable
private fun SelectionActions(
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onMove: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SelectionAction(R.drawable.ic_toggle_on, R.string.selection_enable, onEnable)
        SelectionAction(R.drawable.ic_toggle_off, R.string.selection_disable, onDisable)
        SelectionAction(R.drawable.ic_folder, R.string.selection_group, onMove)
        SelectionAction(R.drawable.ic_edit, R.string.selection_edit, onEdit)
        SelectionAction(R.drawable.ic_delete, R.string.selection_delete, onDelete)
    }
}

@Composable
private fun SelectionAction(icon: Int, label: Int, onClick: () -> Unit) {
    val colors = ClockTheme.colors
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.content, modifier = Modifier.size(24.dp))
        Text(stringResource(label), style = ClockTheme.typography.caption, color = colors.content, maxLines = 1)
    }
}

@Composable
private fun MoveToGroupDialog(
    groups: List<AlarmGroup>,
    onPick: (Long?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    if (creating) {
        TextInputDialog(stringResource(R.string.group_new), stringResource(R.string.group_name), "", MAX_GROUP_NAME, onCreate, onDismiss)
        return
    }
    OptionsDialog(
        title = stringResource(R.string.group_move_title),
        options = listOf(stringResource(R.string.group_none) to { onPick(null) }) +
            groups.map { group -> group.name to { onPick(group.id) } } +
            listOf(stringResource(R.string.group_new) to { creating = true }),
        onDismiss = onDismiss,
    )
}

@Composable
private fun GroupMenuDialog(group: AlarmGroup, onRename: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    OptionsDialog(
        title = group.name,
        options = listOf(
            stringResource(R.string.group_rename) to onRename,
            stringResource(R.string.group_delete) to onDelete,
        ),
        onDismiss = onDismiss,
    )
}

/** Delete only the group (alarms stay, ungrouped) or the group with its alarms (docs/PLANO.md 3.9). */
@Composable
private fun DeleteGroupDialog(group: AlarmGroup, onDelete: (withAlarms: Boolean) -> Unit, onDismiss: () -> Unit) {
    OptionsDialog(
        title = stringResource(R.string.group_delete_title, group.name),
        options = listOf(
            stringResource(R.string.group_delete_only) to { onDelete(false) },
            stringResource(R.string.group_delete_with_alarms) to { onDelete(true) },
        ),
        onDismiss = onDismiss,
    )
}

private const val VOLUME_POLL_MS = 1_500L
internal const val MAX_GROUP_NAME = 40

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
