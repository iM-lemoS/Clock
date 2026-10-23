package io.github.imlemos.clock.alarm.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.zones.ZoneNames
import io.github.imlemos.clock.core.zones.ZonePickerDialog
import java.time.ZoneId

/** An alarm's own time zone: "Local time" (follows the app) or a city (docs/PLANO.md 3.6). */
@Composable
fun ZoneField(zone: ZoneId?, manualZone: ZoneId?, onChange: (ZoneId?) -> Unit, showTitle: Boolean = true) {
    val colors = ClockTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    var choosing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (showTitle) {
            Text(
                stringResource(R.string.zone_field).uppercase(),
                style = ClockTheme.typography.label,
                color = colors.contentMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CapsuleShape)
                .border(1.dp, colors.outline, CapsuleShape)
                .clickable(role = Role.Button) { choosing = true }
                .heightIn(min = 52.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                zone?.let { ZoneNames.city(it, locale) } ?: stringResource(R.string.zone_local),
                style = ClockTheme.typography.body,
                color = colors.content,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.contentMuted)
        }
    }
    if (choosing) {
        ZonePickerDialog(
            title = stringResource(R.string.zone_field),
            selected = zone,
            noZoneLabel = stringResource(R.string.zone_local),
            noZoneSummary = manualZone?.let { stringResource(R.string.zone_local_summary_app, ZoneNames.city(it, locale)) }
                ?: stringResource(R.string.zone_local_summary_phone),
            onSelect = { onChange(it); choosing = false },
            onDismiss = { choosing = false },
        )
    }
}
