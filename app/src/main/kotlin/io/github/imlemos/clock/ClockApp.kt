package io.github.imlemos.clock

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.core.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ClockApp : Application() {

    @Inject lateinit var alarmController: AlarmController

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        // Cheap safety net: whenever the process starts, make sure AlarmManager matches the database.
        appScope.launch { alarmController.rescheduleAll() }
    }
}
