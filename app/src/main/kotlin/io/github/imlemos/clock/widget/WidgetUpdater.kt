package io.github.imlemos.clock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.imlemos.clock.MainActivity
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.designsystem.theme.AccentPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Draws and delivers every widget, and keeps them current: once a minute while the screen is on
 * (a non-wakeup alarm, so nothing runs while it is off), and whenever alarms change.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: WidgetConfigStore,
    private val contents: WidgetContentFactory,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val manager = AppWidgetManager.getInstance(context)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    val renderer = WidgetRenderer(context, store::photoFile)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var pendingAlarmUpdate: Job? = null

    fun ids(kind: WidgetKind): IntArray = manager.getAppWidgetIds(ComponentName(context, providerOf(kind)))

    fun kindOf(widgetId: Int): WidgetKind? = when (manager.getAppWidgetInfo(widgetId)?.provider?.className) {
        ClockWidgetProvider::class.java.name -> WidgetKind.CLOCK
        NextAlarmWidgetProvider::class.java.name -> WidgetKind.NEXT_ALARM
        else -> null
    }

    /** Redraws every widget and schedules the next minute. */
    suspend fun updateAll() {
        WidgetKind.entries.forEach { update(it, ids(it)) }
        scheduleTick()
    }

    /** Fire and forget [updateAll], e.g. when leaving the app after changing its settings. */
    fun requestUpdateAll() {
        scope.launch { updateAll() }
    }

    suspend fun update(kind: WidgetKind, widgetIds: IntArray) = mutex.withLock {
        // Before the first unlock the widgets' settings cannot be read, and launchers show no
        // widgets anyway; they are drawn once the phone is unlocked.
        if (widgetIds.isEmpty() || !userManager.isUserUnlocked) return@withLock
        runCatching {
            val accent = accent()
            val is24Hour = contents.is24Hour()
            widgetIds.forEach { id ->
                runCatching { manager.updateAppWidget(id, build(kind, id, store.get(id), accent, is24Hour)) }
                    .onFailure { Log.w(TAG, "Could not update widget $id", it) }
            }
        }.onFailure { Log.w(TAG, "Could not update $kind widgets", it) }
    }

    /** The alarms changed: refresh the next-alarm widgets once the burst of changes is over. */
    fun alarmsChanged() {
        if (ids(WidgetKind.NEXT_ALARM).isEmpty()) return
        pendingAlarmUpdate?.cancel()
        pendingAlarmUpdate = scope.launch {
            delay(ALARM_CHANGE_DELAY_MS)
            update(WidgetKind.NEXT_ALARM, ids(WidgetKind.NEXT_ALARM))
        }
    }

    /**
     * Wakes this app at the start of the next minute, but only if the phone is awake then: a
     * non-wakeup alarm waits while the screen is off and fires as soon as it turns on. Apps with
     * a widget on the home screen stay in the "active" standby bucket, so it is not deferred.
     */
    fun scheduleTick() {
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(context, WidgetTickReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (WidgetKind.entries.all { ids(it).isEmpty() }) {
            alarmManager.cancel(intent)
            return
        }
        val next = (clock.millis() / MINUTE_MS + 1) * MINUTE_MS
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExact(AlarmManager.RTC, next, intent)
        } else {
            alarmManager.setWindow(AlarmManager.RTC, next, TICK_WINDOW_MS, intent)
        }
    }

    /** A preview for the configuration screen, drawn exactly like the widget. */
    suspend fun preview(kind: WidgetKind, config: WidgetConfig, widthDp: Int, heightDp: Int, night: Boolean): WidgetImage {
        val density = context.resources.displayMetrics.density
        val content = content(kind, config, contents.is24Hour()).first
        return renderer.render(config, content, (widthDp * density).toInt(), (heightDp * density).toInt(), density, night, accent())
    }

    /** The widget's size in dp: portrait width and height, as launchers report them. */
    fun sizeDp(widgetId: Int, kind: WidgetKind): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(widgetId)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return if (width > 0 && height > 0) width to height else defaultSizeDp(kind)
    }

    private suspend fun build(kind: WidgetKind, id: Int, config: WidgetConfig, accent: Int, is24Hour: Boolean): RemoteViews {
        val (widthDp, heightDp) = sizeDp(id, kind)
        // Keep each image within MAX_PIXELS: two of them travel with every update.
        var density = context.resources.displayMetrics.density
        val pixels = widthDp * heightDp * density * density
        if (pixels > MAX_PIXELS) density *= sqrt(MAX_PIXELS / pixels)
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        val (content, next) = content(kind, config, is24Hour)
        val light = renderer.render(config, content, widthPx, heightPx, density, night = false, accent = accent)
        val dark = if (config.themed) renderer.render(config, content, widthPx, heightPx, density, night = true, accent = accent) else light

        return RemoteViews(context.packageName, R.layout.widget_image).apply {
            setImageViewBitmap(R.id.widget_light, light.bitmap)
            setImageViewBitmap(R.id.widget_dark, dark.bitmap)
            setContentDescription(R.id.widget_root, describe(content))
            val tab = if (kind == WidgetKind.NEXT_ALARM) MainActivity.TAB_ALARMS else null
            setOnClickPendingIntent(R.id.widget_root, openApp(tab))
            if (next?.alarmId != null && content.action != null && light.actionHeightPx > 0) {
                setViewVisibility(R.id.widget_action, View.VISIBLE)
                setViewLayoutHeight(R.id.widget_action, light.actionHeightPx / density, TypedValue.COMPLEX_UNIT_DIP)
                setContentDescription(R.id.widget_action, content.action)
                setOnClickPendingIntent(R.id.widget_action, WidgetActionReceiver.skipIntent(context, next.alarmId, skip = !next.skipped))
            } else {
                setViewVisibility(R.id.widget_action, View.GONE)
            }
        }
    }

    private suspend fun content(kind: WidgetKind, config: WidgetConfig, is24Hour: Boolean): Pair<WidgetContent, NextAlarmContent?> =
        when (kind) {
            WidgetKind.CLOCK -> contents.clock(config, contents.now(), is24Hour) to null
            WidgetKind.NEXT_ALARM -> contents.nextAlarm(config, is24Hour).let { it.content to it }
        }

    private fun describe(content: WidgetContent): String =
        listOfNotNull(content.warning, "${content.hours}:${content.minutes} ${content.dayPeriod.orEmpty()}".trim())
            .plus(content.lines)
            .joinToString(", ")

    private fun openApp(tab: String?): PendingIntent =
        if (tab != null) {
            MainActivity.openTabIntent(context, tab)
        } else {
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

    private suspend fun accent(): Int = settings.appearance.first().accentArgb ?: AccentPalette.Default.color.toArgb()

    companion object {
        private const val TAG = "WidgetUpdater"
        private const val MINUTE_MS = 60_000L
        private const val TICK_WINDOW_MS = 10_000L
        private const val ALARM_CHANGE_DELAY_MS = 300L
        private const val MAX_PIXELS = 1_200_000f

        fun providerOf(kind: WidgetKind): Class<*> = when (kind) {
            WidgetKind.CLOCK -> ClockWidgetProvider::class.java
            WidgetKind.NEXT_ALARM -> NextAlarmWidgetProvider::class.java
        }

        /** Before the launcher reports a size: the widgets' default cells, roughly. */
        fun defaultSizeDp(kind: WidgetKind): Pair<Int, Int> = when (kind) {
            WidgetKind.CLOCK -> 320 to 160
            WidgetKind.NEXT_ALARM -> 240 to 160
        }
    }
}
