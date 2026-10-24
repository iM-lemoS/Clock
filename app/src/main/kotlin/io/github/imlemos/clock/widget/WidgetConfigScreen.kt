package io.github.imlemos.clock.widget

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.layout.ClockFont
import io.github.imlemos.clock.alarm.layout.GradientDirection
import io.github.imlemos.clock.alarm.layout.editor.ColorSwatches
import io.github.imlemos.clock.alarm.layout.editor.DateOptions
import io.github.imlemos.clock.alarm.layout.editor.EditorTab
import io.github.imlemos.clock.alarm.layout.editor.EditorTabRow
import io.github.imlemos.clock.alarm.layout.editor.FontChips
import io.github.imlemos.clock.alarm.layout.editor.OptionChips
import io.github.imlemos.clock.alarm.layout.editor.ToggleChips
import io.github.imlemos.clock.alarm.layout.editor.sliderColors
import io.github.imlemos.clock.alarm.layout.render.spec
import io.github.imlemos.clock.alarm.ui.Label
import io.github.imlemos.clock.core.designsystem.component.ClockSwitch
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.SegmentedControl
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.rememberHaptics
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import kotlin.math.roundToInt

/**
 * The widget editor (docs/PLANO.md 5.2): a live preview drawn exactly like the widget, ready-made
 * styles, and every setting grouped in tabs.
 */
@Composable
fun WidgetConfigScreen(viewModel: WidgetConfigViewModel, onClose: () -> Unit, onSaved: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = ClockTheme.colors
    LaunchedEffect(colors.isDark) { viewModel.initPreviewNight(colors.isDark) }
    val haptics = rememberHaptics()
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        ClockTopBar(
            title = stringResource(if (state.kind == WidgetKind.CLOCK) R.string.widget_editor_clock_title else R.string.widget_editor_next_alarm_title),
            navigationIcon = { TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClose) },
        )
        if (!state.loaded) return@Column
        Preview(state, onNight = viewModel::setPreviewNight)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Label(stringResource(R.string.widget_styles))
            PresetRow(state, onPick = viewModel::applyPreset)
            Spacer(Modifier.height(8.dp))
            EditorTabRow(
                tabs = listOf(
                    EditorTab(WidgetTab.TIME, R.string.widget_tab_time, R.drawable.ic_schedule),
                    EditorTab(WidgetTab.DETAILS, R.string.widget_tab_details, R.drawable.ic_calendar_today),
                    EditorTab(WidgetTab.BACKGROUND, R.string.widget_tab_background, R.drawable.ic_wallpaper),
                    EditorTab(WidgetTab.LAYOUT, R.string.widget_tab_layout, R.drawable.ic_align),
                ),
                selected = state.tab,
                onSelect = viewModel::showTab,
            )
            Spacer(Modifier.height(16.dp))
            when (state.tab) {
                WidgetTab.TIME -> TimeOptions(state.config.time) { change -> viewModel.edit { it.copy(time = change(it.time)) } }
                WidgetTab.DETAILS -> DetailsOptions(state.kind, state.config, viewModel::edit)
                WidgetTab.BACKGROUND -> BackgroundOptions(state.config.background, viewModel)
                WidgetTab.LAYOUT -> LayoutOptions(state.config, viewModel::edit)
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
                .background(colors.accent, CapsuleShape)
                .clickable(role = Role.Button) {
                    haptics.confirm()
                    viewModel.save(onSaved)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.action_save), style = ClockTheme.typography.title, color = colors.onAccent)
        }
    }
}

