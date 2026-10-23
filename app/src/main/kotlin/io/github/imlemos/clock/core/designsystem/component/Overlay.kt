package io.github.imlemos.clock.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.imlemos.clock.core.designsystem.glass.GlassBackdrop
import io.github.imlemos.clock.core.designsystem.glass.glassBackdropSource
import io.github.imlemos.clock.core.designsystem.glass.rememberGlassBackdrop

/**
 * Dialogs and sheets drawn inside the activity's own window, above everything else. A separate
 * dialog window cannot sample what is behind it (cross-window blur is missing on many phones), so
 * this is what lets them be real glass.
 */
@Stable
class OverlayHost internal constructor(val backdrop: GlassBackdrop) {
    internal val entries = mutableStateListOf<OverlayEntry>()
}

internal class OverlayEntry(locals: CompositionLocalContext, content: @Composable () -> Unit) {
    var locals by mutableStateOf(locals)
    var content by mutableStateOf(content)
}

val LocalOverlayHost = staticCompositionLocalOf<OverlayHost?> { null }

/** Hosts [Overlay]s for [content]; the screen underneath is what their glass samples. */
@Composable
fun OverlayRoot(content: @Composable () -> Unit) {
    val backdrop = rememberGlassBackdrop()
    val host = remember(backdrop) { OverlayHost(backdrop) }
    CompositionLocalProvider(LocalOverlayHost provides host) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .glassBackdropSource(backdrop)
                    // Like a dialog window: TalkBack only reaches the open overlay.
                    .then(if (host.entries.isEmpty()) Modifier else Modifier.clearAndSetSemantics {}),
            ) { content() }
            host.entries.forEach { entry ->
                key(entry) { CompositionLocalProvider(entry.locals) { entry.content() } }
            }
        }
    }
}

/**
 * Shows [content] above the whole screen while this is in the composition, with the caller's
 * theme and other composition locals. Without an [OverlayRoot] (alarm and timer screens) it falls
 * back to a plain full-screen dialog window.
 */
@Composable
fun Overlay(content: @Composable () -> Unit) {
    val host = LocalOverlayHost.current
    if (host == null) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) { content() }
        return
    }
    val locals = currentCompositionLocalContext
    val entry = remember { OverlayEntry(locals, content) }
    SideEffect {
        entry.locals = locals
        entry.content = content
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}
