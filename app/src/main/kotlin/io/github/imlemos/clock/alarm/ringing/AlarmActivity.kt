package io.github.imlemos.clock.alarm.ringing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.alarm.layout.LayoutRepository
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.scheduling.AlarmIntents
import io.github.imlemos.clock.core.scheduling.TimeOfDay
import java.time.Instant
import java.time.ZoneId
import io.github.imlemos.clock.core.data.prefs.AppearanceSettings
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.data.prefs.VolumeButtonAction
import io.github.imlemos.clock.core.designsystem.glass.rememberSystemReducesEffects
import io.github.imlemos.clock.core.designsystem.glass.resolveGlassLevel
import io.github.imlemos.clock.core.designsystem.theme.AccentPalette
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full-screen alarm shown over the lock screen (also before the first unlock after a reboot).
 * Dismissing or snoozing is delegated to [RingingService]; the screen closes when ringing ends.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var ringingState: RingingState
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var layouts: LayoutRepository

    private var volumeButtonAction = VolumeButtonAction.OFF

    // "Test alarm" from the layout editor: the real screen, but nothing rings and both actions
    // close it. A real alarm arriving meanwhile (single instance) replaces the preview.
    private var previewLayoutId by mutableLongStateOf(NO_PREVIEW)
    private var previewLabel = ""

    private fun readPreview(intent: Intent) {
        previewLabel = intent.getStringExtra(EXTRA_PREVIEW_LABEL).orEmpty()
        previewLayoutId = intent.getLongExtra(EXTRA_PREVIEW_LAYOUT_ID, NO_PREVIEW)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readPreview(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Back must not silently leave a ringing alarm: the user dismisses or snoozes explicitly.
        // A "Test" preview closes normally.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (previewLayoutId != NO_PREVIEW) finish()
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settings.volumeButtonAction.collect { volumeButtonAction = it }
            }
        }

        readPreview(intent)

        setContent {
            val ringing by ringingState.session.collectAsStateWithLifecycle()
            val appearance by settings.appearance.collectAsStateWithLifecycle(AppearanceSettings())
            val preview by produceState<RingingSession?>(null, previewLayoutId) {
                value = if (previewLayoutId != NO_PREVIEW) previewSession(previewLayoutId, previewLabel) else null
            }
            val session = if (previewLayoutId != NO_PREVIEW) preview else ringing
            LaunchedEffect(ringing, previewLayoutId) { if (previewLayoutId == NO_PREVIEW && ringing == null) finish() }
            val current = session ?: return@setContent
            // The alarm screen is always dark: a white screen at night is harsh.
            ClockTheme(
                darkTheme = true,
                accent = AccentPalette.colorFor(appearance.accentArgb),
                glassLevel = resolveGlassLevel(appearance.reduceTransparency, rememberSystemReducesEffects()),
            ) {
                AlarmRingingScreen(
                    session = current,
                    spec = current.primary.layout,
                    onDismiss = { if (previewLayoutId != NO_PREVIEW) finish() else send(AlarmIntents.ACTION_DISMISS) },
                    onSnooze = { minutes ->
                        if (previewLayoutId != NO_PREVIEW) return@AlarmRingingScreen finish()
                        startService(
                            Intent(this, RingingService::class.java)
                                .setAction(AlarmIntents.ACTION_SNOOZE)
                                .putExtra(AlarmIntents.EXTRA_SNOOZE_MINUTES, minutes ?: 0),
                        )
                    },
                )
            }
        }
    }

    /** Volume down silences this ringing when the user enabled it in settings. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volumeButtonAction != VolumeButtonAction.OFF) {
            startService(
                Intent(this, RingingService::class.java)
                    .setAction(AlarmIntents.ACTION_SILENCE)
                    .putExtra(
                        AlarmIntents.EXTRA_SILENCE_VIBRATION,
                        volumeButtonAction == VolumeButtonAction.SILENCE_SOUND_AND_VIBRATION,
                    ),
            )
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private suspend fun previewSession(layoutId: Long, label: String): RingingSession {
        val now = Instant.now()
        val time = now.atZone(ZoneId.systemDefault())
        return RingingSession(
            listOf(
                RingingAlarm(
                    id = -1,
                    time = TimeOfDay(time.hour, time.minute),
                    label = label,
                    snoozeMinutes = Alarm.DEFAULT_SNOOZE_MINUTES,
                    createdAt = now,
                    layout = layouts.resolve(layoutId),
                ),
            ),
        )
    }

    private fun send(action: String) {
        startService(Intent(this, RingingService::class.java).setAction(action))
    }

    companion object {
        private const val EXTRA_PREVIEW_LAYOUT_ID = "preview_layout_id"
        private const val EXTRA_PREVIEW_LABEL = "preview_label"
        private const val NO_PREVIEW = Long.MIN_VALUE

        /** Opens the alarm screen with [layoutId] as a silent preview. */
        fun previewIntent(context: Context, layoutId: Long, label: String): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_PREVIEW_LAYOUT_ID, layoutId)
                .putExtra(EXTRA_PREVIEW_LABEL, label)
    }
}
