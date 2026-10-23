package io.github.imlemos.clock.stopwatch

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.imlemos.clock.core.scheduling.StopwatchMath
import io.github.imlemos.clock.core.scheduling.StopwatchRun
import io.github.imlemos.clock.core.time.ElapsedClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The stopwatch (docs/PLANO.md 5): kept in DataStore as timestamps, so it stays right with the app
 * closed, and shown in an ongoing notification while it has time on it.
 */
@Singleton
class StopwatchController @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val notification: StopwatchNotification,
    private val clock: Clock,
    private val elapsed: ElapsedClock,
) {
    val run: Flow<StopwatchRun> = dataStore.data.map { decode(it[KEY]) }.distinctUntilChanged()

    suspend fun start() = change { StopwatchMath.start(it, elapsed.millis(), clock.millis()) }

    suspend fun pause() = change { StopwatchMath.pause(it, elapsed.millis()) }

    suspend fun lap() = change { StopwatchMath.lap(it, elapsed.millis()) }

    suspend fun reset() = change { StopwatchMath.reset() }

    /** After a reboot or clock change, keeps a running stopwatch counting from the same moment. */
    suspend fun resync(afterBoot: Boolean) = change {
        if (afterBoot) StopwatchMath.afterReboot(it, elapsed.millis(), clock.millis()) else StopwatchMath.afterClockChange(it, elapsed.millis(), clock.millis())
    }

    private suspend fun change(transform: (StopwatchRun) -> StopwatchRun) {
        dataStore.edit { prefs -> prefs[KEY] = encode(transform(decode(prefs[KEY]))) }
        notification.show(run.first())
    }

    /** Stored as JSON; field names are persisted, never rename them. */
    @Serializable
    private data class Snapshot(
        val running: Boolean = false,
        val accumulatedMillis: Long = 0,
        val startedElapsed: Long? = null,
        val startedWall: Long? = null,
        val laps: List<Long> = emptyList(),
    )

    private fun encode(run: StopwatchRun): String = json.encodeToString(
        Snapshot.serializer(),
        Snapshot(run.running, run.accumulatedMillis, run.startedElapsed, run.startedWall, run.laps),
    )

    private fun decode(text: String?): StopwatchRun = text?.let {
        runCatching { json.decodeFromString(Snapshot.serializer(), it) }.getOrNull()
    }?.let { StopwatchRun(it.running, it.accumulatedMillis, it.startedElapsed, it.startedWall, it.laps) } ?: StopwatchRun()

    private companion object {
        val KEY = stringPreferencesKey("stopwatch")
        val json = Json { ignoreUnknownKeys = true }
    }
}
