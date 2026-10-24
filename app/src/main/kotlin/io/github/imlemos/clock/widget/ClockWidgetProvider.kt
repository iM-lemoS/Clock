package io.github.imlemos.clock.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared by both widgets: draws them when the launcher asks (added, resized, after a reboot or an
 * update) and forgets their look when they are removed.
 */
abstract class ClockAppWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {

    @Inject lateinit var updater: WidgetUpdater
    @Inject lateinit var store: WidgetConfigStore

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = work {
        updater.update(kind, appWidgetIds)
        // Also the periodic update: restarts the minute ticks if anything stopped them.
        updater.scheduleTick()
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        work { updater.update(kind, intArrayOf(appWidgetId)) }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = work { store.remove(appWidgetIds) }

    override fun onEnabled(context: Context) = work { updater.scheduleTick() }

    override fun onDisabled(context: Context) = work { updater.scheduleTick() }

    private fun work(block: suspend () -> Unit) {
        val pending = goAsync()
        scope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

/** The current time and date (docs/PLANO.md 5.2). */
@AndroidEntryPoint
class ClockWidgetProvider : ClockAppWidgetProvider(WidgetKind.CLOCK) {
    // Hilt injects in the annotated class's onReceive.
    override fun onReceive(context: Context, intent: Intent) = super.onReceive(context, intent)
}
