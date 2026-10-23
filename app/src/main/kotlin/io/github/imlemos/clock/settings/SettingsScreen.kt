package io.github.imlemos.clock.settings

import io.github.imlemos.clock.core.designsystem.rememberHaptics
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.LocalResources
import io.github.imlemos.clock.core.designsystem.component.DurationPickerDialog
import io.github.imlemos.clock.alarm.ui.formatNotice
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.imlemos.clock.R
import androidx.compose.ui.graphics.Color
import io.github.imlemos.clock.core.designsystem.component.ColorPickerDialog
import io.github.imlemos.clock.alarm.ui.ToneField
import io.github.imlemos.clock.alarm.layout.layoutName
import io.github.imlemos.clock.alarm.ui.SoundSection
import io.github.imlemos.clock.core.data.prefs.AudioOutput
import io.github.imlemos.clock.core.data.prefs.ThemeMode
import io.github.imlemos.clock.core.data.prefs.TimeFormat
import io.github.imlemos.clock.core.data.prefs.VolumeButtonAction
import io.github.imlemos.clock.core.designsystem.component.ClockSwitch
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.SegmentedControl
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.glass.rememberSystemReducesEffects
import io.github.imlemos.clock.core.designsystem.theme.AccentPalette
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.zones.ZoneNames
import io.github.imlemos.clock.core.zones.ZonePickerDialog
import java.time.ZoneId

/** The settings categories, each on its own page (docs/PLANO.md section 6). */
enum class SettingsSection(@param:StringRes val title: Int, @param:StringRes val summary: Int, @param:DrawableRes val icon: Int) {
    APPEARANCE(R.string.settings_section_appearance, R.string.settings_appearance_summary, R.drawable.ic_palette),
    ALARMS(R.string.settings_section_alarms, R.string.settings_alarms_summary, R.drawable.ic_alarm),
    SOUND(R.string.settings_section_sound, R.string.settings_sound_summary, R.drawable.ic_volume_up),
    TIMERS(R.string.settings_section_timers, R.string.settings_timers_summary, R.drawable.ic_hourglass_empty),
    DATE_TIME(R.string.settings_section_datetime, R.string.settings_datetime_summary, R.drawable.ic_schedule),
}

/** The settings home: the categories and the other pages, grouped in cards. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    onOpenReliability: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenLayouts: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenGlassLab: (() -> Unit)?,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    SettingsPage(stringResource(R.string.settings_title), onBack) {
        SettingsCard {
            SettingsSection.entries.forEachIndexed { index, section ->
                if (index > 0) CardDivider()
                ClickableRow(
                    title = stringResource(section.title),
                    summary = stringResource(section.summary),
                    icon = section.icon,
                    onClick = { onOpenSection(section) },
                )
            }
        }
        SettingsCard {
            val defaultLayout by viewModel.defaultLayout.collectAsStateWithLifecycle()
            ClickableRow(
                title = stringResource(R.string.settings_layouts),
                summary = stringResource(R.string.settings_layouts_summary, defaultLayout?.let { layoutName(it) }.orEmpty()),
                icon = R.drawable.ic_wallpaper,
                onClick = onOpenLayouts,
            )
            CardDivider()
            ClickableRow(
                title = stringResource(R.string.settings_reliability),
                summary = stringResource(R.string.settings_reliability_summary),
                icon = R.drawable.ic_verified_user,
                onClick = onOpenReliability,
            )
            CardDivider()
            ClickableRow(
                title = stringResource(R.string.settings_trash),
                summary = stringResource(R.string.settings_trash_summary),
                icon = R.drawable.ic_delete,
                onClick = onOpenTrash,
            )
        }
        SettingsCard {
            // Per-app language settings exist from Android 13; on Android 12 the app follows the system.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClickableRow(
                    title = stringResource(R.string.settings_language),
                    summary = stringResource(R.string.settings_language_summary),
                    icon = R.drawable.ic_translate,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri()),
                        )
                    },
                )
                CardDivider()
            }
            ClickableRow(
                title = stringResource(R.string.settings_about),
                summary = stringResource(R.string.settings_about_summary),
                icon = R.drawable.ic_info,
                onClick = onOpenAbout,
            )
        }
        if (onOpenGlassLab != null) {
            SectionHeader(stringResource(R.string.settings_section_developer))
            SettingsCard {
                ClickableRow(
                    title = stringResource(R.string.settings_glass_lab),
                    summary = stringResource(R.string.settings_glass_lab_summary),
                    icon = R.drawable.ic_code,
                    onClick = onOpenGlassLab,
                )
                CardDivider()
                val testLabel = stringResource(R.string.test_alarm_label)
                ClickableRow(
                    title = stringResource(R.string.settings_test_alarm),
                    summary = stringResource(R.string.settings_test_alarm_summary),
                    icon = R.drawable.ic_alarm,
                    onClick = { viewModel.createTestAlarm(testLabel) },
                )
            }
        }
    }
}

/** One settings category on its own page. */
@Composable
fun SettingsSectionScreen(section: SettingsSection, onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    SettingsPage(stringResource(section.title), onBack) {
        when (section) {
            SettingsSection.APPEARANCE -> AppearanceSettings(viewModel)
            SettingsSection.ALARMS -> AlarmSettings(viewModel)
            SettingsSection.SOUND -> SoundSettings(viewModel)
            SettingsSection.TIMERS -> TimerSettings(viewModel)
            SettingsSection.DATE_TIME -> DateTimeSettings(viewModel)
        }
    }
}

