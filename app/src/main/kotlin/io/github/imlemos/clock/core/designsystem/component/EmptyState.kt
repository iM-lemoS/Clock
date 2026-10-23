package io.github.imlemos.clock.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/** Idle dot-matrix digits with a short explanation underneath. */
@Composable
fun EmptyState(
    digits: String,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DotDigits(
            text = digits,
            color = ClockTheme.colors.contentFaint,
            minFontSize = 32.sp,
            // Decorative: screen readers get the title and body instead.
            modifier = Modifier.clearAndSetSemantics {},
        )
        Spacer(Modifier.height(24.dp))
        Text(text = title, style = ClockTheme.typography.title, color = ClockTheme.colors.content, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = body, style = ClockTheme.typography.body, color = ClockTheme.colors.contentMuted, textAlign = TextAlign.Center)
        }
    }
}
