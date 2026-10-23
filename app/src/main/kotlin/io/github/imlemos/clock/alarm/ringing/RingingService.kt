package io.github.imlemos.clock.alarm.ringing

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.data.AlarmController
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.scheduling.AlarmIntents
import io.github.imlemos.clock.alarm.ui.formatTimeOfDay
import io.github.imlemos.clock.core.audio.AlarmSoundPlayer
import io.github.imlemos.clock.core.notifications.AlarmNotifications
import io.github.imlemos.clock.core.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Foreground service that rings: plays sound and vibration on the alarm stream and shows the
 * full-screen notification that opens [AlarmActivity] over the lock screen.
 *
 * Type `systemExempted`, allowed for apps holding USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM; falls
 * back to `mediaPlayback` if the system refuses it. Android 12-13 have no service types.
 */
@AndroidEntryPoint
class RingingService : Service() {

    @Inject lateinit var controller: AlarmController
    @Inject lateinit var player: AlarmSoundPlayer
    @Inject lateinit var ringingState: RingingState
    @Inject lateinit var notifications: AlarmNotifications

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timeoutJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AlarmIntents.ACTION_FIRE -> {
                // Go foreground before any I/O: the system allows only a few seconds. Only once per
                // session: re-posting the full-screen notification for every colliding alarm within
                // milliseconds can make the system drop the full-screen launch.
                if (!inForeground) {
                    startInForeground(buildNotification(title = getString(R.string.alarm_notification_title), text = null))
                    inForeground = true
                }
                acquireWakeLock()
                val id = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1)
                val occurrence = Instant.ofEpochMilli(intent.getLongExtra(AlarmIntents.EXTRA_OCCURRENCE, 0))
                val snooze = intent.getBooleanExtra(AlarmIntents.EXTRA_SNOOZE, false)
                scope.launch { onFire(id, occurrence, snooze) }
            }
            AlarmIntents.ACTION_DISMISS -> scope.launch { finishSession { controller.dismiss(it) } }
            AlarmIntents.ACTION_SNOOZE -> scope.launch { finishSession { controller.snooze(it) } }
            AlarmIntents.ACTION_SILENCE -> if (ringingState.session.value != null) {
                player.silence(alsoVibration = intent.getBooleanExtra(AlarmIntents.EXTRA_SILENCE_VIBRATION, false))
                ringingState.markSilenced()
                Log.i(TAG, "Ringing silenced with the volume button")
            }
            else -> if (ringingState.session.value == null) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private suspend fun onFire(id: Long, occurrence: Instant, snooze: Boolean) {
        val alarm = controller.onFired(id, occurrence, snooze)
        if (alarm == null) {
            Log.i(TAG, "Alarm $id fired but is gone or disabled; staying silent")
            if (ringingState.session.value == null) stopService()
            return
        }
        val previousPrimary = ringingState.session.value?.primary
        ringingState.add(RingingAlarm(alarm.id, alarm.time, alarm.label, alarm.snoozeMinutes, alarm.createdAt))
        // In a collision the alarm created first leads (docs/PLANO.md 3.11), even if another
        // fired a few milliseconds earlier: switch to its sound unless the user silenced it.
        val session = ringingState.session.value
        if (session?.primary?.id == alarm.id && !session.silenced) {
            player.start(alarm.ringtoneUri?.toUri(), alarm.vibrate)
        }
        if (previousPrimary == null) {
            timeoutJob = scope.launch {
                delay(alarm.ringDurationSeconds * 1_000L)
                // Clear first: finishSession cancels timeoutJob, which must not cancel this coroutine.
                timeoutJob = null
                onTimeout()
            }
        }
        updateNotification()
        Log.i(TAG, "Ringing alarm ${alarm.id} (snooze=$snooze)")
    }

    private suspend fun onTimeout() {
        val session = ringingState.session.value ?: return
        Log.i(TAG, "Ring duration elapsed; stopping and reporting missed alarms")
        session.alarms.forEach { notifications.showMissed(it.id, it.time, it.label) }
        finishSession { controller.dismiss(it) }
    }

    /** Applies [action] to every ringing alarm, then stops sound, notification and service. */
    private suspend fun finishSession(action: suspend (Long) -> Unit) {
        val session = ringingState.session.value
        timeoutJob?.cancel()
        player.stop()
        session?.alarms?.forEach { action(it.id) }
        ringingState.clear()
        stopService()
    }

    private fun stopService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        inForeground = false
        releaseWakeLock()
        stopSelf()
    }

    override fun onDestroy() {
        player.stop()
        ringingState.clear()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification)
            return
        }
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "systemExempted refused; falling back to mediaPlayback", e)
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        }
    }

    private fun updateNotification() {
        val alarm = ringingState.session.value?.primary ?: return
        val notification = buildNotification(
            title = formatTimeOfDay(this, alarm.time),
            text = alarm.label.ifBlank { getString(R.string.alarm_notification_title) },
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, text: String?): Notification {
        val fullScreen = AlarmIntents.ringingActivityPendingIntent(this)
        val snoozeMinutes = ringingState.session.value?.primary?.snoozeMinutes ?: Alarm.DEFAULT_SNOOZE_MINUTES
        return NotificationCompat.Builder(this, NotificationChannels.ALARM_RINGING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            // Updates (label, colliding alarms) must not alert or relaunch the full-screen intent.
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(
                0,
                resources.getQuantityString(R.plurals.alarm_action_snooze_minutes, snoozeMinutes, snoozeMinutes),
                AlarmIntents.serviceActionPendingIntent(this, AlarmIntents.ACTION_SNOOZE),
            )
            .addAction(
                0,
                getString(R.string.alarm_action_dismiss),
                AlarmIntents.serviceActionPendingIntent(this, AlarmIntents.ACTION_DISMISS),
            )
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Clock:Ringing")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private companion object {
        const val TAG = "RingingService"
        const val NOTIFICATION_ID = 1_000
        const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1_000L
    }
}