@Composable
private fun AppearanceSettings(viewModel: SettingsViewModel) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val systemReducesEffects = rememberSystemReducesEffects()
    SettingsCard {
        SettingLabel(stringResource(R.string.settings_theme))
        ThemeSelector(selected = appearance.themeMode, onSelect = viewModel::setThemeMode)
        Spacer(Modifier.height(16.dp))
    }
    SettingsCard {
        SettingLabel(stringResource(R.string.settings_accent))
        AccentSelector(
            selected = AccentPalette.matching(appearance.accentArgb),
            customArgb = appearance.accentArgb?.takeIf { AccentPalette.matching(it) == null },
            onSelect = { accent ->
                viewModel.setAccent(if (accent == AccentPalette.Default) null else accent.color.toArgb())
            },
            onCustom = viewModel::setAccent,
        )
        Spacer(Modifier.height(12.dp))
    }
    SettingsCard {
        SwitchRow(
            title = stringResource(R.string.settings_reduce_transparency),
            summary = stringResource(
                if (systemReducesEffects) {
                    R.string.settings_reduce_transparency_forced
                } else {
                    R.string.settings_reduce_transparency_summary
                },
            ),
            checked = appearance.reduceTransparency || systemReducesEffects,
            enabled = !systemReducesEffects,
            onCheckedChange = viewModel::setReduceTransparency,
        )
    }
}

