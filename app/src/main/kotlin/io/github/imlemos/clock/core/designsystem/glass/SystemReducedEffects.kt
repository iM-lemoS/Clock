package io.github.imlemos.clock.core.designsystem.glass

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.function.Consumer

/**
 * Android has no "reduce transparency" accessibility setting. The closest system signal is
 * cross-window blur being disabled, which happens in battery saver, when the GPU or the OEM does
 * not support it, or when "Allow window-level blurs" is off in developer options.
 */
@Composable
fun rememberSystemReducesEffects(): Boolean {
    val context = LocalContext.current
    val windowManager = remember(context) { context.getSystemService(WindowManager::class.java) }
    var reduced by remember { mutableStateOf(!windowManager.isCrossWindowBlurEnabled) }
    DisposableEffect(windowManager) {
        // The listener is invoked immediately with the current value, then on every change.
        val listener = Consumer<Boolean> { enabled -> reduced = !enabled }
        windowManager.addCrossWindowBlurEnabledListener(ContextCompat.getMainExecutor(context), listener)
        onDispose { windowManager.removeCrossWindowBlurEnabledListener(listener) }
    }
    return reduced
}
