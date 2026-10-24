package io.github.imlemos.clock.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.imlemos.clock.alarm.data.AlarmController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Buttons on widgets: "Skip next" and "Don't skip" on the next-alarm widget. */
@AndroidEntryPoint
class WidgetActionReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: AlarmController
    @Inject lateinit var updater: WidgetUpdater

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SKIP) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return
        val pending = goAsync()
        scope.launch {
            try {
                controller.setSkipNext(alarmId, intent.getBooleanExtra(EXTRA_SKIP, true))
                updater.update(WidgetKind.NEXT_ALARM, updater.ids(WidgetKind.NEXT_ALARM))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_SKIP = "io.github.imlemos.clock.widget.SKIP"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_SKIP = "skip"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Skips (or un-skips, when [skip] is false) the next occurrence of [alarmId]. */
        fun skipIntent(context: Context, alarmId: Long, skip: Boolean): PendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            Intent(context, WidgetActionReceiver::class.java).setAction(ACTION_SKIP)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_SKIP, skip),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
