package io.github.imlemos.clock.core.time

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells whether the phone rebooted since a consumer last checked, from the system boot count.
 * Timers and the stopwatch keep `elapsedRealtime` values, which restart at zero on boot; whether
 * they must be rebuilt cannot depend on which event (app start, boot broadcast, clock change)
 * happens to arrive first.
 */
@Singleton
class BootDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private val mutex = Mutex()

    /**
     * True once per boot for [consumer] when the phone booted since its last call. The first call
     * ever (nothing stored yet) is not a reboot: there is nothing older to rebuild.
     */
    suspend fun rebootedSinceLastCheck(consumer: String): Boolean = mutex.withLock {
        val current = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (current < 0) return@withLock false
        val key = intPreferencesKey("boot_count_$consumer")
        var seen: Int? = null
        dataStore.edit { prefs ->
            seen = prefs[key]
            prefs[key] = current
        }
        seen != null && seen != current
    }
}
