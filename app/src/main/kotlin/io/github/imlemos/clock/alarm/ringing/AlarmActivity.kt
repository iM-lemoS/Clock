package io.github.imlemos.clock.alarm.ringing

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
import io.github.imlemos.clock.alarm.scheduling.AlarmIntents
import io.github.imlemos.clock.core.data.prefs.AppearanceSettings
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.designsystem.glass.rememberSystemReducesEffects
import io.github.imlemos.clock.core.designsystem.glass.resolveGlassLevel
import io.github.imlemos.clock.core.designsystem.theme.AccentPalette
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import javax.inject.Inject

/**
 * Full-screen alarm shown over the lock screen (also before the first unlock after a reboot).
 * Dismissing or snoozing is delegated to [RingingService]; the screen closes when ringing ends.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var ringingState: RingingState
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Back must not silently leave a ringing alarm: the user dismisses or snoozes explicitly.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            val session by ringingState.session.collectAsStateWithLifecycle()
            val appearance by settings.appearance.collectAsStateWithLifecycle(AppearanceSettings())
            LaunchedEffect(session) { if (session == null) finish() }
            val current = session ?: return@setContent
            // The alarm screen is always dark: a white screen at night is harsh.
            ClockTheme(
                darkTheme = true,
                accent = AccentPalette.colorFor(appearance.accentArgb),
                glassLevel = resolveGlassLevel(appearance.reduceTransparency, rememberSystemReducesEffects()),
            ) {
                AlarmRingingScreen(
                    session = current,
                    onDismiss = { send(AlarmIntents.ACTION_DISMISS) },
                    onSnooze = { send(AlarmIntents.ACTION_SNOOZE) },
                )
            }
        }
    }

    private fun send(action: String) {
        startService(Intent(this, RingingService::class.java).setAction(action))
    }
}