@Composable
private fun AlarmSettings(viewModel: SettingsViewModel) {
    SettingsCard {
        SettingLabel(stringResource(R.string.settings_notices))
        Hint(stringResource(R.string.settings_notices_summary))
        val notices by viewModel.noticeMinutes.collectAsStateWithLifecycle()
        NoticeChips(selected = notices, onToggle = viewModel::toggleNotice)
        Spacer(Modifier.height(16.dp))
    }
    SettingsCard {
        SettingLabel(stringResource(R.string.settings_volume_button))
        Hint(stringResource(R.string.settings_volume_button_summary))
        val volumeAction by viewModel.volumeButtonAction.collectAsStateWithLifecycle()
        RadioList(
            options = listOf(
                VolumeButtonAction.OFF to R.string.volume_button_off,
                VolumeButtonAction.SILENCE_SOUND to R.string.volume_button_sound,
                VolumeButtonAction.SILENCE_SOUND_AND_VIBRATION to R.string.volume_button_sound_vibration,
            ),
            selected = volumeAction,
            onSelect = viewModel::setVolumeButtonAction,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SoundSettings(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.settings_default_sound_summary),
        style = ClockTheme.typography.caption,
        color = ClockTheme.colors.contentMuted,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
    )
    SettingsCard {
        val defaultSound by viewModel.defaultSound.collectAsStateWithLifecycle()
        SoundSection(
            sound = defaultSound,
            onChange = viewModel::setDefaultSound,
            onSystemTonePicked = viewModel::setDefaultSystemTone,
            onFilePicked = { uri ->
                viewModel.importDefaultTone(uri) {
                    Toast.makeText(context, R.string.sound_import_failed, Toast.LENGTH_SHORT).show()
                }
            },
            onPreviewVibration = viewModel::previewVibration,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        )
    }
    SettingsCard {
        SettingLabel(stringResource(R.string.audio_output))
        val output by viewModel.audioOutput.collectAsStateWithLifecycle()
        RadioList(
            options = listOf(
                AudioOutput.PHONE_AND_BLUETOOTH to R.string.output_both,
                AudioOutput.PHONE_ONLY to R.string.output_phone,
                AudioOutput.BLUETOOTH_ONLY to R.string.output_bluetooth,
            ),
            selected = output,
            onSelect = viewModel::setAudioOutput,
        )
        Hint(stringResource(R.string.output_bluetooth_summary))
        Spacer(Modifier.height(8.dp))
    }
    SettingsCard {
        val warnLow by viewModel.warnLowVolume.collectAsStateWithLifecycle()
        SwitchRow(
            title = stringResource(R.string.settings_warn_low_volume),
            summary = stringResource(R.string.settings_warn_low_volume_summary),
            checked = warnLow,
            enabled = true,
            onCheckedChange = viewModel::setWarnLowVolume,
        )
    }
}

@Composable
private fun TimerSettings(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    SettingsCard {
        SettingLabel(stringResource(R.string.settings_timer_sound))
        val timerTone by viewModel.timerTone.collectAsStateWithLifecycle()
        Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            ToneField(
                title = timerTone.ringtoneTitle,
                onSystemTonePicked = viewModel::setTimerSystemTone,
                onFilePicked = { uri ->
                    viewModel.importTimerTone(uri) {
                        Toast.makeText(context, R.string.sound_import_failed, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}

@Composable
private fun DateTimeSettings(viewModel: SettingsViewModel) {
    SettingsCard {
        SettingLabel(stringResource(R.string.settings_time_format))
        val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle()
        SegmentedControl(
            options = listOf(
                TimeFormat.SYSTEM to stringResource(R.string.time_format_system),
                TimeFormat.H24 to stringResource(R.string.time_format_24),
                TimeFormat.H12 to stringResource(R.string.time_format_12),
            ),
            selected = timeFormat,
            onSelect = viewModel::setTimeFormat,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
    SettingsCard { ZoneSetting(viewModel) }
}

/** A settings page: top bar and a scrolling column of cards. */
@Composable
private fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ClockTheme.colors.background),
    ) {
        ClockTopBar(
            title = title,
            navigationIcon = { TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
        ) { content() }
    }
}

/** A group of related settings on a solid card, so each group reads as one block. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val colors = ClockTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape),
    ) { content() }
}

/** Between rows of a card; [afterIcon] lines it up with the text of rows that have an icon. */
@Composable
private fun CardDivider(afterIcon: Boolean = true) {
    HorizontalDivider(Modifier.padding(start = if (afterIcon) 64.dp else 20.dp), thickness = 1.dp, color = ClockTheme.colors.outline)
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = ClockTheme.typography.caption,
        color = ClockTheme.colors.contentMuted,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
    )
}

/** Automatic (the phone's zone) or a manual zone for every alarm without its own (docs/PLANO.md 3.6). */
@Composable
private fun ZoneSetting(viewModel: SettingsViewModel) {
    val manualZone by viewModel.manualZone.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    var choosing by rememberSaveable { mutableStateOf(false) }
    val summary = manualZone?.let { stringResource(R.string.settings_zone_manual, ZoneNames.city(it, locale)) }
        ?: stringResource(R.string.settings_zone_automatic, ZoneNames.city(ZoneId.systemDefault(), locale))
    ClickableRow(title = stringResource(R.string.settings_zone), summary = summary, onClick = { choosing = true })
    manualZone?.let { zone ->
        Text(
            text = stringResource(R.string.settings_zone_manual_hint, ZoneNames.city(zone, locale)),
            style = ClockTheme.typography.caption,
            color = ClockTheme.colors.contentMuted,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
    }
    if (choosing) {
        ZonePickerDialog(
            title = stringResource(R.string.settings_zone),
            selected = manualZone,
            noZoneLabel = stringResource(R.string.zone_automatic),
            noZoneSummary = stringResource(R.string.zone_automatic_summary),
            onSelect = { viewModel.setManualZone(it); choosing = false },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = ClockTheme.typography.label,
        color = ClockTheme.colors.contentMuted,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = ClockTheme.typography.body,
        color = ClockTheme.colors.content,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
    )
}

@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    SegmentedControl(
        options = listOf(
            ThemeMode.SYSTEM to stringResource(R.string.theme_system),
            ThemeMode.LIGHT to stringResource(R.string.theme_light),
            ThemeMode.DARK to stringResource(R.string.theme_dark),
        ),
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun AccentSelector(
    selected: AccentPalette?,
    customArgb: Int?,
    onSelect: (AccentPalette) -> Unit,
    onCustom: (Int) -> Unit,
) {
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AccentPalette.entries.forEach { accent ->
            val isSelected = accent == selected
            val label = stringResource(accent.label)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = {
                        if (!isSelected) haptics.tick()
                        onSelect(accent)
                    })
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (isSelected) 40.dp else 32.dp)
                        .border(if (isSelected) 2.dp else 0.dp, colors.content, CircleShape)
                        .padding(if (isSelected) 5.dp else 0.dp)
                        .background(accent.color, CircleShape),
                )
            }
        }
        // Any colour: the current custom one, or a "+" to pick it.
        val customLabel = stringResource(R.string.accent_custom)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .selectable(selected = customArgb != null, role = Role.RadioButton, onClick = { picking = true })
                .semantics { contentDescription = customLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(if (customArgb != null) 40.dp else 32.dp)
                    .border(if (customArgb != null) 2.dp else 1.dp, if (customArgb != null) colors.content else colors.outline, CircleShape)
                    .padding(if (customArgb != null) 5.dp else 0.dp)
                    .background(customArgb?.let(::Color) ?: Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (customArgb == null) Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.content, modifier = Modifier.size(18.dp))
            }
        }
    }
    customArgb?.let { ContrastWarning(Color(it)) }
    if (picking) {
        ColorPickerDialog(
            initial = customArgb ?: colors.accent.toArgb(),
            allowAuto = false,
            showOpacity = false,
            onPick = { argb -> argb?.let(onCustom); picking = false },
            onDismiss = { picking = false },
            footer = { argb -> ContrastWarning(Color(argb), padded = false) },
        )
    }
}

/** Tells when a custom accent would be hard to see on the dark or the light theme. */
@Composable
private fun ContrastWarning(color: Color, padded: Boolean = true) {
    val problems = AccentPalette.contrastProblems(color)
    if (!problems.any) return
    val text = when {
        problems.tooDarkForDark && problems.tooLightForLight -> R.string.accent_low_contrast_both
        problems.tooDarkForDark -> R.string.accent_low_contrast_dark
        else -> R.string.accent_low_contrast_light
    }
    Text(
        stringResource(text),
        style = ClockTheme.typography.caption,
        color = ClockTheme.colors.contentMuted,
        modifier = Modifier.padding(horizontal = if (padded) 20.dp else 0.dp, vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, summary, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        ClockSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ClickableRow(title: String, summary: String, onClick: () -> Unit, @DrawableRes icon: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = ClockTheme.colors.content, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(18.dp))
        }
        RowText(title, summary, Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = ClockTheme.colors.contentMuted,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun RowText(title: String, summary: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = ClockTheme.typography.body, color = ClockTheme.colors.content)
        Spacer(Modifier.height(2.dp))
        Text(summary, style = ClockTheme.typography.caption, color = ClockTheme.colors.contentMuted)
    }
}

@Composable
private fun <T> RadioList(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    Column(Modifier.selectableGroup()) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = {
                        if (!isSelected) haptics.tick()
                        onSelect(value)
                    })
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .border(1.5.dp, if (isSelected) colors.accent else colors.outline, CircleShape)
                        .padding(5.dp)
                        .background(if (isSelected) colors.accent else colors.background, CircleShape),
                )
                Spacer(Modifier.width(16.dp))
                Text(stringResource(label), style = ClockTheme.typography.body, color = colors.content)
            }
        }
    }
}

