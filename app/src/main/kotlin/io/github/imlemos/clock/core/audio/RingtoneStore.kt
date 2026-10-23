package io.github.imlemos.clock.core.audio

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.imlemos.clock.core.data.di.DeviceProtected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PickedTone(val uri: String, val title: String)

/**
 * Alarm sounds chosen from files. They are copied into device-protected app storage (no storage
 * permission needed, and they play before the first unlock and after the original is deleted).
 */
@Singleton
class RingtoneStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @DeviceProtected deviceContext: Context,
) {
    private val dir = File(deviceContext.filesDir, "tones")

    /** Copies a file picked with the system document picker. Returns `null` if it is unusable. */
    suspend fun import(source: Uri): PickedTone? = withContext(Dispatchers.IO) {
        runCatching {
            val (name, size) = context.contentResolver.query(
                source, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(0) to cursor.getLong(1)
            } ?: return@runCatching null
            if (size > MAX_BYTES) return@runCatching null
            dir.mkdirs()
            val extension = name.substringAfterLast('.', "").take(5).lowercase()
            val target = File(dir, UUID.randomUUID().toString() + if (extension.isEmpty()) "" else ".$extension")
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            PickedTone(target.toUri().toString(), name.substringBeforeLast('.'))
        }.onFailure { Log.w(TAG, "Could not import $source", it) }.getOrNull()
    }

    /** Title of a sound picked from the system ringtone list. */
    fun systemTitle(uri: Uri): String? =
        runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()

    /** Deletes copied files that no alarm or default refers to anymore. */
    suspend fun deleteUnused(inUse: Set<String>) = withContext(Dispatchers.IO) {
        dir.listFiles()?.filter { it.toUri().toString() !in inUse }?.forEach { it.delete() }
    }

    private companion object {
        const val TAG = "RingtoneStore"
        const val MAX_BYTES = 30L * 1024 * 1024
    }
}
