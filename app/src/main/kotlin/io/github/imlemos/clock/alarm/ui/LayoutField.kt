package io.github.imlemos.clock.alarm.ui

import io.github.imlemos.clock.core.designsystem.rememberHaptics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.layout.AlarmLayout
import io.github.imlemos.clock.alarm.layout.LayoutThumbnail
import io.github.imlemos.clock.alarm.layout.layoutName
import io.github.imlemos.clock.alarm.ringing.AlarmActivity
import io.github.imlemos.clock.core.designsystem.component.GlassSheet
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/**
 * An alarm's screen layout (docs/PLANO.md 3.7): the default, one from the library, or a new one.
 * The choice is a grid of live previews; "View" opens one full screen (silent).
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
        LayoutPickerSheet(
            selectedId = chosen?.id,
            layouts = layouts,
            defaultLayout = defaultLayout,
            defaultName = defaultName,
            onSelect = { onSelect(it); choosing = false },
            onNew = onNew?.let { new -> { choosing = false; new() } },
            onDismiss = { choosing = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayoutPickerSheet(
    selectedId: Long?,
    layouts: List<AlarmLayout>,
    defaultLayout: AlarmLayout?,
    defaultName: String,
    onSelect: (Long?) -> Unit,
    onNew: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    GlassSheet(title = stringResource(R.string.layout_field), onDismiss = onDismiss) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val columns = if (maxWidth >= 480.dp) 4 else 3
            val gap = 10.dp
            // A little slack so rounding never pushes the last card to the next row.
            val cardWidth = (maxWidth - gap * (columns - 1)) / columns - 1.dp
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                maxItemsInEachRow = columns,
            ) {
                defaultLayout?.let {
                    LayoutCard(
                        layout = it,
                        name = stringResource(R.string.layout_field_default, defaultName),
                        selected = selectedId == null,
                        width = cardWidth,
                        onClick = { onSelect(null) },
                    )
                }
                layouts.forEach { layout ->
                    LayoutCard(
                        layout = layout,
                        name = layoutName(layout),
                        selected = layout.id == selectedId,
                        width = cardWidth,
                        onClick = { onSelect(layout.id) },
                    )
                }
                if (onNew != null) NewLayoutCard(width = cardWidth, onClick = onNew)
            }
        }
    }
}

@Composable
private fun LayoutCard(layout: AlarmLayout, name: String, selected: Boolean, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val colors = ClockTheme.colors
    val context = LocalContext.current
    val sampleLabel = stringResource(R.string.layout_sample_label)
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .width(width)
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.16f) else colors.content.copy(alpha = 0.05f), shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.content.copy(alpha = 0.16f), shape)
            .selectable(selected = selected, role = Role.RadioButton) {
                haptics.tick()
                onClick()
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            LayoutThumbnail(layout.spec, width = width - 16.dp)
            if (selected) {
                Box(
                    Modifier.padding(6.dp).size(22.dp).clip(CircleShape).background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            name,
            style = ClockTheme.typography.caption,
            color = if (selected) colors.content else colors.contentMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp).heightIn(min = 32.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CapsuleShape)
                .border(1.dp, colors.content.copy(alpha = 0.28f), CapsuleShape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.layout_view)) {
                    context.startActivity(AlarmActivity.previewIntent(context, layout.spec, sampleLabel))
                }
                .heightIn(min = 36.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_visibility), contentDescription = null, tint = colors.content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.layout_view), style = ClockTheme.typography.caption, color = colors.content)
        }
    }
}

@Composable
private fun NewLayoutCard(width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val colors = ClockTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .width(width)
            .clip(shape)
            .border(1.dp, colors.content.copy(alpha = 0.28f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp)
            .height((width - 16.dp) * 2.1f + 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.layout_new), style = ClockTheme.typography.caption, color = colors.content, textAlign = TextAlign.Center)
    }
}