private val NoticePresets = listOf(5, 10, 15, 30, 60, 120, 1440)

/**
 * Notices before each alarm: any number of them, from presets or a custom length in days, hours
 * and minutes. Custom ones show as their own chips; tapping a chosen chip removes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoticeChips(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val colors = ClockTheme.colors
    val resources = LocalResources.current
    var picking by rememberSaveable { mutableStateOf(false) }
    FlowRow(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (NoticePresets + selected).distinct().sorted().forEach { minutes ->
            ChoiceChip(
                label = formatNotice(resources, minutes),
                selected = minutes in selected,
                onClick = { onToggle(minutes) },
            )
        }
        Box(
            Modifier
                .clip(CapsuleShape)
                .border(1.dp, colors.content.copy(alpha = 0.4f), CapsuleShape)
                .clickable(role = Role.Button) { picking = true }
                .heightIn(min = 40.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.duration_custom), style = ClockTheme.typography.label, color = colors.content)
            }
        }
    }
    Text(
        stringResource(R.string.settings_notices_skipped),
        style = ClockTheme.typography.caption,
        color = colors.contentMuted,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
    )
    if (picking) {
        DurationPickerDialog(
            title = stringResource(R.string.settings_notice_custom_title),
            initialMinutes = 60,
            maxDays = MAX_NOTICE_DAYS,
            onPick = { minutes ->
                if (minutes !in selected) onToggle(minutes)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    Box(
        Modifier
            .clip(CapsuleShape)
            .background(if (selected) colors.accent else colors.background, CapsuleShape)
            .border(1.dp, if (selected) colors.accent else colors.outline, CapsuleShape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = {
                haptics.toggle(it)
                onClick()
            })
            .heightIn(min = 40.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ClockTheme.typography.label, color = if (selected) colors.onAccent else colors.content)
    }
}

private const val MAX_NOTICE_DAYS = 30
