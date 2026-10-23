package io.github.imlemos.clock.alarm.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.alarm.model.AlarmPatch
import io.github.imlemos.clock.alarm.model.GradualVolume
import io.github.imlemos.clock.alarm.model.Vibration
import io.github.imlemos.clock.core.audio.AlarmSoundPlayer
import io.github.imlemos.clock.core.audio.RingtoneStore
import io.github.imlemos.clock.core.designsystem.component.ClockSwitch
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = BatchEditViewModel.Factory::class)
class BatchEditViewModel @AssistedInject constructor(
    @Assisted val alarmIds: List<Long>,
    private val controller: AlarmController,
    private val ringtones: RingtoneStore,
    private val player: AlarmSoundPlayer,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(alarmIds: List<Long>): BatchEditViewModel
    }

    private val _patch = MutableStateFlow(AlarmPatch())

    /** Every field starts as "don't change" (`null`). */
    val patch: StateFlow<AlarmPatch> = _patch.asStateFlow()

    fun update(transform: (AlarmPatch) -> AlarmPatch) = _patch.update(transform)

    fun setSystemTone(uri: Uri?) = update { it.copy(ringtone = AlarmPatch.Ringtone(uri?.toString(), uri?.let(ringtones::systemTitle))) }

    fun importTone(uri: Uri, onFailed: () -> Unit) {
        viewModelScope.launch {
            val tone = ringtones.import(uri) ?: return@launch onFailed()
            update { it.copy(ringtone = AlarmPatch.Ringtone(tone.uri, tone.title)) }
        }
    }

    fun previewVibration(vibration: Vibration) = player.previewVibration(vibration)

    fun apply(onDone: () -> Unit) {
        viewModelScope.launch {
            controller.applyPatch(alarmIds, _patch.value)
            onDone()
        }
    }
}

/** Batch edit: each field is left unchanged unless switched to "change" (docs/PLANO.md 3.9). */
@Composable
fun BatchEditScreen(
    alarmIds: List<Long>,
    onClose: () -> Unit,
    viewModel: BatchEditViewModel = hiltViewModel<BatchEditViewModel, BatchEditViewModel.Factory>(
        key = "batch-${alarmIds.joinToString()}",
        creationCallback = { it.create(alarmIds) },
    ),
) {
    val patch by viewModel.patch.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = ClockTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        ClockTopBar(
            title = pluralStringResource(R.plurals.batch_edit_title, alarmIds.size, alarmIds.size),
            navigationIcon = { TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClose) },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(stringResource(R.string.batch_edit_intro), style = ClockTheme.typography.caption, color = colors.contentMuted)
            Spacer(Modifier.height(8.dp))

            ChangeableField(R.string.alarm_editor_label, patch.label != null, { on -> viewModel.update { it.copy(label = if (on) "" else null) } }) {
                OutlinedTextField(
                    value = patch.label.orEmpty(),
                    onValueChange = { text -> viewModel.update { it.copy(label = text.take(AlarmEditorViewModel.MAX_LABEL_LENGTH)) } },
                    singleLine = true,
                    textStyle = ClockTheme.typography.body,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.content,
                        unfocusedBorderColor = colors.outline,
                        cursorColor = colors.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ChangeableField(R.string.sound_tone, patch.ringtone != null, { on ->
                viewModel.update { it.copy(ringtone = if (on) AlarmPatch.Ringtone(null, null) else null) }
            }) {
                ToneField(
                    title = patch.ringtone?.title,
                    onSystemTonePicked = viewModel::setSystemTone,
                    onFilePicked = { uri ->
                        viewModel.importTone(uri) { Toast.makeText(context, R.string.sound_import_failed, Toast.LENGTH_SHORT).show() }
                    },
                )
            }
            ChangeableField(R.string.vibration, patch.vibration != null, { on ->
                viewModel.update { it.copy(vibration = if (on) Vibration.SYNC else null) }
            }) {
                VibrationField(patch.vibration ?: Vibration.SYNC) { vibration ->
                    viewModel.update { it.copy(vibration = vibration) }
                    viewModel.previewVibration(vibration)
                }
            }
            ChangeableField(R.string.gradual, patch.gradual != null, { on ->
                viewModel.update { it.copy(gradual = if (on) GradualVolume(enabled = true) else null) }
            }) {
                GradualField(patch.gradual ?: GradualVolume()) { gradual -> viewModel.update { it.copy(gradual = gradual) } }
            }
            ChangeableField(R.string.ring_duration, patch.ringDurationSeconds != null, { on ->
                viewModel.update { it.copy(ringDurationSeconds = if (on) 60 else null) }
            }) {
                RingDurationField(patch.ringDurationSeconds ?: 60) { seconds -> viewModel.update { it.copy(ringDurationSeconds = seconds) } }
            }
            ChangeableField(R.string.alarm_editor_single_use, patch.singleUse != null, { on ->
                viewModel.update { it.copy(singleUse = if (on) false else null) }
            }) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = patch.singleUse == true, role = Role.Switch, onValueChange = { v -> viewModel.update { it.copy(singleUse = v) } })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.alarm_editor_single_use_summary), style = ClockTheme.typography.caption, color = colors.contentMuted, modifier = Modifier.weight(1f))
                    ClockSwitch(checked = patch.singleUse == true, onCheckedChange = null)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .heightIn(min = 56.dp)
                .clip(CapsuleShape)
                .background(if (patch.isEmpty) colors.surface else colors.accent, CapsuleShape)
                .clickable(enabled = !patch.isEmpty, role = Role.Button) { viewModel.apply(onClose) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                pluralStringResource(R.plurals.batch_apply, alarmIds.size, alarmIds.size),
                style = ClockTheme.typography.title,
                color = if (patch.isEmpty) colors.contentMuted else colors.onAccent,
            )
        }
    }
}

/** A field that stays "don't change" until switched on; then its editor appears. */
@Composable
private fun ChangeableField(
    title: Int,
    changing: Boolean,
    onChangingChange: (Boolean) -> Unit,
    editor: @Composable () -> Unit,
) {
    val colors = ClockTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = changing, role = Role.Switch, onValueChange = onChangingChange)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = ClockTheme.typography.body, color = colors.content)
                Text(
                    stringResource(if (changing) R.string.batch_change else R.string.batch_keep),
                    style = ClockTheme.typography.caption,
                    color = if (changing) colors.accent else colors.contentMuted,
                )
            }
            ClockSwitch(checked = changing, onCheckedChange = null)
        }
        if (changing) {
            Spacer(Modifier.height(8.dp))
            editor()
        }
    }
}
