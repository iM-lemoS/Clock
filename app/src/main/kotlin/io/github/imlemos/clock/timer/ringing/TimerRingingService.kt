package io.github.imlemos.clock.timer.ringing

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.model.GradualVolume
import io.github.imlemos.clock.core.audio.AlarmSoundPlayer
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.core.notifications.NotificationChannels
import io.github.imlemos.clock.timer.data.TimerController
import io.github.imlemos.clock.timer.data.TimerRepository
import io.github.imlemos.clock.timer.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Rings finished timers (docs/PLANO.md 4): like an alarm, a full-screen notification that opens
 * [TimerRingingActivity] when the phone is locked and a persistent pop-up while it is in use,
 * with sound until stopped. Uses its own sound player, so a timer and an alarm ringing together
 * never silence each other.
 */
@AndroidEntryPoint
class TimerRingingService : Service() {

    @Inject lateinit var controller: TimerController
    @Inject lateinit var repository: TimerRepository
    @Inject lateinit var state: TimerRingingState
    @Inject lateinit var settings: SettingsRepository

    private val player by lazy { AlarmSoundPlayer(applicationContext) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timeoutJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var inForeground = false
    private var notificationId = NOTIFICATION_ID
    private var screenOffRegistered = false

    /** Locking the phone while a timer rings brings up the full screen (see RingingService). */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (state.timers.value.isNotEmpty()) repostNotification()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_TIMER_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_START -> {
                if (!inForeground) {
                    startInForeground(buildNotification(getString(R.string.timer_done_title)))
                    inForeground = true
                }
                acquireWakeLock()
                registerScreenOff()
                scope.launch { onStart(id) }
            }
            ACTION_STOP -> scope.launch { finish(if (id >= 0) listOf(id) else state.timers.value.map { it.id }) { controller.stopRinging(it) } }
            ACTION_ADD_MINUTE -> scope.launch { finish(state.timers.value.map { it.id }) { controller.addMinute(it) } }
            else -> if (state.timers.value.isEmpty()) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private suspend fun onStart(id: Long) {
        val timer = repository.get(id) ?: return
        val first = state.timers.value.isEmpty()
        state.add(RingingTimer(id, timer.name.ifBlank { formatDuration(timer.run.durationMillis) }, timer.ringingSince ?: Instant.now()))
        if (first) {
            // The default sound from settings, at once (no gradual volume): a timer is a reminder.
            val sound = settings.defaultSound.first().copy(gradual = GradualVolume())
            player.start(sound, settings.audioOutput.first())
            timeoutJob = scope.launch {
                delay(MAX_RING_MILLIS)
                timeoutJob = null
                Log.i(TAG, "Timer rang for the maximum time; stopping")
                finish(state.timers.value.map { it.id }) { controller.stopRinging(it) }
            }
        }
        updateNotification()
        // With the app open the system shows no pop-up for its own notification: open the screen.
        // From the background this is refused quietly and the notification does the job.
        runCatching {
            startActivity(Intent(this, TimerRingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Applies [action] to [ids]; stops ringing when no timer is left. */
    private suspend fun finish(ids: List<Long>, action: suspend (Long) -> Unit) {
        ids.forEach {
            action(it)
            state.remove(it)
        }
        if (state.timers.value.isEmpty()) stopRinging() else updateNotification()
    }

    private fun stopRinging() {
        timeoutJob?.cancel()
        timeoutJob = null
        player.stop()
        state.clear()
        if (screenOffRegistered) unregisterReceiver(screenOffReceiver)
        screenOffRegistered = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        inForeground = false
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        stopSelf()
    }

    override fun onDestroy() {
        player.stop()
        state.clear()
        if (screenOffRegistered) unregisterReceiver(screenOffReceiver)
        wakeLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun registerScreenOff() {
        if (screenOffRegistered) return
        // Protected system broadcast; only the system can send it.
        ContextCompat.registerReceiver(this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_EXPORTED)
        screenOffRegistered = true
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notificationId, notification)
            return
        }
        try {
            ServiceCompat.startForeground(this, notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } catch (e: RuntimeException) {
            Log.w(TAG, "systemExempted refused; falling back to mediaPlayback", e)
            ServiceCompat.startForeground(this, notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(notificationId, buildNotification(names()))
    }

    /** The system launches a full-screen intent only for a new notification: move to the other id. */
    private fun repostNotification() {
        val previous = notificationId
        notificationId = if (previous == NOTIFICATION_ID) NOTIFICATION_ID_ALT else NOTIFICATION_ID
        startInForeground(buildNotification(names()))
        getSystemService(NotificationManager::class.java).cancel(previous)
    }

    private fun names(): String = state.timers.value.joinToString(" · ") { it.name }

    private fun buildNotification(text: String): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, 0, Intent(this, TimerRingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannels.TIMER_RINGING)
            .setSmallIcon(R.drawable.ic_hourglass_empty)
            .setContentTitle(getString(R.string.timer_done_title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, getString(R.string.timer_add_minute), serviceIntent(this, ACTION_ADD_MINUTE, -1))
            .addAction(0, getString(R.string.timer_stop), serviceIntent(this, ACTION_STOP, -1))
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Clock:Timer")
            .apply { acquire(MAX_RING_MILLIS + 60_000) }
    }

    companion object {
        private const val TAG = "TimerRingingService"
        private const val NOTIFICATION_ID = 2_000
        private const val NOTIFICATION_ID_ALT = 2_001
        private const val MAX_RING_MILLIS = 10L * 60 * 1000
        const val ACTION_START = "io.github.imlemos.clock.action.TIMER_RING"
        const val ACTION_STOP = "io.github.imlemos.clock.action.TIMER_STOP"
        const val ACTION_ADD_MINUTE = "io.github.imlemos.clock.action.TIMER_RING_ADD_MINUTE"
        const val EXTRA_TIMER_ID = "timer_id"

        fun start(context: Context, timerId: Long) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimerRingingService::class.java).setAction(ACTION_START).putExtra(EXTRA_TIMER_ID, timerId),
            )
        }

        /** Stops one ringing timer (deleted or changed elsewhere); no-op if it is not ringing. */
        fun stop(context: Context, timerId: Long) {
            runCatching {
                context.startService(Intent(context, TimerRingingService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_TIMER_ID, timerId))
            }
        }

        fun serviceIntent(context: Context, action: String, timerId: Long): PendingIntent = PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, TimerRingingService::class.java).setAction(action).putExtra(EXTRA_TIMER_ID, timerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
