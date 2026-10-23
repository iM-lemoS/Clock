package io.github.imlemos.clock

import android.app.UiModeManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.core.data.prefs.ThemeMode
import io.github.imlemos.clock.core.designsystem.glass.rememberSystemReducesEffects
import io.github.imlemos.clock.core.designsystem.glass.resolveGlassLevel
import io.github.imlemos.clock.core.designsystem.theme.AccentPalette
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.navigation.ClockShell

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        keepSplashUntilSettingsLoad()
        handleIntent(intent)

        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val settings = appearance ?: return@setContent
            ApplyNightMode(settings.themeMode)
            ClockTheme(
                darkTheme = isSystemInDarkTheme(),
                accent = AccentPalette.colorFor(settings.accentArgb),
                glassLevel = resolveGlassLevel(
                    reduceTransparency = settings.reduceTransparency,
                    systemReducesEffects = rememberSystemReducesEffects(),
                ),
            ) {
                val showIntro by viewModel.showReliabilityIntro.collectAsStateWithLifecycle()
                ClockShell(showReliabilityIntro = showIntro, onReliabilityIntroShown = viewModel::onReliabilityIntroShown)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** "Tap to undo" on the notification of an alarm the app deleted after its last ring. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ACTION_RESTORE_FROM_TRASH) return
        val trashId = intent.getLongExtra(EXTRA_TRASH_ID, -1)
        if (trashId >= 0) {
            viewModel.restore(trashId) {
                Toast.makeText(this, R.string.alarm_restored, Toast.LENGTH_SHORT).show()
            }
        }
        setIntent(Intent(this, MainActivity::class.java))
    }

    companion object {
        const val ACTION_RESTORE_FROM_TRASH = "io.github.imlemos.clock.action.RESTORE_FROM_TRASH"
        const val EXTRA_TRASH_ID = "trash_id"
    }

    /** Avoids a first frame in the wrong theme: hold the system splash until DataStore answers. */
    private fun keepSplashUntilSettingsLoad() {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = viewModel.appearance.value != null
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
    }
}

/**
 * Applies the theme choice at the app level (Android 12+ API) instead of only inside Compose, so
 * the splash screen, window background, system bars and system dialogs match it too. The system
 * persists the value; calling it again with the same mode is a no-op.
 */
@Composable
private fun ApplyNightMode(mode: ThemeMode) {
    val context = LocalContext.current
    LaunchedEffect(mode) {
        val uiModeManager = context.getSystemService(UiModeManager::class.java)
        uiModeManager.setApplicationNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }
}