/** The widget at its proportions over a stand-in home screen, light or dark. */
@Composable
private fun Preview(state: WidgetConfigState, onNight: (Boolean) -> Unit) {
    val backdrop = if (state.previewNight) {
        Brush.linearGradient(listOf(Color(0xFF34343A), Color(0xFF0E0E10)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFC9CBD2), Color(0xFFF2F2F4)))
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(PREVIEW_HEIGHT)
                .clip(RoundedCornerShape(24.dp))
                .background(backdrop),
            contentAlignment = Alignment.Center,
        ) {
            state.preview?.let { bitmap ->
                val ratio = if (state.heightDp > 0) state.widthDp.toFloat() / state.heightDp else 2f
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(16.dp)
                        .aspectRatio(ratio.coerceIn(0.4f, 4f), matchHeightConstraintsFirst = ratio < 2.2f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SegmentedControl(
            options = listOf(false to stringResource(R.string.widget_preview_light), true to stringResource(R.string.widget_preview_dark)),
            selected = state.previewNight,
            onSelect = onNight,
        )
    }
}

/** The ready-made looks, each drawn with the current content. */
@Composable
private fun PresetRow(state: WidgetConfigState, onPick: (WidgetPreset) -> Unit) {
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WidgetPreset.entries.forEach { preset ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, colors.content.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .selectable(selected = false, role = Role.Button) {
                        haptics.tick()
                        onPick(preset)
                    }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 112.dp, height = 64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (state.previewNight) Color(0xFF26262B) else Color(0xFFDADBE0)),
                    contentAlignment = Alignment.Center,
                ) {
                    state.presetPreviews[preset]?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.padding(4.dp)) }
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(preset.label), style = ClockTheme.typography.caption, color = colors.content)
            }
        }
    }
}

@Composable
private fun TimeOptions(time: WidgetTime, edit: ((WidgetTime) -> WidgetTime) -> Unit) {
    Label(stringResource(R.string.widget_shape))
    OptionChips(
        options = listOf(
            TimeArrangement.INLINE to stringResource(R.string.widget_shape_inline),
            TimeArrangement.STACKED to stringResource(R.string.widget_shape_stacked),
        ),
        selected = time.arrangement,
        onSelect = { value -> edit { it.copy(arrangement = value) } },
    )
    Spacer(Modifier.height(12.dp))
    Label(stringResource(R.string.layout_font))
    FontChips(selected = time.font) { font -> edit { it.copy(font = font, weight = null) } }
    WeightSlider(time.font, time.weight) { value -> edit { it.copy(weight = value) } }
    if (time.font == ClockFont.DOTO) {
        Label(stringResource(R.string.layout_roundness))
        Slider(
            value = time.roundness.toFloat(),
            onValueChange = { value -> edit { it.copy(roundness = value.roundToInt()) } },
            valueRange = 0f..100f,
            colors = sliderColors(),
        )
    }
    if (time.arrangement == TimeArrangement.INLINE) {
        Label(stringResource(R.string.widget_separator))
        OptionChips(
            options = listOf(
                SeparatorStyle.DOTS to stringResource(R.string.widget_separator_dots),
                SeparatorStyle.GLYPH to stringResource(R.string.widget_separator_glyph),
                SeparatorStyle.NONE to stringResource(R.string.widget_separator_none),
            ),
            selected = time.separator,
            onSelect = { value -> edit { it.copy(separator = value) } },
        )
        Spacer(Modifier.height(12.dp))
    }
    ColorField(stringResource(R.string.widget_time_color), time.color, WidgetColor.Content) { value -> edit { it.copy(color = value) } }
    if (time.arrangement == TimeArrangement.INLINE && time.separator != SeparatorStyle.NONE) {
        ColorField(stringResource(R.string.widget_separator_color), time.separatorColor, WidgetColor.Content) { value -> edit { it.copy(separatorColor = value) } }
    }
    Label(stringResource(R.string.layout_size))
    Slider(
        value = time.scale,
        onValueChange = { value -> edit { it.copy(scale = value) } },
        valueRange = WidgetConfig.MIN_TIME_SCALE..1f,
        colors = sliderColors(),
    )
}

