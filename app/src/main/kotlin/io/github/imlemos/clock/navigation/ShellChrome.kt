package io.github.imlemos.clock.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets a tab replace the floating navigation with its own glass bar, e.g. the multi-select action
 * bar (docs/PLANO.md 7). Glass must be drawn by the shell, outside the backdrop it samples.
 */
@Stable
class ShellChrome {
    var actionBar: (@Composable () -> Unit)? by mutableStateOf(null)
}

val LocalShellChrome = staticCompositionLocalOf { ShellChrome() }
