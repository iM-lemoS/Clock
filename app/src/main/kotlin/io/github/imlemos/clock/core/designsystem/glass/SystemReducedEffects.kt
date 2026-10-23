package io.github.imlemos.clock.core.designsystem.glass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Whether the system asks for fewer effects. Android has no "reduce transparency" setting, so the
 * signal is battery saver.
 *
 * Cross-window blur (`WindowManager.isCrossWindowBlurEnabled`) is deliberately not used: Samsung
 * reports it as always off because One UI draws its own blur, which made glass permanently solid
 * there, while our glass is drawn inside the app window and does not depend on it.
 */
@Composable
fun rememberSystemReducesEffects(): Boolean {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var reduced by remember { mutableStateOf(powerManager.isPowerSaveMode) }
    DisposableEffect(powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                reduced = powerManager.isPowerSaveMode
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        reduced = powerManager.isPowerSaveMode
        onDispose { context.unregisterReceiver(receiver) }
    }
    return reduced
}
