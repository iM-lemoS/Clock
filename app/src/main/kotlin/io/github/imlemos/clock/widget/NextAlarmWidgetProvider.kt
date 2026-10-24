package io.github.imlemos.clock.widget

import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

/** The next alarm that will ring, with a skip button and the reliability warning (docs/PLANO.md 5.2). */
@AndroidEntryPoint
class NextAlarmWidgetProvider : ClockAppWidgetProvider(WidgetKind.NEXT_ALARM) {
    // Hilt injects in the annotated class's onReceive.
    override fun onReceive(context: Context, intent: Intent) = super.onReceive(context, intent)
}
