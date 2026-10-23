package io.github.imlemos.clock.stopwatch

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.imlemos.clock.MainActivity
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.notifications.NotificationChannels
import io.github.imlemos.clock.core.scheduling.StopwatchMath
import io.github.imlemos.clock.core.scheduling.StopwatchRun
import io.github.imlemos.clock.core.time.ElapsedClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** An ongoing notification while the stopwatch has time on it; a Live Update on Android 16. */
@Singleton
class StopwatchNotification @Inject constructor(
    @ApplicationContext private val context: Context,
    private val elapsed: ElapsedClock,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(run: StopwatchRun) {
        val total = StopwatchMath.elapsed(run, elapsed.millis())
        if (!run.running && total == 0L) {
            manager.cancel(ID)
            return
        }
        val builder = NotificationCompat.Builder(context, NotificationChannels.STOPWATCH)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.nav_stopwatch))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(MainActivity.openTabIntent(context, MainActivity.TAB_STOPWATCH))
            .setRequestPromotedOngoing(true)
        if (run.running) {
            builder.setUsesChronometer(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() - total)
                .addAction(0, context.getString(R.string.stopwatch_pause), action(StopwatchReceiver.ACTION_PAUSE))
                .addAction(0, context.getString(R.string.stopwatch_lap), action(StopwatchReceiver.ACTION_LAP))
        } else {
            val text = formatStopwatch(total)
            builder.setShowWhen(false)
                .setContentText(context.getString(R.string.stopwatch_paused_at, text))
                .setShortCriticalText(text)
                .addAction(0, context.getString(R.string.stopwatch_resume), action(StopwatchReceiver.ACTION_START))
                .addAction(0, context.getString(R.string.stopwatch_reset), action(StopwatchReceiver.ACTION_RESET))
        }
        if (run.laps.isNotEmpty()) builder.setSubText(context.resources.getQuantityString(R.plurals.stopwatch_laps, run.laps.size, run.laps.size))
        manager.notify(ID, builder.build())
    }

    private fun action(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, StopwatchReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val ID = 4_000
    }
}

/** The stopwatch notification's buttons. */
@AndroidEntryPoint
class StopwatchReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: StopwatchController

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_START -> controller.start()
                    ACTION_PAUSE -> controller.pause()
                    ACTION_LAP -> controller.lap()
                    ACTION_RESET -> controller.reset()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_START = "io.github.imlemos.clock.action.STOPWATCH_START"
        const val ACTION_PAUSE = "io.github.imlemos.clock.action.STOPWATCH_PAUSE"
        const val ACTION_LAP = "io.github.imlemos.clock.action.STOPWATCH_LAP"
        const val ACTION_RESET = "io.github.imlemos.clock.action.STOPWATCH_RESET"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** "01:23,45" or "1:02:03,45": minutes, seconds and hundredths, hours when needed. */
fun formatStopwatch(millis: Long, decimalSeparator: Char = ','): String {
    val hundredths = millis / 10 % 100
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds / 60 % 60
    val seconds = totalSeconds % 60
    val head = if (hours > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes, seconds) else "%02d:%02d".format(java.util.Locale.ROOT, minutes, seconds)
    return head + decimalSeparator + "%02d".format(java.util.Locale.ROOT, hundredths)
}
