package io.github.imlemos.clock.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** Each widget's look, by widget id, as JSON in its own DataStore file. */
@Singleton
class WidgetConfigStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val dataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { context.preferencesDataStoreFile("widgets") },
    )

    /** The saved look, or the default for a widget that has none yet. */
    suspend fun get(widgetId: Int): WidgetConfig {
        val json = dataStore.data.first()[key(widgetId)] ?: return WidgetConfig()
        return runCatching { JSON.decodeFromString<WidgetConfig>(json) }
            .onFailure { Log.w(TAG, "Unreadable config for widget $widgetId", it) }
            .getOrDefault(WidgetConfig())
    }

    suspend fun set(widgetId: Int, config: WidgetConfig) {
        dataStore.edit { it[key(widgetId)] = JSON.encodeToString(WidgetConfig.serializer(), config) }
        pruneUnusedPhotos()
    }

    suspend fun remove(widgetIds: IntArray) {
        dataStore.edit { prefs -> widgetIds.forEach { prefs.remove(key(it)) } }
        pruneUnusedPhotos()
    }

    /**
     * Copies a picked photo into app storage, scaled so its longer side is at most
     * [MAX_PHOTO_SIDE] (widgets are small). Returns its file name, or `null` if it cannot be read.
     */
    suspend fun importPhoto(source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_PHOTO_SIDE) {
                    val ratio = MAX_PHOTO_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * ratio).toInt(), (info.size.height * ratio).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            photoDir.mkdirs()
            val name = UUID.randomUUID().toString() + ".jpg"
            File(photoDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            name
        }.onFailure { Log.w(TAG, "Could not import $source", it) }.getOrNull()
    }

    fun photoFile(name: String): File = File(photoDir, name)

    /**
     * Deletes photos no widget uses. Recent ones are kept: a configuration screen may have
     * imported one that is not saved yet.
     */
    private suspend fun pruneUnusedPhotos() = withContext(Dispatchers.IO) {
        val inUse = dataStore.data.first().asMap().values.mapNotNull { json ->
            runCatching { JSON.decodeFromString<WidgetConfig>(json as String).background.photo }.getOrNull()
        }.toSet()
        val cutoff = System.currentTimeMillis() - PHOTO_GRACE_MILLIS
        photoDir.listFiles()?.filter { it.name !in inUse && it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private val photoDir get() = File(context.filesDir, "widget_photos")

    private fun key(widgetId: Int) = stringPreferencesKey("widget_$widgetId")

    private companion object {
        const val TAG = "WidgetConfigStore"
        const val MAX_PHOTO_SIDE = 1280
        const val JPEG_QUALITY = 90
        const val PHOTO_GRACE_MILLIS = 60L * 60 * 1000
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
