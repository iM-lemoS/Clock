package io.github.imlemos.clock.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.designsystem.component.OverlayRoot
import io.github.imlemos.clock.core.designsystem.glass.rememberSystemReducesEffects
import io.github.imlemos.clock.core.designsystem.glass.resolveGlassLevel
import io.github.imlemos.clock.core.designsystem.theme.AccentPalette
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import javax.inject.Inject

/**
 * Opened by the launcher when a widget is added (and from the widget's own settings later). Until
 * the user saves, the result is "cancelled", so a widget being added is not placed.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    private val viewModel: WidgetConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = viewModel.widgetId
        setResult(Activity.RESULT_CANCELED, result(widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue = null)
            val current = appearance ?: return@setContent
            ClockTheme(
                darkTheme = isSystemInDarkTheme(),
                accent = AccentPalette.colorFor(current.accentArgb),
                glassLevel = resolveGlassLevel(
                    reduceTransparency = current.reduceTransparency,
                    systemReducesEffects = rememberSystemReducesEffects(),
                ),
            ) {
                OverlayRoot {
                    WidgetConfigScreen(
                        viewModel = viewModel,
                        onClose = ::finish,
                        onSaved = {
                            setResult(Activity.RESULT_OK, result(widgetId))
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun result(widgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
