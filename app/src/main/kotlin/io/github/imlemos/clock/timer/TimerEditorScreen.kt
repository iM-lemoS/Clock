package io.github.imlemos.clock.timer

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.DurationWheelLabels
import io.github.imlemos.clock.core.designsystem.component.DurationWheels
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.timer.data.TimerController
import io.github.imlemos.clock.timer.data.TimerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimerEditorState(
    val loaded: Boolean = false,
    val name: String = "",
    val hours: Int = 0,
    val minutes: Int = 5,
    val seconds: Int = 0,
) {
    val durationMillis: Long get() = ((hours * 60L + minutes) * 60 + seconds) * 1000
    val canSave: Boolean get() = loaded && durationMillis > 0
}

@HiltViewModel(assistedFactory = TimerEditorViewModel.Factory::class)
class TimerEditorViewModel @AssistedInject constructor(
    @Assisted private val timerId: Long,
    private val repository: TimerRepository,
    private val controller: TimerController,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(timerId: Long): TimerEditorViewModel
    }

    private val _state = MutableStateFlow(TimerEditorState())
    val state: StateFlow<TimerEditorState> = _state.asStateFlow()
    val isNew: Boolean = timerId == NEW_TIMER

    init {
        viewModelScope.launch {
            val timer = if (isNew) null else repository.get(timerId)
            val seconds = (timer?.run?.durationMillis ?: (5 * 60_000L)) / 1000
            _state.update {
                it.copy(loaded = true, name = timer?.name.orEmpty(), hours = (seconds / 3600).toInt(), minutes = (seconds / 60 % 60).toInt(), seconds = (seconds % 60).toInt())
            }
        }
    }

    fun setDuration(hours: Int, minutes: Int, seconds: Int) = _state.update { it.copy(hours = hours, minutes = minutes, seconds = seconds) }

    fun setName(name: String) = _state.update { it.copy(name = name.take(MAX_NAME)) }

    /** Saves and, with [start], starts it right away. */
    fun save(start: Boolean, onDone: () -> Unit) {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            val id = if (isNew) controller.create(current.name, current.durationMillis) else timerId.also { controller.edit(it, current.name, current.durationMillis) }
            if (start) controller.start(id)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            if (!isNew) controller.delete(timerId)
            onDone()
        }
    }

    companion object {
        const val NEW_TIMER = 0L
        const val MAX_NAME = 40
    }
}

/** Creates or edits a saved timer: duration on wheels (up to 23:59:59) and an optional name. */
@Composable
fun TimerEditorScreen(
    timerId: Long,
    onClose: () -> Unit,
    viewModel: TimerEditorViewModel = hiltViewModel<TimerEditorViewModel, TimerEditorViewModel.Factory>(
        key = "timer-editor-$timerId",
        creationCallback = { it.create(timerId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ClockTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        ClockTopBar(
            title = stringResource(if (viewModel.isNew) R.string.timer_new_title else R.string.timer_edit_title),
            navigationIcon = { TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClose) },
            actions = {
                if (!viewModel.isNew) TopBarIconButton(R.drawable.ic_delete, stringResource(R.string.timer_delete)) { viewModel.delete(onClose) }
            },
        )
        if (!state.loaded) return@Column
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            DurationWheels(
                hours = state.hours,
                minutes = state.minutes,
                seconds = state.seconds,
                onChange = viewModel::setDuration,
                labels = DurationWheelLabels(
                    hours = stringResource(R.string.wheel_hours),
                    minutes = stringResource(R.string.wheel_minutes),
                    seconds = stringResource(R.string.wheel_seconds),
                    increase = stringResource(R.string.wheel_increase),
                    decrease = stringResource(R.string.wheel_decrease),
                ),
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.timer_name)) },
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
        }
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorButton(stringResource(R.string.action_save), accent = false, enabled = state.canSave, modifier = Modifier.weight(1f)) {
                viewModel.save(start = false, onDone = onClose)
            }
            EditorButton(stringResource(R.string.timer_start), accent = true, enabled = state.canSave, modifier = Modifier.weight(1f)) {
                viewModel.save(start = true, onDone = onClose)
            }
        }
    }
}

@Composable
private fun EditorButton(text: String, accent: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = ClockTheme.colors
    Box(
        modifier
            .heightIn(min = 56.dp)
            .clip(CapsuleShape)
            .background(if (accent && enabled) colors.accent else colors.background, CapsuleShape)
            .border(1.dp, if (accent && enabled) colors.accent else colors.outline, CapsuleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = ClockTheme.typography.title,
            color = when {
                !enabled -> colors.contentMuted
                accent -> colors.onAccent
                else -> colors.content
            },
        )
    }
}
