package io.github.imlemos.clock.alarm.ui

import io.github.imlemos.clock.core.designsystem.rememberHaptics
import io.github.imlemos.clock.core.designsystem.component.DialogButtonStyle
import io.github.imlemos.clock.core.designsystem.component.DialogAction
import io.github.imlemos.clock.core.designsystem.component.GlassAlertDialog
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.layout.newLayoutName
import io.github.imlemos.clock.core.designsystem.component.ClockSwitch
import io.github.imlemos.clock.core.designsystem.component.KeyboardTimeInput
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.MultiDateCalendar
import io.github.imlemos.clock.core.designsystem.component.OptionsSheet
import io.github.imlemos.clock.core.designsystem.component.SheetOption
import io.github.imlemos.clock.core.designsystem.component.TextInputDialog
import io.github.imlemos.clock.core.designsystem.component.SegmentedControl
import io.github.imlemos.clock.core.designsystem.component.TimeWheelLabels
import io.github.imlemos.clock.core.designsystem.component.TimeWheels
import io.github.imlemos.clock.core.designsystem.component.WeekdayPicker
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.scheduling.TimeOfDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    alarmId: Long,
    onClose: () -> Unit,
    onNewLayout: (fromId: Long, name: String, requestId: Long) -> Unit,
    initialZone: String? = null,
    viewModel: AlarmEditorViewModel = hiltViewModel<AlarmEditorViewModel, AlarmEditorViewModel.Factory>(
        key = "alarm-editor-$alarmId-$initialZone",
        creationCallback = { factory -> factory.create(alarmId, initialZone) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val colors = ClockTheme.colors
    var keyboardInput by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val locale = LocalConfiguration.current.locales[0]
    val onSaved: (SavedAlarm) -> Unit = { saved ->
        saved.nextRing?.let { next ->
            val text = resources.getString(R.string.alarm_set_toast, describeNextRing(resources, saved.now, next, locale))
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
        onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        ClockTopBar(
            title = stringResource(if (state.isNew) R.string.alarm_editor_new_title else R.string.alarm_editor_edit_title),
            navigationIcon = { TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClose) },
            actions = {
                TopBarIconButton(
                    icon = if (keyboardInput) R.drawable.ic_schedule else R.drawable.ic_keyboard,
                    contentDescription = stringResource(
                        if (keyboardInput) R.string.alarm_editor_use_dial else R.string.alarm_editor_use_keyboard,
                    ),
                    onClick = { keyboardInput = !keyboardInput },
                )
                if (!state.isNew) {
                    TopBarIconButton(
                        icon = R.drawable.ic_delete,
                        contentDescription = stringResource(R.string.action_delete_alarm),
                        onClick = {
                            haptics.reject()
                            viewModel.delete(onClose)
                        },
                    )
                }
            },
        )
        if (!state.loaded) return@Column

        val is24Hour = is24Hour(context)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            if (keyboardInput) {
                KeyboardTimeInput(
                    hour = state.time.hour,
                    minute = state.time.minute,
                    is24Hour = is24Hour,
                    onChange = { hour, minute -> viewModel.setTime(TimeOfDay(hour, minute)) },
                    labels = rememberTimeWheelLabels(locale),
                )
            } else {
                TimeWheels(
                    hour = state.time.hour,
                    minute = state.time.minute,
                    is24Hour = is24Hour,
                    onChange = { hour, minute -> viewModel.setTime(TimeOfDay(hour, minute)) },
                    labels = rememberTimeWheelLabels(locale),
                )
            }
            val hereTime = remember(state.time, state.zone, state.kind, state.weekDays, state.dates) { viewModel.hereTime(state) }
            if (hereTime != null) {
                Text(
                    stringResource(
                        R.string.zone_here,
                        formatHereTime(context, hereTime, hereTime.withZoneSameInstant(state.zone ?: state.manualZone ?: hereTime.zone).toLocalDate(), locale),
                    ),
                    style = ClockTheme.typography.body,
                    color = colors.contentMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            RecurrenceSection(state = state, viewModel = viewModel, locale = locale)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.label,
                onValueChange = viewModel::setLabel,
                label = { Text(stringResource(R.string.alarm_editor_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                textStyle = ClockTheme.typography.body,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.content,
                    unfocusedBorderColor = colors.outline,
                    focusedLabelColor = colors.content,
                    unfocusedLabelColor = colors.contentMuted,
                    cursorColor = colors.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = state.singleUse, role = Role.Switch, onValueChange = viewModel::setSingleUse)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.alarm_editor_single_use), style = ClockTheme.typography.body, color = colors.content)
                    Text(
                        stringResource(R.string.alarm_editor_single_use_summary),
                        style = ClockTheme.typography.caption,
                        color = colors.contentMuted,
                    )
                }
                ClockSwitch(checked = state.singleUse, onCheckedChange = null)
            }
            Spacer(Modifier.height(8.dp))
            GroupField(state = state, viewModel = viewModel)
            Spacer(Modifier.height(16.dp))
            ZoneField(zone = state.zone, manualZone = state.manualZone, onChange = viewModel::setZone)
            Spacer(Modifier.height(16.dp))
            val newLayoutName = newLayoutName(state.layouts)
            LayoutField(
                layoutId = state.layoutId,
                layouts = state.layouts,
                defaultLayoutId = state.defaultLayoutId,
                onSelect = viewModel::setLayout,
                onNew = { viewModel.createLayout(newLayoutName, onNewLayout) },
            )
            Spacer(Modifier.height(16.dp))
            SoundSection(
                sound = state.sound,
                onChange = viewModel::setSound,
                onSystemTonePicked = viewModel::setSystemTone,
                onFilePicked = { uri ->
                    viewModel.importTone(uri) {
                        Toast.makeText(context, R.string.sound_import_failed, Toast.LENGTH_SHORT).show()
                    }
                },
                onPreviewVibration = viewModel::previewVibration,
            )
            Spacer(Modifier.height(24.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(CapsuleShape)
                    .background(if (state.canSave) colors.accent else colors.surface, CapsuleShape)
                    .clickable(enabled = state.canSave, role = Role.Button) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        haptics.confirm()
                        viewModel.save(onSaved = onSaved)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.action_save),
                    style = ClockTheme.typography.title,
                    color = if (state.canSave) colors.onAccent else colors.contentMuted,
                )
            }
        }
    }
    state.collision?.let { collision ->
        CollisionDialog(
            collision = collision,
            onSaveAnyway = { viewModel.save(force = true, onSaved = onSaved) },
            onDismiss = viewModel::dismissCollision,
        )
    }
}

