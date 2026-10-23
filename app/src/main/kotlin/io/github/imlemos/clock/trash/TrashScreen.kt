package io.github.imlemos.clock.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.alarm.ui.describeRecurrence
import io.github.imlemos.clock.alarm.ui.formatTimeOfDay
import io.github.imlemos.clock.alarm.ui.timeParts
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.DotTime
import io.github.imlemos.clock.core.designsystem.component.EmptyState
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trash: TrashRepository,
    private val controller: AlarmController,
) : ViewModel() {
    val items: StateFlow<List<TrashedItem>> = trash.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(trashId: Long) {
        viewModelScope.launch { controller.restore(trashId) }
    }

    fun restoreGroup(trashId: Long, alarmIds: Set<Long>) {
        viewModelScope.launch { controller.restoreGroup(trashId, alarmIds) }
    }

    fun empty() {
        viewModelScope.launch { trash.empty() }
    }
}

/** The trash (docs/PLANO.md 3.10): restore an alarm or empty everything. */
@Composable
fun TrashScreen(onBack: () -> Unit, viewModel: TrashViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val colors = ClockTheme.colors
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    var restoringGroup by remember { mutableStateOf<TrashedGroup?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        ClockTopBar(
            title = stringResource(R.string.trash_title),
            navigationIcon = { TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) },
            actions = {
                if (items.isNotEmpty()) {
                    TextButton(onClick = { confirmEmpty = true }) {
                        Text(stringResource(R.string.trash_empty_action), style = ClockTheme.typography.label, color = colors.accent)
                    }
                }
            },
        )
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    digits = stringResource(R.string.alarms_empty_digits),
                    title = stringResource(R.string.trash_empty_title),
                    body = stringResource(R.string.trash_empty_body),
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.trashId }) { item ->
                    when (item) {
                        is TrashedAlarm -> TrashRow(item, onRestore = { viewModel.restore(item.trashId) })
                        is TrashedGroup -> TrashGroupRow(
                            item,
                            onRestore = {
                                if (item.alarms.size > 1) restoringGroup = item else viewModel.restore(item.trashId)
                            },
                        )
                    }
                }
            }
        }
    }

    restoringGroup?.let { group ->
        RestoreGroupDialog(
            group = group,
            onRestore = { ids ->
                viewModel.restoreGroup(group.trashId, ids)
                restoringGroup = null
            },
            onDismiss = { restoringGroup = null },
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.trash_empty_confirm_title)) },
            text = { Text(stringResource(R.string.trash_empty_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.empty(); confirmEmpty = false }) {
                    Text(stringResource(R.string.trash_empty_action), color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.action_cancel), color = colors.content) }
            },
            containerColor = colors.surfaceRaised,
            titleContentColor = colors.content,
            textContentColor = colors.contentMuted,
        )
    }
}

@Composable
private fun TrashRow(item: TrashedAlarm, onRestore: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val colors = ClockTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val parts = timeParts(context, item.alarm.time)
    val deletedOn = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        .format(item.deletedAt.atZone(ZoneId.systemDefault()))
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            DotTime(
                hours = parts.hours,
                minutes = parts.minutes,
                dayPeriod = parts.dayPeriod,
                contentDescription = formatTimeOfDay(context, item.alarm.time),
                style = ClockTheme.typography.timeSmall,
                color = colors.contentMuted,
            )
            Spacer(Modifier.height(6.dp))
            if (item.alarm.label.isNotBlank()) {
                Text(item.alarm.label, style = ClockTheme.typography.body, color = colors.content, maxLines = 1)
            }
            Text(
                describeRecurrence(resources, item.alarm.recurrence, locale, LocalDate.now()),
                style = ClockTheme.typography.caption,
                color = colors.contentMuted,
            )
            Text(
                stringResource(
                    if (item.automatic) R.string.trash_deleted_automatically else R.string.trash_deleted_on,
                    deletedOn,
                ),
                style = ClockTheme.typography.caption,
                color = colors.contentMuted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.trash_restore),
            style = ClockTheme.typography.label,
            color = colors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onRestore)
                .padding(8.dp),
        )
    }
}

@Composable
private fun TrashGroupRow(item: TrashedGroup, onRestore: () -> Unit) {
    val colors = ClockTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val deletedOn = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        .format(item.deletedAt.atZone(ZoneId.systemDefault()))
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.group.name, style = ClockTheme.typography.title, color = colors.content, maxLines = 1)
            Text(
                pluralStringResource(R.plurals.group_alarm_count, item.alarms.size, item.alarms.size),
                style = ClockTheme.typography.caption,
                color = colors.contentMuted,
            )
            Text(stringResource(R.string.trash_deleted_on, deletedOn), style = ClockTheme.typography.caption, color = colors.contentMuted)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.trash_restore),
            style = ClockTheme.typography.label,
            color = colors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onRestore)
                .padding(8.dp),
        )
    }
}

/** Restore a group with one, several or all of its alarms (docs/PLANO.md 3.10). */
@Composable
private fun RestoreGroupDialog(group: TrashedGroup, onRestore: (Set<Long>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = ClockTheme.colors
    var chosen by remember(group) { mutableStateOf(group.alarms.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trash_restore_group_title, group.group.name)) },
        text = {
            Column {
                group.alarms.forEach { alarm ->
                    val checked = alarm.id in chosen
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { chosen = if (it) chosen + alarm.id else chosen - alarm.id },
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = colors.onAccent),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            formatTimeOfDay(context, alarm.time) + if (alarm.label.isNotBlank()) " · ${alarm.label}" else "",
                            style = ClockTheme.typography.body,
                            color = colors.content,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRestore(chosen) }) { Text(stringResource(R.string.trash_restore), color = colors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.content) }
        },
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.content,
    )
}
