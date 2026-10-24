package io.github.imlemos.clock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.rememberHaptics
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/**
 * Settings > Widgets: each widget with a sample, a button that asks the launcher to add it (its
 * editor opens once it is placed) and the ones already on the home screen, to edit them.
 */
@Composable
fun WidgetSettings() {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    var placed by remember { mutableStateOf(placedWidgets(context, manager)) }
    // Widgets may have been added or removed on the home screen meanwhile.
    LifecycleResumeEffect(Unit) {
        placed = placedWidgets(context, manager)
        onPauseOrDispose { }
    }
    Text(
        stringResource(R.string.widgets_intro),
        style = ClockTheme.typography.caption,
        color = ClockTheme.colors.contentMuted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    WidgetCard(
        kind = WidgetKind.CLOCK,
        name = R.string.widget_clock_name,
        description = R.string.widget_clock_description,
        preview = R.drawable.widget_preview_clock,
        ids = placed[WidgetKind.CLOCK].orEmpty(),
        canAdd = manager.isRequestPinAppWidgetSupported,
    )
    WidgetCard(
        kind = WidgetKind.NEXT_ALARM,
        name = R.string.widget_next_alarm_name,
        description = R.string.widget_next_alarm_description,
        preview = R.drawable.widget_preview_next_alarm,
        ids = placed[WidgetKind.NEXT_ALARM].orEmpty(),
        canAdd = manager.isRequestPinAppWidgetSupported,
    )
}

@Composable
private fun WidgetCard(
    kind: WidgetKind,
    @StringRes name: Int,
    @StringRes description: Int,
    @DrawableRes preview: Int,
    ids: List<Int>,
    canAdd: Boolean,
) {
    val context = LocalContext.current
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .padding(20.dp),
    ) {
        Image(
            painter = painterResource(preview),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 140.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(name), style = ClockTheme.typography.title, color = colors.content)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(description), style = ClockTheme.typography.caption, color = colors.contentMuted)
        Spacer(Modifier.height(16.dp))
        // One under the other, full width: "Adicionar ao ecrã principal" leaves no room beside it.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canAdd) {
                Button(stringResource(R.string.widgets_add), accent = true) {
                    haptics.confirm()
                    requestPin(context, kind)
                }
            }
            if (ids.size == 1) {
                Button(stringResource(R.string.widgets_edit), accent = false) { openEditor(context, ids.first()) }
            }
        }
        if (ids.size > 1) {
            Spacer(Modifier.height(12.dp))
            Text(
                pluralStringResource(R.plurals.widgets_on_home, ids.size, ids.size),
                style = ClockTheme.typography.caption,
                color = colors.contentMuted,
            )
            ids.forEachIndexed { index, id ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { openEditor(context, id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.widgets_edit_numbered, index + 1),
                        style = ClockTheme.typography.body,
                        color = colors.content,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.contentMuted, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Button(text: String, accent: Boolean, onClick: () -> Unit) {
    val colors = ClockTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(CapsuleShape)
            .background(if (accent) colors.accent else colors.content.copy(alpha = 0.08f), CapsuleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ClockTheme.typography.label, color = if (accent) colors.onAccent else colors.content, maxLines = 1)
    }
}

private fun placedWidgets(context: Context, manager: AppWidgetManager): Map<WidgetKind, List<Int>> =
    WidgetKind.entries.associateWith { kind ->
        manager.getAppWidgetIds(ComponentName(context, WidgetUpdater.providerOf(kind))).sorted()
    }

/**
 * Asks the launcher to add the widget. Once placed, the launcher sends the new widget's id to the
 * editor, which opens so it can be styled right away.
 */
private fun requestPin(context: Context, kind: WidgetKind) {
    val manager = AppWidgetManager.getInstance(context)
    val onPlaced = PendingIntent.getActivity(
        context,
        kind.ordinal,
        Intent(context, WidgetConfigActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        // Mutable: the launcher adds the new widget's id to this (explicit) intent.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
    manager.requestPinAppWidget(ComponentName(context, WidgetUpdater.providerOf(kind)), null, onPlaced)
}

private fun openEditor(context: Context, widgetId: Int) {
    context.startActivity(
        Intent(context, WidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