@Composable
private fun RecurrenceSection(state: AlarmEditorState, viewModel: AlarmEditorViewModel, locale: java.util.Locale) {
    val colors = ClockTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.alarm_editor_repeat),
            style = ClockTheme.typography.label,
            color = colors.contentMuted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SegmentedControl(
            options = listOf(
                RecurrenceKind.ONCE to stringResource(R.string.recurrence_once),
                RecurrenceKind.WEEKLY to stringResource(R.string.recurrence_weekly),
                RecurrenceKind.DATES to stringResource(R.string.recurrence_dates),
            ),
            selected = state.kind,
            onSelect = viewModel::setKind,
        )
        Spacer(Modifier.height(12.dp))
        when (state.kind) {
            RecurrenceKind.ONCE -> Unit
            RecurrenceKind.WEEKLY -> {
                WeekdayPicker(selected = state.weekDays, onToggle = viewModel::toggleDay, locale = locale)
                if (state.weekDays.isEmpty()) Hint(stringResource(R.string.alarm_editor_pick_days))
            }
            RecurrenceKind.DATES -> {
                MultiDateCalendar(
                    selected = state.dates,
                    onToggle = viewModel::toggleDate,
                    minDate = viewModel.today(),
                    locale = locale,
                    previousLabel = stringResource(R.string.calendar_previous_month),
                    nextLabel = stringResource(R.string.calendar_next_month),
                )
                if (state.dates.isEmpty()) Hint(stringResource(R.string.alarm_editor_pick_dates))
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = ClockTheme.typography.caption,
        color = ClockTheme.colors.contentMuted,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun rememberTimeWheelLabels(locale: java.util.Locale): TimeWheelLabels {
    val amPm = remember(locale) { java.text.DateFormatSymbols.getInstance(locale).amPmStrings }
    return TimeWheelLabels(
        hours = stringResource(R.string.wheel_hours),
        minutes = stringResource(R.string.wheel_minutes),
        period = stringResource(R.string.wheel_period),
        am = amPm[0],
        pm = amPm[1],
        increase = stringResource(R.string.wheel_increase),
        decrease = stringResource(R.string.wheel_decrease),
    )
}

@Composable
private fun GroupField(state: AlarmEditorState, viewModel: AlarmEditorViewModel) {
    val colors = ClockTheme.colors
    var choosing by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val groupName = state.newGroupName
        ?: state.groups.firstOrNull { it.id == state.groupId }?.name
        ?: stringResource(R.string.group_none)
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.group_field).uppercase(),
            style = ClockTheme.typography.label,
            color = colors.contentMuted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CapsuleShape)
                .border(1.dp, colors.outline, CapsuleShape)
                .clickable(role = Role.Button) { choosing = true }
                .heightIn(min = 52.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(groupName, style = ClockTheme.typography.body, color = colors.content, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.contentMuted)
        }
    }
    if (choosing) {
        val none = state.groupId == null && state.newGroupName == null
        OptionsSheet(
            title = stringResource(R.string.group_field),
            options = buildList {
                add(SheetOption(stringResource(R.string.group_none), R.drawable.ic_close, selected = none) { viewModel.setGroup(null); choosing = false })
                state.groups.forEach { group ->
                    add(SheetOption(group.name, R.drawable.ic_folder, selected = group.id == state.groupId) { viewModel.setGroup(group.id); choosing = false })
                }
                state.newGroupName?.let { name ->
                    add(SheetOption(name, R.drawable.ic_folder, selected = true, summary = stringResource(R.string.group_created_on_save)) { choosing = false })
                }
                add(SheetOption(stringResource(R.string.group_new), R.drawable.ic_add) { choosing = false; creating = true })
            },
            onDismiss = { choosing = false },
        )
    }
    if (creating) {
        TextInputDialog(
            title = stringResource(R.string.group_new),
            label = stringResource(R.string.group_name),
            initial = "",
            maxLength = MAX_GROUP_NAME,
            onConfirm = { name -> viewModel.createGroup(name); creating = false },
            onDismiss = { creating = false },
        )
    }
}

/** "This alarm rings at the same time as ..." with the choice to save anyway (docs/PLANO.md 3.11). */
@Composable
private fun CollisionDialog(collision: Collision, onSaveAnyway: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = ClockTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val day = java.time.format.DateTimeFormatter
        .ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
        .format(collision.at)
    val other = collision.other.label.ifBlank { formatTimeOfDay(context, collision.other.time) }
    GlassAlertDialog(
        title = stringResource(R.string.collision_title),
        text = stringResource(R.string.collision_body, other, "$day, ${formatTimeOfDay(context, collision.other.time)}"),
        onDismiss = onDismiss,
        actions = listOf(
            DialogAction(stringResource(R.string.collision_change), onClick = onDismiss),
            DialogAction(stringResource(R.string.collision_save_anyway), DialogButtonStyle.Primary, onClick = onSaveAnyway),
        ),
    )
}