@Composable
private fun DetailsOptions(kind: WidgetKind, config: WidgetConfig, edit: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    if (kind == WidgetKind.CLOCK) {
        SwitchRow(stringResource(R.string.widget_show_date), config.showDate) { on -> edit { it.copy(showDate = on) } }
        if (config.showDate) DateOptions(config.date) { parts -> edit { it.copy(date = parts) } }
    } else {
        Label(stringResource(R.string.widget_show))
        ToggleChips(
            listOf(
                stringResource(R.string.widget_show_day) to config.showDay,
                stringResource(R.string.widget_show_label) to config.showLabel,
                stringResource(R.string.widget_show_countdown) to config.showCountdown,
                stringResource(R.string.widget_show_skip) to config.showSkip,
            ),
        ) { index, on ->
            edit {
                when (index) {
                    0 -> it.copy(showDay = on)
                    1 -> it.copy(showLabel = on)
                    2 -> it.copy(showCountdown = on)
                    else -> it.copy(showSkip = on)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    val details = config.details
    Label(stringResource(R.string.layout_font))
    FontChips(selected = details.font) { font -> edit { it.copy(details = it.details.copy(font = font, weight = null)) } }
    WeightSlider(details.font, details.weight) { value -> edit { it.copy(details = it.details.copy(weight = value)) } }
    ColorField(stringResource(R.string.widget_details_color), details.color, WidgetColor.Muted) { value ->
        edit { it.copy(details = it.details.copy(color = value)) }
    }
    Label(stringResource(R.string.layout_size))
    Slider(
        value = details.scale,
        onValueChange = { value -> edit { it.copy(details = it.details.copy(scale = value)) } },
        valueRange = WidgetConfig.MIN_DETAILS_SCALE..WidgetConfig.MAX_DETAILS_SCALE,
        colors = sliderColors(),
    )
}

@Composable
private fun BackgroundOptions(background: WidgetBackground, viewModel: WidgetConfigViewModel) {
    val context = LocalContext.current
    val failed = stringResource(R.string.layout_import_failed)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importPhoto(uri) { Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() }
    }
    fun edit(change: (WidgetBackground) -> WidgetBackground) = viewModel.edit { it.copy(background = change(it.background)) }
    Label(stringResource(R.string.widget_bg_type))
    OptionChips(
        options = listOf(
            WidgetBackgroundType.NONE to stringResource(R.string.widget_bg_none),
            WidgetBackgroundType.SOLID to stringResource(R.string.widget_bg_solid),
            WidgetBackgroundType.GRADIENT to stringResource(R.string.widget_bg_gradient),
            WidgetBackgroundType.PHOTO to stringResource(R.string.widget_bg_photo),
        ),
        selected = background.type,
        onSelect = { type ->
            if (type == WidgetBackgroundType.PHOTO && background.photo == null) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                edit { it.copy(type = type) }
            }
        },
    )
    Spacer(Modifier.height(12.dp))
    when (background.type) {
        WidgetBackgroundType.NONE -> Unit
        WidgetBackgroundType.SOLID ->
            ColorField(stringResource(R.string.widget_bg_color), background.color, WidgetColor.Surface) { value -> edit { it.copy(color = value) } }
        WidgetBackgroundType.GRADIENT -> {
            Label(stringResource(R.string.layout_gradient_direction))
            OptionChips(
                options = listOf(
                    GradientDirection.DOWN to stringResource(R.string.layout_gradient_down),
                    GradientDirection.ACROSS to stringResource(R.string.layout_gradient_across),
                    GradientDirection.DIAGONAL_DOWN to stringResource(R.string.layout_gradient_diagonal_down),
                    GradientDirection.DIAGONAL_UP to stringResource(R.string.layout_gradient_diagonal_up),
                ),
                selected = background.direction,
                onSelect = { value -> edit { it.copy(direction = value) } },
            )
            Spacer(Modifier.height(12.dp))
            ColorField(stringResource(R.string.widget_bg_color), background.color, WidgetColor.Surface) { value -> edit { it.copy(color = value) } }
            ColorField(stringResource(R.string.widget_bg_color_b), background.colorB, WidgetColor.Surface) { value -> edit { it.copy(colorB = value) } }
        }
        WidgetBackgroundType.PHOTO -> {
            // A single chip that is never "selected": it opens the photo picker.
            OptionChips(
                options = listOf(true to stringResource(R.string.widget_change_photo)),
                selected = false,
                onSelect = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            Spacer(Modifier.height(12.dp))
            Label(stringResource(R.string.layout_dim))
            Slider(
                value = background.dim.toFloat(),
                onValueChange = { value -> edit { it.copy(dim = value.roundToInt()) } },
                valueRange = 0f..WidgetConfig.MAX_DIM.toFloat(),
                colors = sliderColors(),
            )
        }
    }
    if (background.type != WidgetBackgroundType.NONE) {
        Label(stringResource(R.string.widget_opacity))
        Slider(
            value = background.opacity.toFloat(),
            onValueChange = { value -> edit { it.copy(opacity = value.roundToInt()) } },
            valueRange = 0f..100f,
            colors = sliderColors(),
        )
    }
    Label(stringResource(R.string.widget_corners))
    Slider(
        value = background.cornerRadius.toFloat(),
        onValueChange = { value -> edit { it.copy(cornerRadius = value.roundToInt()) } },
        valueRange = 0f..WidgetConfig.MAX_RADIUS.toFloat(),
        colors = sliderColors(),
    )
}

@Composable
private fun LayoutOptions(config: WidgetConfig, edit: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    Label(stringResource(R.string.widget_alignment))
    OptionChips(
        options = listOf(
            WidgetAlignment.START to stringResource(R.string.widget_align_start),
            WidgetAlignment.CENTER to stringResource(R.string.widget_align_center),
            WidgetAlignment.END to stringResource(R.string.widget_align_end),
        ),
        selected = config.alignment,
        onSelect = { value -> edit { it.copy(alignment = value) } },
    )
    Spacer(Modifier.height(12.dp))
    Label(stringResource(R.string.widget_padding))
    Slider(
        value = config.padding.toFloat(),
        onValueChange = { value -> edit { it.copy(padding = value.roundToInt()) } },
        valueRange = 0f..WidgetConfig.MAX_PADDING.toFloat(),
        colors = sliderColors(),
    )
}

/** Thickness within the font's range; starts at the font's default. */
@Composable
private fun WeightSlider(font: ClockFont, weight: Int?, onChange: (Int) -> Unit) {
    val spec = font.spec
    Spacer(Modifier.height(8.dp))
    Label(stringResource(R.string.layout_weight))
    Slider(
        value = WidgetRenderer.effectiveWeight(font, weight).toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = spec.minWeight.toFloat()..spec.maxWeight.toFloat(),
        colors = sliderColors(),
    )
}

/**
 * A colour: automatic ([auto], which follows the light or dark home screen), the app's accent,
 * or any colour from the picker.
 */
@Composable
private fun ColorField(title: String, color: WidgetColor, auto: WidgetColor, onChange: (WidgetColor) -> Unit) {
    Label(title)
    val choice = when (color.mode) {
        WidgetColor.Mode.ACCENT -> WidgetColor.Mode.ACCENT
        WidgetColor.Mode.CUSTOM -> WidgetColor.Mode.CUSTOM
        else -> auto.mode
    }
    OptionChips(
        options = listOf(
            auto.mode to stringResource(R.string.widget_color_auto),
            WidgetColor.Mode.ACCENT to stringResource(R.string.widget_color_accent),
            WidgetColor.Mode.CUSTOM to stringResource(R.string.widget_color_custom),
        ),
        selected = choice,
        onSelect = { mode ->
            onChange(
                when (mode) {
                    WidgetColor.Mode.ACCENT -> WidgetColor.Accent
                    WidgetColor.Mode.CUSTOM -> WidgetColor.custom(if (color.mode == WidgetColor.Mode.CUSTOM) color.argb else DEFAULT_CUSTOM)
                    else -> auto
                },
            )
        },
    )
    Spacer(Modifier.height(8.dp))
    if (color.mode == WidgetColor.Mode.CUSTOM) {
        ColorSwatches(selected = color.argb, allowAuto = false, showLabel = false) { argb -> argb?.let { onChange(WidgetColor.custom(it)) } }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = ClockTheme.typography.body, color = ClockTheme.colors.content, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        ClockSwitch(checked = checked, onCheckedChange = null)
    }
}

private val PREVIEW_HEIGHT = 190.dp
private const val DEFAULT_CUSTOM = 0xFFFFFFFF.toInt()
