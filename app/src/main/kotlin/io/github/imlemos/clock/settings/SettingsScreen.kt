package io.github.imlemos.clock.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.data.prefs.ThemeMode
import io.github.imlemos.clock.core.data.prefs.VolumeButtonAction
import io.github.imlemos.clock.core.designsystem.component.ClockSwitch
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.SegmentedControl
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.glass.rememberSystemReducesEffects
import io.github.imlemos.clock.core.designsystem.theme.AccentPalette
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/**
 * Phase 0 settings: only what is needed to exercise the design system. The full list from
 * docs/PLANO.md section 6 arrives in phase 8.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenReliability: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenGlassLab: (() -> Unit)?,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val systemReducesEffects = rememberSystemReducesEffects()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(ClockTheme.colors.background),
    ) {
        ClockTopBar(
            title = stringResource(R.string.settings_title),
            navigationIcon = {
                TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_section_appearance))

            SettingLabel(stringResource(R.string.settings_theme))
            ThemeSelector(selected = appearance.themeMode, onSelect = viewModel::setThemeMode)

            SettingLabel(stringResource(R.string.settings_accent))
            AccentSelector(
                selected = AccentPalette.matching(appearance.accentArgb),
                onSelect = { accent ->
                    viewModel.setAccent(if (accent == AccentPalette.Default) null else accent.color.toArgb())
                },
            )

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

            SectionHeader(stringResource(R.string.settings_section_alarms))
            SettingLabel(stringResource(R.string.settings_volume_button))
            Text(
                text = stringResource(R.string.settings_volume_button_summary),
                style = ClockTheme.typography.caption,
                color = ClockTheme.colors.contentMuted,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
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

            SettingLabel(stringResource(R.string.settings_notices))
            Text(
                text = stringResource(R.string.settings_notices_summary),
                style = ClockTheme.typography.caption,
                color = ClockTheme.colors.contentMuted,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
            )
            val notices by viewModel.noticeMinutes.collectAsStateWithLifecycle()
            NoticeChips(selected = notices, onToggle = viewModel::toggleNotice)
            ClickableRow(
                title = stringResource(R.string.settings_trash),
                summary = stringResource(R.string.settings_trash_summary),
                onClick = onOpenTrash,
            )

            SectionHeader(stringResource(R.string.settings_section_general))
            ClickableRow(
                title = stringResource(R.string.settings_reliability),
                summary = stringResource(R.string.settings_reliability_summary),
                onClick = onOpenReliability,
            )
            // Per-app language settings exist from Android 13; on Android 12 the app follows the system.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClickableRow(
                    title = stringResource(R.string.settings_language),
                    summary = stringResource(R.string.settings_language_summary),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri()),
                        )
                    },
                )
            }

            if (onOpenGlassLab != null) {
                SectionHeader(stringResource(R.string.settings_section_developer))
                ClickableRow(
                    title = stringResource(R.string.settings_glass_lab),
                    summary = stringResource(R.string.settings_glass_lab_summary),
                    onClick = onOpenGlassLab,
                )
                val testLabel = stringResource(R.string.test_alarm_label)
                ClickableRow(
                    title = stringResource(R.string.settings_test_alarm),
                    summary = stringResource(R.string.settings_test_alarm_summary),
                    onClick = { viewModel.createTestAlarm(testLabel) },
                )
            }
        }
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
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 10.dp),
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
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

@Composable
private fun AccentSelector(selected: AccentPalette?, onSelect: (AccentPalette) -> Unit) {
    val colors = ClockTheme.colors
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
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
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(accent) })
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
    }
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
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, summary, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        ClockSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ClickableRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    Column(Modifier.selectableGroup()) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = 24.dp, vertical = 12.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoticeChips(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val colors = ClockTheme.colors
    FlowRow(
        modifier = Modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NoticePresets.forEach { minutes ->
            val isSelected = minutes in selected
            val label = when {
                minutes >= 1440 -> pluralStringResource(R.plurals.notice_days, minutes / 1440, minutes / 1440)
                minutes >= 60 -> stringResource(R.string.duration_hours, minutes / 60)
                else -> stringResource(R.string.duration_minutes, minutes)
            }
            Box(
                Modifier
                    .clip(CapsuleShape)
                    .background(if (isSelected) colors.accent else colors.background, CapsuleShape)
                    .border(1.dp, if (isSelected) colors.accent else colors.outline, CapsuleShape)
                    .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = { onToggle(minutes) })
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = ClockTheme.typography.label, color = if (isSelected) colors.onAccent else colors.content)
            }
        }
    }
}
