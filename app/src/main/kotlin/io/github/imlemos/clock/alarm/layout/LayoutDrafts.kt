package io.github.imlemos.clock.alarm.layout

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A new layout saved from the editor, for the screen that asked for it with [requestId]. */
data class CreatedLayout(val requestId: Long, val layoutId: Long)

/**
 * Hands a newly saved layout back to whoever opened the editor for it (the alarm editor's "New
 * layout"). A new layout is only a draft until the user saves it.
 */
@Singleton
class LayoutDrafts @Inject constructor() {
    private val _created = MutableSharedFlow<CreatedLayout>(extraBufferCapacity = 4)
    val created: SharedFlow<CreatedLayout> = _created.asSharedFlow()

    fun publish(result: CreatedLayout) {
        _created.tryEmit(result)
    }
}
