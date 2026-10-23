package io.github.imlemos.clock.timer.ringing

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.core.data.prefs.AppearanceSettings
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.designsystem.glass.rememberSystemReducesEffects
import io.github.imlemos.clock.core.designsystem.glass.resolveGlassLevel
import io.github.imlemos.clock.core.designsystem.theme.AccentPalette
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import javax.inject.Inject

/** Full screen for finished timers, over the lock screen; closes when they stop ringing. */
@AndroidEntryPoint
class TimerRingingActivity : ComponentActivity() {

    @Inject lateinit var state: TimerRingingState
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Like the alarm screen, Back does not silently leave a ringing timer.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        setContent {
            val timers by state.timers.collectAsStateWithLifecycle()
            val appearance by settings.appearance.collectAsStateWithLifecycle(AppearanceSettings())
            LaunchedEffect(timers) { if (timers.isEmpty()) finish() }
            if (timers.isEmpty()) return@setContent
            ClockTheme(
                darkTheme = true,
                accent = AccentPalette.colorFor(appearance.accentArgb),
                glassLevel = resolveGlassLevel(appearance.reduceTransparency, rememberSystemReducesEffects()),
            ) {
                TimerRingingScreen(
                    timers = timers,
                    onStop = { send(TimerRingingService.ACTION_STOP) },
                    onAddMinute = { send(TimerRingingService.ACTION_ADD_MINUTE) },
                )
            }
        }
    }

    private fun send(action: String) {
        startService(Intent(this, TimerRingingService::class.java).setAction(action))
    }
}
