package io.github.imlemos.clock.core.zones

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.ui.formatTimeOfDay
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * Full-screen time zone search (docs/PLANO.md 3.6). The first row is the "no zone of its own"
 * choice ([noZoneLabel]: "Automatic" in settings, "Local time" for an alarm).
 */
@Composable
fun ZonePickerDialog(
    title: String,
    selected: ZoneId?,
    noZoneLabel: String,
    noZoneSummary: String,
    onSelect: (ZoneId?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ClockTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val zones by produceState(emptyList<ZoneOption>(), locale) {
        value = withContext(Dispatchers.Default) { ZoneNames.all(locale) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val now = remember { Instant.now() }
    val shown = remember(zones, query) { if (query.isBlank()) zones else zones.filter { it.matches(query) } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .imePadding(),
        ) {
            ClockTopBar(
                title = title,
                navigationIcon = { TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onDismiss) },
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.zone_search)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                textStyle = ClockTheme.typography.body,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.content,
                    unfocusedBorderColor = colors.outline,
                    cursorColor = colors.accent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                if (query.isBlank()) {
                    item(key = "none") {
                        ZoneRow(
                            title = noZoneLabel,
                            summary = noZoneSummary,
                            trailing = null,
                            selected = selected == null,
                            onClick = { onSelect(null) },
                        )
                    }
                }
                items(shown, key = { it.zone.id }) { option ->
                    ZoneRow(
                        title = option.city,
                        summary = listOf(option.country, ZoneNames.offset(option.zone, now, locale))
                            .filter(String::isNotBlank)
                            .joinToString(" · "),
                        trailing = now.atZone(option.zone).let { TimeOfDay(it.hour, it.minute) },
                        selected = option.zone == selected,
                        onClick = { onSelect(option.zone) },
                    )
                }
                item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
            }
        }
    }
}

@Composable
private fun ZoneRow(title: String, summary: String, trailing: TimeOfDay?, selected: Boolean, onClick: () -> Unit) {
    val colors = ClockTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = ClockTheme.typography.body, color = if (selected) colors.accent else colors.content)
            Text(summary, style = ClockTheme.typography.caption, color = colors.contentMuted)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(formatTimeOfDay(LocalContext.current, trailing), style = ClockTheme.typography.body, color = colors.contentMuted)
        }
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
    }
}
