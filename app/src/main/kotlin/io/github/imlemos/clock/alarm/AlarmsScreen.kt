package io.github.imlemos.clock.alarm

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.component.EmptyState
import io.github.imlemos.clock.navigation.TabScreen

@Composable
fun AlarmsScreen(contentPadding: PaddingValues, onOpenSettings: () -> Unit) {
    TabScreen(stringResource(R.string.nav_alarms), contentPadding, onOpenSettings) {
        EmptyState(
            digits = stringResource(R.string.alarms_empty_digits),
            title = stringResource(R.string.alarms_empty_title),
            body = stringResource(R.string.alarms_empty_body),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
