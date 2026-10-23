package io.github.imlemos.clock.timer

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.imlemos.clock.MainActivity
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.notifications.NotificationChannels
import io.github.imlemos.clock.core.scheduling.TimerMath
import io.github.imlemos.clock.core.scheduling.TimerStatus
import io.github.imlemos.clock.core.time.ElapsedClock
import io.github.imlemos.clock.timer.data.Timer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One ongoing notification per running or paused timer, counting down, grouped when there are
 * several (docs/PLANO.md 4). On Android 16 they ask to be promoted to Live Updates.
 */
@Singleton
class TimerNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val elapsed: ElapsedClock,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun showRunning(timers: List<Timer>) {
        val active = timers.filter { it.status == TimerStatus.RUNNING || it.status == TimerStatus.PAUSED }
        val grouped = active.size > 1
        active.forEach { manager.notify(BASE + it.id.toInt(), build(it, grouped)) }
        timers.filterNot { it in active }.forEach { cancel(it.id) }
        if (grouped) manager.notify(SUMMARY_ID, summary(active.size)) else manager.cancel(SUMMARY_ID)
    }

    fun cancel(timerId: Long) = manager.cancel(BASE + timerId.toInt())

    private fun build(timer: Timer, grouped: Boolean): Notification {
        val running = timer.status == TimerStatus.RUNNING
        val left = TimerMath.remaining(timer.run, elapsed.millis())
        val builder = NotificationCompat.Builder(context, NotificationChannels.TIMERS)
            .setSmallIcon(R.drawable.ic_hourglass_empty)
            .setContentTitle(timer.name.ifBlank { context.getString(R.string.timer_default_name, formatDuration(timer.run.durationMillis)) })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(MainActivity.openTabIntent(context, MainActivity.TAB_TIMERS))
            .setRequestPromotedOngoing(true)
        if (running) {
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() + left)
                .addAction(0, context.getString(R.string.timer_pause), TimerReceiver.pendingIntent(context, TimerReceiver.ACTION_PAUSE, timer.id))
                .addAction(0, context.getString(R.string.timer_add_minute), TimerReceiver.pendingIntent(context, TimerReceiver.ACTION_ADD_MINUTE, timer.id))
        } else {
            builder.setShowWhen(false)
                .setContentText(context.getString(R.string.timer_paused_left, formatDuration(left)))
                .setShortCriticalText(formatDuration(left))
                .addAction(0, context.getString(R.string.timer_resume), TimerReceiver.pendingIntent(context, TimerReceiver.ACTION_RESUME, timer.id))
                .addAction(0, context.getString(R.string.timer_reset), TimerReceiver.pendingIntent(context, TimerReceiver.ACTION_RESET, timer.id))
        }
        if (grouped) builder.setGroup(GROUP)
        return builder.build()
    }

    private fun summary(count: Int): Notification = NotificationCompat.Builder(context, NotificationChannels.TIMERS)
        .setSmallIcon(R.drawable.ic_hourglass_empty)
        .setContentTitle(context.resources.getQuantityString(R.plurals.timers_running, count, count))
        .setGroup(GROUP)
        .setGroupSummary(true)
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(MainActivity.openTabIntent(context, MainActivity.TAB_TIMERS))
        .build()

    private companion object {
        const val BASE = 3_000
        const val SUMMARY_ID = 2_999
        const val GROUP = "timers"
    }
}

/** "4:05", "1:02:03" (hours only when needed). */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds / 60 % 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds) else "%d:%02d".format(Locale.ROOT, minutes, seconds)
}
