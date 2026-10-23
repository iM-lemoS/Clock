package io.github.imlemos.clock.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.DotGrid
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton

/** Common frame of the three tabs: title, settings action and a dot-matrix backdrop. */
@Composable
fun TabScreen(
    title: String,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        DotGrid(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            ClockTopBar(
                title = title,
                actions = {
                    TopBarIconButton(
                        icon = R.drawable.ic_settings,
                        contentDescription = stringResource(R.string.action_settings),
                        onClick = onOpenSettings,
                    )
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(contentPadding),
                content = content,
            )
        }
    }
}
