package io.github.imlemos.clock.alarm.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import io.github.imlemos.clock.R
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundSection(
    sound: AlarmSound,
    onChange: (AlarmSound) -> Unit,
    onSystemTonePicked: (Uri?) -> Unit,
    onFilePicked: (Uri) -> Unit,
    onPreviewVibration: (Vibration) -> Unit,
    modifier: Modifier = Modifier,
) {
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

    Column(modifier.fillMaxWidth()) {
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
                text = sound.ringtoneTitle ?: stringResource(R.string.sound_default_tone),
                style = ClockTheme.typography.body,
                color = colors.content,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.contentMuted)
        }

        Spacer(Modifier.height(20.dp))
        Label(stringResource(R.string.vibration))
        ChipRow(
            options = Vibration.entries.map { it to stringResource(it.label) },
            selected = sound.vibration,
            onSelect = { vibration ->
                onChange(sound.copy(vibration = vibration))
                onPreviewVibration(vibration)
            },
        )

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = sound.gradual.enabled,
                    role = Role.Switch,
                    onValueChange = { onChange(sound.copy(gradual = sound.gradual.copy(enabled = it))) },
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.gradual), style = ClockTheme.typography.body, color = colors.content)
                Text(stringResource(R.string.gradual_summary), style = ClockTheme.typography.caption, color = colors.contentMuted)
            }
            ClockSwitch(checked = sound.gradual.enabled, onCheckedChange = null)
        }
        if (sound.gradual.enabled) {
            GradualSliders(sound.gradual) { onChange(sound.copy(gradual = it)) }
        }

        Spacer(Modifier.height(20.dp))
        Label(stringResource(R.string.ring_duration))
        ChipRow(
            options = RingDurationsMinutes.map { it * 60 to stringResource(R.string.duration_minutes, it) },
            selected = sound.ringDurationSeconds,
            onSelect = { onChange(sound.copy(ringDurationSeconds = it)) },
        )
        Text(
            stringResource(R.string.ring_duration_summary),
            style = ClockTheme.typography.caption,
            color = colors.contentMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
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
    val colors = ClockTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sound_tone)) },
        text = {
            Column {
                listOf(
                    R.string.sound_pick_system to onSystem,
                    R.string.sound_pick_file to onFile,
                    R.string.sound_use_default to onDefault,
                ).forEach { (label, action) ->
                    Text(
                        stringResource(label),
                        style = ClockTheme.typography.body,
                        color = colors.content,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClick = action)
                            .padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.content) }
        },
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = ClockTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
}

@Composable
private fun Label(text: String) {
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
