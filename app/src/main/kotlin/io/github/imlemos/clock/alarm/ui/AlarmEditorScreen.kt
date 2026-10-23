package io.github.imlemos.clock.alarm.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.scheduling.TimeOfDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    alarmId: Long,
    onClose: () -> Unit,
    viewModel: AlarmEditorViewModel = hiltViewModel<AlarmEditorViewModel, AlarmEditorViewModel.Factory>(
        key = "alarm-editor-$alarmId",
        creationCallback = { factory -> factory.create(alarmId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val colors = ClockTheme.colors
    var keyboardInput by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val locale = LocalConfiguration.current.locales[0]

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
                        onClick = { viewModel.delete(onClose) },
                    )
                }
            },
        )
        if (!state.loaded) return@Column

        val pickerState = rememberTimePickerState(
            initialHour = state.time.hour,
            initialMinute = state.time.minute,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
        )
        LaunchedEffect(pickerState) {
            snapshotFlow { TimeOfDay(pickerState.hour, pickerState.minute) }.collect(viewModel::setTime)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            val pickerColors = TimePickerDefaults.colors(
                clockDialColor = colors.surface,
                clockDialSelectedContentColor = colors.onAccent,
                clockDialUnselectedContentColor = colors.content,
                selectorColor = colors.accent,
                containerColor = colors.background,
                periodSelectorBorderColor = colors.outline,
                periodSelectorSelectedContainerColor = colors.content,
                periodSelectorUnselectedContainerColor = colors.background,
                periodSelectorSelectedContentColor = colors.background,
                periodSelectorUnselectedContentColor = colors.content,
                timeSelectorSelectedContainerColor = colors.surfaceRaised,
                timeSelectorUnselectedContainerColor = colors.surface,
                timeSelectorSelectedContentColor = colors.content,
                timeSelectorUnselectedContentColor = colors.contentMuted,
            )
            if (keyboardInput) {
                TimeInput(state = pickerState, colors = pickerColors)
            } else {
                TimePicker(state = pickerState, colors = pickerColors)
            }
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
                    .background(colors.accent, CapsuleShape)
                    .clickable(role = Role.Button) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.save { saved ->
                            saved.nextRing?.let { next ->
                                val text = resources.getString(
                                    R.string.alarm_set_toast,
                                    describeNextRing(resources, saved.now, next, locale),
                                )
                                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                            }
                            onClose()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.action_save), style = ClockTheme.typography.title, color = colors.onAccent)
            }
        }
    }
}
