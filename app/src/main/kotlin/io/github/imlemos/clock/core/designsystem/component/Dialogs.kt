package io.github.imlemos.clock.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/** One row of an [OptionsSheet]. [destructive] actions (delete) are shown in the accent colour. */
data class SheetOption(
    val label: String,
    @param:DrawableRes val icon: Int? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * A menu of actions as a bottom sheet: big rows with icons, dismissed by tapping outside or
 * swiping down. [header] replaces the plain [title], e.g. with a preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsSheet(
    title: String,
    options: List<SheetOption>,
    onDismiss: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val colors = ClockTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = colors.surfaceRaised,
        contentColor = colors.content,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CapsuleShape)
                    .background(colors.outline),
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                header?.invoke() ?: Text(title, style = ClockTheme.typography.title, color = colors.content)
            }
            Spacer(Modifier.height(8.dp))
            options.forEach { option ->
                val tint = if (option.destructive) colors.accent else colors.content
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = option.onClick)
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (option.icon != null) {
                        Icon(painterResource(option.icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(20.dp))
                    }
                    Text(option.label, style = ClockTheme.typography.body, color = tint)
                }
            }
        }
    }
}

/** A list of plain choices; each closes the sheet through its own action. */
@Composable
fun OptionsDialog(title: String, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    OptionsSheet(title = title, options = options.map { (label, action) -> SheetOption(label, onClick = action) }, onDismiss = onDismiss)
}

/** A single-line text prompt, e.g. a group name. */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    maxLength: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ClockTheme.colors
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(maxLength) },
                label = { Text(label) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.content,
                    unfocusedBorderColor = colors.outline,
                    focusedLabelColor = colors.content,
                    unfocusedLabelColor = colors.contentMuted,
                    cursorColor = colors.accent,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.action_save), color = if (text.isNotBlank()) colors.accent else colors.contentMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.content) }
        },
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.content,
    )
}
