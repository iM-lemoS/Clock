package io.github.imlemos.clock

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.alarm.ui.ClockTimeFormat
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ClockApp : Application() {

    @Inject lateinit var alarmController: AlarmController
    @Inject lateinit var settings: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        // Cheap safety net: whenever the process starts, make sure AlarmManager matches the database.
        appScope.launch { alarmController.rescheduleAll() }
        // The 12/24-hour choice is read everywhere times are formatted, including notifications.
        mainScope.launch { settings.timeFormat.collect { ClockTimeFormat.format = it } }
    }
}
