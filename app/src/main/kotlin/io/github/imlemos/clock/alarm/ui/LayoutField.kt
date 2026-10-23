package io.github.imlemos.clock.alarm.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.layout.AlarmLayout
import io.github.imlemos.clock.alarm.layout.LayoutThumbnail
import io.github.imlemos.clock.alarm.layout.layoutName
import io.github.imlemos.clock.core.designsystem.component.OptionsDialog
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/**
 * An alarm's screen layout (docs/PLANO.md 3.7): the default, one from the library, or a new one.
 *
 * @param onNew `null` hides "New layout" (batch edit).
 */
@Composable
fun LayoutField(
    layoutId: Long?,
    layouts: List<AlarmLayout>,
    defaultLayoutId: Long,
    onSelect: (Long?) -> Unit,
    onNew: (() -> Unit)?,
    showTitle: Boolean = true,
) {
    val colors = ClockTheme.colors
    var choosing by rememberSaveable { mutableStateOf(false) }
    val defaultLayout = layouts.firstOrNull { it.id == defaultLayoutId }
    val defaultName = defaultLayout?.let { layoutName(it) }.orEmpty()
    val chosen = layouts.firstOrNull { it.id == layoutId }
    val shown = chosen ?: defaultLayout
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth()) {
        if (showTitle) Label(stringResource(R.string.layout_field))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, colors.outline, shape)
                .clickable(role = Role.Button) { choosing = true }
                .heightIn(min = 52.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shown?.let { LayoutThumbnail(it.spec) }
            Spacer(Modifier.width(14.dp))
            Text(
                chosen?.let { layoutName(it) } ?: stringResource(R.string.layout_field_default, defaultName),
                style = ClockTheme.typography.body,
                color = colors.content,
                modifier = Modifier.weight(1f),
            )
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.contentMuted)
        }
    }
    if (choosing) {
        OptionsDialog(
            title = stringResource(R.string.layout_field),
            options = buildList {
                add(stringResource(R.string.layout_field_default, defaultName) to { onSelect(null); choosing = false })
                layouts.forEach { layout -> add(layoutName(layout) to { onSelect(layout.id); choosing = false }) }
                if (onNew != null) add(stringResource(R.string.layout_new) to { choosing = false; onNew() })
            },
            onDismiss = { choosing = false },
        )
    }
}
