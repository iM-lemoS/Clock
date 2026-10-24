package io.github.imlemos.clock.widget

import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Which group of settings the editor shows. */
enum class WidgetTab { TIME, DETAILS, BACKGROUND, LAYOUT }

data class WidgetConfigState(
    val kind: WidgetKind,
    val loaded: Boolean = false,
    val config: WidgetConfig = WidgetConfig(),
    val tab: WidgetTab = WidgetTab.TIME,
    /** Previews for a light and a dark home screen. */
    val previewNight: Boolean = true,
    val preview: Bitmap? = null,
    val presetPreviews: Map<WidgetPreset, Bitmap> = emptyMap(),
    /** The widget's size in dp, so the preview has its proportions. */
    val widthDp: Int = 0,
    val heightDp: Int = 0,
)

/** The widget editor: opens when a widget is added and again from its "Settings" (reconfigurable). */
@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val store: WidgetConfigStore,
    private val updater: WidgetUpdater,
) : ViewModel() {

    val widgetId: Int = savedState[AppWidgetManager.EXTRA_APPWIDGET_ID] ?: AppWidgetManager.INVALID_APPWIDGET_ID
    private val kind = updater.kindOf(widgetId) ?: WidgetKind.CLOCK
    private val _state = MutableStateFlow(WidgetConfigState(kind = kind))
    val state: StateFlow<WidgetConfigState> = _state.asStateFlow()
    private var renderJob: Job? = null

    init {
        viewModelScope.launch {
            val (width, height) = updater.sizeDp(widgetId, kind)
            _state.update { it.copy(loaded = true, config = store.get(widgetId), widthDp = width, heightDp = height) }
            render(withPresets = true)
        }
    }

    fun edit(transform: (WidgetConfig) -> WidgetConfig) {
        _state.update { it.copy(config = transform(it.config)) }
        render(withPresets = false)
    }

    fun applyPreset(preset: WidgetPreset) = edit(preset::applyTo)

    fun showTab(tab: WidgetTab) = _state.update { it.copy(tab = tab) }

    /** Starts the preview in the theme the app is in; only once, before the user picks one. */
    fun initPreviewNight(night: Boolean) {
        if (previewNightChosen) return
        previewNightChosen = true
        if (_state.value.previewNight != night) setPreviewNight(night)
    }

    private var previewNightChosen = false

    fun setPreviewNight(night: Boolean) {
        previewNightChosen = true
        _state.update { it.copy(previewNight = night) }
        render(withPresets = true)
    }

    /** Copies a picked photo into the app and makes it the background; [onFailed] if unreadable. */
    fun importPhoto(uri: Uri, onFailed: () -> Unit) {
        viewModelScope.launch {
            val name = store.importPhoto(uri) ?: return@launch onFailed()
            edit { it.copy(background = it.background.copy(type = WidgetBackgroundType.PHOTO, photo = name)) }
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            store.set(widgetId, _state.value.config)
            updater.update(kind, intArrayOf(widgetId))
            updater.scheduleTick()
            onSaved()
        }
    }

    /** Redraws the preview (and the preset thumbnails, which only change with the theme). */
    private fun render(withPresets: Boolean) {
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val current = _state.value
            val preview = withContext(Dispatchers.Default) {
                updater.preview(kind, current.config, current.widthDp, current.heightDp, current.previewNight).bitmap
            }
            _state.update { it.copy(preview = preview) }
            if (withPresets) {
                val thumbnails = withContext(Dispatchers.Default) {
                    WidgetPreset.entries.associateWith { preset ->
                        updater.preview(kind, preset.applyTo(current.config), THUMB_WIDTH_DP, THUMB_HEIGHT_DP, current.previewNight).bitmap
                    }
                }
                _state.update { it.copy(presetPreviews = thumbnails) }
            }
        }
    }

    private companion object {
        const val THUMB_WIDTH_DP = 140
        const val THUMB_HEIGHT_DP = 80
    }
}
