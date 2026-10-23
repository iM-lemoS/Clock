package io.github.imlemos.clock.reliability

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.imlemos.clock.core.notifications.NotificationChannels
import javax.inject.Inject

enum class ReliabilityCheck {
    /** Notifications allowed, and the ringing channel not turned off. */
    Notifications,

    /** Full-screen notifications (Android 14+ may revoke them). */
    FullScreen,

    /** Exact alarms (only revocable on Android 12; USE_EXACT_ALARM covers 13+). */
    ExactAlarms,

    /** Not subject to battery optimisation. */
    Battery,
}

/** What the Android APIs let us verify. Vendor-specific settings cannot be read (see core/oem). */
class ReliabilityChecker @Inject constructor(@ApplicationContext private val context: Context) {

    fun status(): Map<ReliabilityCheck, Boolean> = ReliabilityCheck.entries.associateWith(::isOk)

    fun allOk(): Boolean = status().values.all { it }

    private fun isOk(check: ReliabilityCheck): Boolean = when (check) {
        ReliabilityCheck.Notifications -> {
            val compat = NotificationManagerCompat.from(context)
            compat.areNotificationsEnabled() &&
                compat.getNotificationChannel(NotificationChannels.ALARM_RINGING)?.importance != NotificationManager.IMPORTANCE_NONE
        }
        ReliabilityCheck.FullScreen ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ReliabilityCheck.ExactAlarms -> context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        ReliabilityCheck.Battery ->
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    /** The system screen that fixes [check]. */
    fun fixIntent(check: ReliabilityCheck): Intent {
        val pkg = "package:${context.packageName}".toUri()
        return when (check) {
            ReliabilityCheck.Notifications ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            ReliabilityCheck.FullScreen ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
                } else {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            ReliabilityCheck.ExactAlarms -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            // Play policy does not list alarm clocks for the direct exemption request (setAlarmClock
            // already fires in Doze), so open the system list and let the user pick the app.
            ReliabilityCheck.Battery -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }
}
