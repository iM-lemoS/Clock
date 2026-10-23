package io.github.imlemos.clock.alarm.ui

import io.github.imlemos.clock.core.designsystem.component.SheetOption
import io.github.imlemos.clock.core.designsystem.component.OptionsSheet
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.model.AlarmSound
import io.github.imlemos.clock.alarm.model.GradualVolume
import io.github.imlemos.clock.alarm.model.Vibration
import io.github.imlemos.clock.core.designsystem.component.ClockSwitch
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import kotlin.math.roundToInt

/** Ring durations offered, in minutes (docs/PLANO.md 3.2: 1 minute by default). */
private val RingDurationsMinutes = listOf(1, 2, 3, 5, 10, 15, 20, 30)

/**
 * Sound, vibration, gradual volume and ring duration. Shared by the alarm editor (one alarm) and
 * settings (defaults for new alarms).
 *
 * @param onSystemTonePicked A sound from the system list, or `null` for the system default.
 * @param onFilePicked A file to import (copied into the app by the caller).
 */
@Composable
fun SoundSection(
    sound: AlarmSound,
    onChange: (AlarmSound) -> Unit,
    onSystemTonePicked: (Uri?) -> Unit,
    onFilePicked: (Uri) -> Unit,
    onPreviewVibration: (Vibration) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        ToneField(sound.ringtoneTitle, onSystemTonePicked, onFilePicked)
        Spacer(Modifier.height(20.dp))
        VibrationField(sound.vibration) { vibration ->
            onChange(sound.copy(vibration = vibration))
            onPreviewVibration(vibration)
        }
        Spacer(Modifier.height(20.dp))
        GradualField(sound.gradual) { onChange(sound.copy(gradual = it)) }
        Spacer(Modifier.height(20.dp))
        RingDurationField(sound.ringDurationSeconds) { onChange(sound.copy(ringDurationSeconds = it)) }
        Spacer(Modifier.height(20.dp))
        SnoozeField(sound.snoozeMinutes) { onChange(sound.copy(snoozeMinutes = it)) }
    }
}

/**
 * The sound row: system sounds, a file (copied into the app by the caller) or the system default.
 *
 * @param onSystemTonePicked A sound from the system list, or `null` for the system default.
 */
@Composable
fun ToneField(title: String?, onSystemTonePicked: (Uri?) -> Unit, onFilePicked: (Uri) -> Unit) {
    val colors = ClockTheme.colors
    var choosingTone by rememberSaveable { mutableStateOf(false) }
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            onSystemTonePicked(uri)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onFilePicked(uri)
    }
    Column(Modifier.fillMaxWidth()) {
        Label(stringResource(R.string.sound_tone))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CapsuleShape)
                .border(1.dp, colors.outline, CapsuleShape)
                .clickable(role = Role.Button) { choosingTone = true }
                .heightIn(min = 52.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title ?: stringResource(R.string.sound_default_tone),
                style = ClockTheme.typography.body,
                color = colors.content,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.contentMuted)
        }
    }
    if (choosingTone) {
        ToneSourceDialog(
            onSystem = {
                choosingTone = false
                systemPicker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false),
                )
            },
            onFile = {
                choosingTone = false
                filePicker.launch(arrayOf("audio/*"))
            },
            onDefault = {
                choosingTone = false
                onSystemTonePicked(null)
            },
            onDismiss = { choosingTone = false },
        )
    }
}

@Composable
fun VibrationField(vibration: Vibration, onSelect: (Vibration) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Label(stringResource(R.string.vibration))
        ChipRow(
            options = Vibration.entries.map { it to stringResource(it.label) },
            selected = vibration,
            onSelect = onSelect,
        )
    }
}

@Composable
fun GradualField(gradual: GradualVolume, onChange: (GradualVolume) -> Unit) {
    val colors = ClockTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = gradual.enabled, role = Role.Switch, onValueChange = { onChange(gradual.copy(enabled = it)) })
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.gradual), style = ClockTheme.typography.body, color = colors.content)
                Text(stringResource(R.string.gradual_summary), style = ClockTheme.typography.caption, color = colors.contentMuted)
            }
            ClockSwitch(checked = gradual.enabled, onCheckedChange = null)
        }
        if (gradual.enabled) GradualSliders(gradual, onChange)
    }
}

