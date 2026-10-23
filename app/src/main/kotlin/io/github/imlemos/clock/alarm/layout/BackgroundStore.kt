package io.github.imlemos.clock.alarm.layout

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.imlemos.clock.core.data.di.DeviceProtected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Photos and videos for alarm screen backgrounds. Picked with the system photo picker (no storage
 * permission) and copied into device-protected storage, so they show before the first unlock and
 * survive the original being deleted.
 */
@Singleton
class BackgroundStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @DeviceProtected deviceContext: Context,
) {
    private val dir = File(deviceContext.filesDir, "backgrounds")

    /** Copies [source]; returns the file path, or `null` if it cannot be read or is too large. */
    suspend fun import(source: Uri, video: Boolean): String? = withContext(Dispatchers.IO) {
        runCatching {
            val size = context.contentResolver.openAssetFileDescriptor(source, "r")?.use { it.length } ?: -1L
            if (size > if (video) MAX_VIDEO_BYTES else MAX_IMAGE_BYTES) return@runCatching null
            dir.mkdirs()
            val target = File(dir, UUID.randomUUID().toString() + if (video) ".mp4" else ".img")
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            target.absolutePath
        }.onFailure { Log.w(TAG, "Could not import $source", it) }.getOrNull()
    }

    /** Deletes copies no layout uses. Recent ones are kept: an open editor may not have saved yet. */
    suspend fun deleteUnused(inUse: Set<String>) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - GRACE_MILLIS
        dir.listFiles()?.filter { it.absolutePath !in inUse && it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private companion object {
        const val TAG = "BackgroundStore"
        const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
        const val MAX_VIDEO_BYTES = 100L * 1024 * 1024
        const val GRACE_MILLIS = 24L * 60 * 60 * 1000
    }
}
