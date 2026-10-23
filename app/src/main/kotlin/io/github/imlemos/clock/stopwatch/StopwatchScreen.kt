package io.github.imlemos.clock.stopwatch

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.component.EmptyState
import io.github.imlemos.clock.navigation.TabScreen

@Composable
fun StopwatchScreen(contentPadding: PaddingValues, onOpenSettings: () -> Unit) {
    TabScreen(stringResource(R.string.nav_stopwatch), contentPadding, onOpenSettings) {
        EmptyState(
            digits = stringResource(R.string.stopwatch_zero),
            title = stringResource(R.string.stopwatch_empty_title),
            body = null,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
