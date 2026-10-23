package io.github.imlemos.clock.alarm.layout

import io.github.imlemos.clock.core.designsystem.rememberHaptics
import io.github.imlemos.clock.core.designsystem.component.DialogButtonStyle
import io.github.imlemos.clock.core.designsystem.component.DialogAction
import io.github.imlemos.clock.core.designsystem.component.GlassAlertDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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
import io.github.imlemos.clock.core.designsystem.component.OptionsSheet
import io.github.imlemos.clock.core.designsystem.component.SheetOption
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

    fun rename(id: Long, name: String) {
        if (name.isNotBlank()) viewModelScope.launch { repository.rename(id, name.trim()) }
    }

    fun delete(id: Long) = delete(setOf(id))

    fun delete(ids: Set<Long>) {
        viewModelScope.launch { ids.forEach { repository.delete(it) } }
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
    PresetLayouts.TALL -> stringResource(R.string.layout_preset_tall)
    PresetLayouts.CLASSIC -> stringResource(R.string.layout_preset_classic)
    else -> layout.name
}

/** Next free "Layout N" name. */
@Composable
fun newLayoutName(layouts: List<AlarmLayout>): String =
    stringResource(R.string.layout_new_name, layouts.count { !it.builtIn } + 1)

/**
 * The layout library (docs/PLANO.md 3.7): presets and the user's layouts, and the default one.
 * A long press selects the user's layouts for deleting several at once.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LayoutLibraryScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onNew: (fromId: Long, name: String) -> Unit,
    viewModel: LayoutLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    var options by remember { mutableStateOf<AlarmLayout?>(null) }
    var renaming by remember { mutableStateOf<AlarmLayout?>(null) }
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()
    val newName = newLayoutName(state.layouts)
    val sampleLabel = stringResource(R.string.layout_sample_label)
    // Layouts deleted elsewhere leave the selection.
    val visibleSelection = selection.filterTo(mutableSetOf()) { id -> state.layouts.any { it.id == id } }
    BackHandler(enabled = selecting) { selection = emptySet() }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        if (selecting) {
            ClockTopBar(
                title = pluralStringResource(R.plurals.layouts_selected, visibleSelection.size, visibleSelection.size),
                navigationIcon = { TopBarIconButton(R.drawable.ic_close, stringResource(R.string.action_clear_selection)) { selection = emptySet() } },
                actions = {
                    TopBarIconButton(R.drawable.ic_delete, stringResource(R.string.layout_delete)) { confirmingDelete = true }
                },
            )
        } else {
            ClockTopBar(
                title = stringResource(R.string.layouts_title),
                navigationIcon = { TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) },
                actions = {
                    TopBarIconButton(R.drawable.ic_add, stringResource(R.string.layout_new)) {
                        onNew(state.defaultId, newName)
                    }
                },
            )
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.layouts_intro), style = ClockTheme.typography.caption, color = colors.contentMuted)
            }
            items(state.layouts, key = { it.id }) { layout ->
                val shape = RoundedCornerShape(24.dp)
                val selected = layout.id in visibleSelection
                fun toggle() {
                    selection = if (selected) selection - layout.id else selection + layout.id
                }
                Row(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .clip(shape)
                        .background(colors.surface, shape)
                        .border(2.dp, if (selected) colors.accent else colors.surface, shape)
                        .semantics { this.selected = selected }
                        .combinedClickable(
                            onClickLabel = stringResource(
                                when {
                                    selecting -> R.string.layout_select
                                    layout.builtIn -> R.string.layout_options
                                    else -> R.string.layout_edit
                                },
                            ),
                            onLongClickLabel = stringResource(if (layout.builtIn) R.string.layout_options else R.string.layout_select),
                            onClick = {
                                when {
                                    selecting -> if (!layout.builtIn) toggle()
                                    layout.builtIn -> options = layout
                                    else -> onEdit(layout.id)
                                }
                            },
                            // Built-in layouts cannot be deleted, so they open their options instead.
                            onLongClick = {
                                haptics.longPress()
                                if (layout.builtIn) options = layout else toggle()
                            },
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
                    if (!selecting) {
                        TopBarIconButton(R.drawable.ic_more, stringResource(R.string.layout_options)) { options = layout }
                    }
                }
            }
        }
    }

    options?.let { layout ->
        val name = layoutName(layout)
        OptionsSheet(
            title = name,
            header = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LayoutThumbnail(layout.spec)
                    Spacer(Modifier.width(16.dp))
                    Text(name, style = ClockTheme.typography.title, color = colors.content)
                }
            },
            options = buildList {
                if (!layout.builtIn) add(SheetOption(stringResource(R.string.layout_edit), R.drawable.ic_edit) { options = null; onEdit(layout.id) })
                add(
                    SheetOption(stringResource(R.string.layout_test), R.drawable.ic_play) {
                        options = null
                        context.startActivity(AlarmActivity.previewIntent(context, layout.spec, sampleLabel))
                    },
                )
                add(
                    SheetOption(stringResource(R.string.layout_duplicate), R.drawable.ic_copy) {
                        options = null
                        onNew(layout.id, newName)
                    },
                )
                if (layout.id != state.defaultId) {
                    add(SheetOption(stringResource(R.string.layout_set_default), R.drawable.ic_star) { options = null; viewModel.setDefault(layout.id) })
                }
                if (!layout.builtIn) {
                    add(SheetOption(stringResource(R.string.layout_rename), R.drawable.ic_rename) { options = null; renaming = layout })
                    add(SheetOption(stringResource(R.string.layout_select), R.drawable.ic_select) { options = null; selection = selection + layout.id })
                    add(SheetOption(stringResource(R.string.layout_delete), R.drawable.ic_delete, destructive = true) { options = null; viewModel.delete(layout.id) })
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
    if (confirmingDelete) {
        GlassAlertDialog(
            title = pluralStringResource(R.plurals.layouts_delete_title, visibleSelection.size, visibleSelection.size),
            text = stringResource(R.string.layouts_delete_body),
            onDismiss = { confirmingDelete = false },
            actions = listOf(
                DialogAction(stringResource(R.string.action_cancel)) { confirmingDelete = false },
                DialogAction(stringResource(R.string.layout_delete), DialogButtonStyle.Destructive) {
                    viewModel.delete(visibleSelection)
                    selection = emptySet()
                    confirmingDelete = false
                },
            ),
        )
    }
}

/** A small, still picture of a layout: the real renderer drawn at phone size and scaled down. */
@Composable
fun LayoutThumbnail(spec: LayoutSpec, modifier: Modifier = Modifier, width: Dp = THUMB_WIDTH.dp) {
    // Alarm screens are always dark, whatever the app theme.
    ClockTheme(darkTheme = true, accent = ClockTheme.colors.accent, glassLevel = LocalGlassLevel.current) {
        ThumbnailContent(spec, modifier, width.value)
    }
}

@Composable
private fun ThumbnailContent(spec: LayoutSpec, modifier: Modifier, width: Float) {
    val backdrop = rememberGlassBackdrop()
    val now = remember { java.time.LocalTime.now() }
    val today = remember { java.time.LocalDate.now() }
    Box(
        modifier
            .size(width.dp, (width * PHONE_RATIO).dp)
            .clip(RoundedCornerShape(10.dp * (width / THUMB_WIDTH))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .requiredSize(PHONE_WIDTH.dp, (PHONE_WIDTH * PHONE_RATIO).dp)
                .graphicsLayer {
                    scaleX = width / PHONE_WIDTH
                    scaleY = width / PHONE_WIDTH
                },
        ) {
            AlarmLayoutView(
                spec = spec,
                time = TimeOfDay(now.hour, now.minute),
                date = today,
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
