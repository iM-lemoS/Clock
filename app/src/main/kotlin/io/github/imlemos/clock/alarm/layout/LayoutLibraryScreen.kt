package io.github.imlemos.clock.alarm.layout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.layout.render.AlarmLayoutView
import io.github.imlemos.clock.alarm.layout.render.LayoutLabel
import io.github.imlemos.clock.alarm.ringing.AlarmActivity
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.OptionsDialog
import io.github.imlemos.clock.core.designsystem.component.TextInputDialog
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.glass.LocalGlassLevel
import io.github.imlemos.clock.core.designsystem.glass.rememberGlassBackdrop
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LayoutLibraryState(val layouts: List<AlarmLayout> = emptyList(), val defaultId: Long = PresetLayouts.DEFAULT)

@HiltViewModel
class LayoutLibraryViewModel @Inject constructor(
    private val repository: LayoutRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<LayoutLibraryState> =
        combine(repository.observeAll(), settings.defaultLayoutId) { layouts, defaultId ->
            LayoutLibraryState(layouts, defaultId?.takeIf { id -> layouts.any { it.id == id } } ?: PresetLayouts.DEFAULT)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LayoutLibraryState())

    /** A copy of [fromId] named [name]; [onCreated] receives its id (to open the editor). */
    fun duplicate(fromId: Long, name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repository.duplicate(fromId, name)) }
    }

    fun rename(id: Long, name: String) {
        if (name.isNotBlank()) viewModelScope.launch { repository.rename(id, name.trim()) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch { settings.setDefaultLayoutId(id.takeIf { it != PresetLayouts.DEFAULT }) }
    }
}

/** Name shown for a layout: built-in presets have translated names. */
@Composable
fun layoutName(layout: AlarmLayout): String = when (layout.id) {
    PresetLayouts.DOTS -> stringResource(R.string.layout_preset_dots)
    PresetLayouts.WAVES -> stringResource(R.string.layout_preset_waves)
    PresetLayouts.MATRIX -> stringResource(R.string.layout_preset_matrix)
    PresetLayouts.ANALOG -> stringResource(R.string.layout_preset_analog)
    else -> layout.name
}

/** Next free "Layout N" name. */
@Composable
fun newLayoutName(layouts: List<AlarmLayout>): String =
    stringResource(R.string.layout_new_name, layouts.count { !it.builtIn } + 1)

/** The layout library (docs/PLANO.md 3.7): presets and the user's layouts, and the default one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LayoutLibraryScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: LayoutLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = ClockTheme.colors
    var options by remember { mutableStateOf<AlarmLayout?>(null) }
    var renaming by remember { mutableStateOf<AlarmLayout?>(null) }
    val newName = newLayoutName(state.layouts)
    val sampleLabel = stringResource(R.string.layout_sample_label)

    Column(Modifier.fillMaxSize().background(colors.background)) {
        ClockTopBar(
            title = stringResource(R.string.layouts_title),
            navigationIcon = { TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) },
            actions = {
                TopBarIconButton(R.drawable.ic_add, stringResource(R.string.layout_new)) {
                    viewModel.duplicate(state.defaultId, newName, onEdit)
                }
            },
        )
        LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.layouts_intro), style = ClockTheme.typography.caption, color = colors.contentMuted)
            }
            items(state.layouts, key = { it.id }) { layout ->
                val shape = RoundedCornerShape(24.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(colors.surface, shape)
                        .combinedClickable(
                            onClickLabel = stringResource(if (layout.builtIn) R.string.layout_options else R.string.layout_edit),
                            onLongClickLabel = stringResource(R.string.layout_options),
                            onClick = { if (layout.builtIn) options = layout else onEdit(layout.id) },
                            onLongClick = { options = layout },
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LayoutThumbnail(layout.spec)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(layoutName(layout), style = ClockTheme.typography.title, color = colors.content)
                        Spacer(Modifier.height(4.dp))
                        val tags = listOfNotNull(
                            stringResource(R.string.layout_default_badge).takeIf { layout.id == state.defaultId },
                            stringResource(R.string.layout_built_in).takeIf { layout.builtIn },
                        )
                        if (tags.isNotEmpty()) {
                            Text(
                                tags.joinToString(" · "),
                                style = ClockTheme.typography.caption,
                                color = if (layout.id == state.defaultId) colors.accent else colors.contentMuted,
                            )
                        }
                    }
                }
            }
        }
    }

    options?.let { layout ->
        val name = layoutName(layout)
        OptionsDialog(
            title = name,
            options = buildList {
                if (!layout.builtIn) add(stringResource(R.string.layout_edit) to { options = null; onEdit(layout.id) })
                add(stringResource(R.string.layout_test) to {
                    options = null
                    context.startActivity(AlarmActivity.previewIntent(context, layout.id, sampleLabel))
                })
                add(stringResource(R.string.layout_duplicate) to {
                    options = null
                    viewModel.duplicate(layout.id, newName, onEdit)
                })
                if (layout.id != state.defaultId) add(stringResource(R.string.layout_set_default) to { options = null; viewModel.setDefault(layout.id) })
                if (!layout.builtIn) {
                    add(stringResource(R.string.layout_rename) to { options = null; renaming = layout })
                    add(stringResource(R.string.layout_delete) to { options = null; viewModel.delete(layout.id) })
                }
            },
            onDismiss = { options = null },
        )
    }
    renaming?.let { layout ->
        TextInputDialog(
            title = stringResource(R.string.layout_rename),
            label = stringResource(R.string.layout_name),
            initial = layout.name,
            maxLength = 40,
            onConfirm = { viewModel.rename(layout.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

/** A small, still picture of a layout: the real renderer drawn at phone size and scaled down. */
@Composable
fun LayoutThumbnail(spec: LayoutSpec, modifier: Modifier = Modifier) {
    // Alarm screens are always dark, whatever the app theme.
    ClockTheme(darkTheme = true, accent = ClockTheme.colors.accent, glassLevel = LocalGlassLevel.current) {
        ThumbnailContent(spec, modifier)
    }
}

@Composable
private fun ThumbnailContent(spec: LayoutSpec, modifier: Modifier) {
    val backdrop = rememberGlassBackdrop()
    val now = remember { java.time.LocalTime.now() }
    Box(
        modifier
            .size(THUMB_WIDTH.dp, (THUMB_WIDTH * PHONE_RATIO).dp)
            .clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .requiredSize(PHONE_WIDTH.dp, (PHONE_WIDTH * PHONE_RATIO).dp)
                .graphicsLayer {
                    scaleX = THUMB_WIDTH / PHONE_WIDTH
                    scaleY = THUMB_WIDTH / PHONE_WIDTH
                },
        ) {
            AlarmLayoutView(
                spec = spec,
                time = TimeOfDay(now.hour, now.minute),
                date = "",
                labels = listOf(LayoutLabel("")),
                backdrop = backdrop,
                controls = { ThumbnailControls() },
                animate = false,
            )
        }
    }
}

/** Plain shapes standing in for the buttons in thumbnails. */
@Composable
private fun ThumbnailControls() {
    val colors = ClockTheme.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(160.dp, 64.dp).clip(RoundedCornerShape(32.dp)).background(colors.surfaceRaised))
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(36.dp)).background(colors.surfaceRaised))
    }
}

private const val THUMB_WIDTH = 64f
private const val PHONE_WIDTH = 360f
private const val PHONE_RATIO = 2.1f