@Composable
fun RingDurationField(seconds: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Label(stringResource(R.string.ring_duration))
        ChipRow(
            options = RingDurationsMinutes.map { it * 60 to stringResource(R.string.duration_minutes, it) },
            selected = seconds,
            onSelect = onSelect,
        )
        Text(
            stringResource(R.string.ring_duration_summary),
            style = ClockTheme.typography.caption,
            color = ClockTheme.colors.contentMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Snooze length for a tap on "Snooze"; a long press on the alarm screen offers the others. */
@Composable
fun SnoozeField(minutes: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Label(stringResource(R.string.snooze_field))
        ChipRow(
            options = Alarm.SNOOZE_CHOICES.map { it to formatSnooze(LocalResources.current, it) },
            selected = minutes,
            onSelect = onSelect,
        )
        Text(
            stringResource(R.string.snooze_field_summary),
            style = ClockTheme.typography.caption,
            color = ClockTheme.colors.contentMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}


@Composable
private fun GradualSliders(gradual: GradualVolume, onChange: (GradualVolume) -> Unit) {
    val colors = ClockTheme.colors
    val sliderColors = SliderDefaults.colors(
        thumbColor = colors.accent,
        activeTrackColor = colors.accent,
        inactiveTrackColor = colors.outline,
        activeTickColor = colors.onAccent,
        inactiveTickColor = colors.contentMuted,
    )
    Text(
        stringResource(R.string.gradual_target, gradual.targetPercent),
        style = ClockTheme.typography.caption,
        color = colors.content,
    )
    Slider(
        value = gradual.targetPercent.toFloat(),
        onValueChange = { onChange(gradual.copy(targetPercent = (it / 10f).roundToInt().times(10).coerceIn(10, 100))) },
        valueRange = 10f..100f,
        steps = 8,
        colors = sliderColors,
    )
    Text(
        stringResource(R.string.gradual_duration, stringResource(R.string.duration_seconds, gradual.seconds)),
        style = ClockTheme.typography.caption,
        color = colors.content,
    )
    Slider(
        value = gradual.seconds.toFloat(),
        onValueChange = { onChange(gradual.copy(seconds = (it / 10f).roundToInt().times(10).coerceIn(10, 300))) },
        valueRange = 10f..300f,
        steps = 28,
        colors = sliderColors,
    )
}

@Composable
private fun ToneSourceDialog(onSystem: () -> Unit, onFile: () -> Unit, onDefault: () -> Unit, onDismiss: () -> Unit) {
    OptionsSheet(
        title = stringResource(R.string.sound_tone),
        options = listOf(
            SheetOption(stringResource(R.string.sound_pick_system), R.drawable.ic_volume_up, onClick = onSystem),
            SheetOption(stringResource(R.string.sound_pick_file), R.drawable.ic_folder, onClick = onFile),
            SheetOption(stringResource(R.string.sound_use_default), R.drawable.ic_replay, onClick = onDefault),
        ),
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, wrap: Boolean = true) {
    val colors = ClockTheme.colors
    val chips: @Composable () -> Unit = {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                Modifier
                    .clip(CapsuleShape)
                    .background(if (isSelected) colors.accent else colors.background, CapsuleShape)
                    .border(1.dp, if (isSelected) colors.accent else colors.outline, CapsuleShape)
                    .toggleable(value = isSelected, role = Role.RadioButton, onValueChange = { onSelect(value) })
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = ClockTheme.typography.label, color = if (isSelected) colors.onAccent else colors.content)
            }
        }
    }
    if (wrap) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Composable
internal fun Label(text: String) {
    Text(
        text.uppercase(),
        style = ClockTheme.typography.label,
        color = ClockTheme.colors.contentMuted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

private val Vibration.label: Int
    get() = when (this) {
        Vibration.NONE -> R.string.vibration_none
        Vibration.SYNC -> R.string.vibration_sync
        Vibration.PULSE -> R.string.vibration_pulse
        Vibration.HEARTBEAT -> R.string.vibration_heartbeat
        Vibration.WAVE -> R.string.vibration_wave
        Vibration.STACCATO -> R.string.vibration_staccato
        Vibration.LONG -> R.string.vibration_long
    }
