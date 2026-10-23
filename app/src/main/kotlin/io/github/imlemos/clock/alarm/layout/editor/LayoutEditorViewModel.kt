package io.github.imlemos.clock.alarm.layout.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.alarm.layout.Background
import io.github.imlemos.clock.alarm.layout.BackgroundStore
import io.github.imlemos.clock.alarm.layout.ClockFont
import io.github.imlemos.clock.alarm.layout.ClockStyle
import io.github.imlemos.clock.alarm.layout.Frame
import io.github.imlemos.clock.alarm.layout.LayoutGeometry
import io.github.imlemos.clock.alarm.layout.LayoutRepository
import io.github.imlemos.clock.alarm.layout.Size2
import io.github.imlemos.clock.alarm.layout.LayoutSpec
import io.github.imlemos.clock.alarm.layout.Placement
import io.github.imlemos.clock.alarm.layout.PresetKind
import io.github.imlemos.clock.alarm.layout.render.LayoutElementId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LayoutEditorState(
    val loaded: Boolean = false,
    val name: String = "",
    val spec: LayoutSpec = LayoutSpec(),
    val selected: LayoutElementId? = LayoutElementId.CLOCK,
    /** Which options the panel shows; `null` is the background. */
    val tab: LayoutElementId? = LayoutElementId.CLOCK,
    val dirty: Boolean = false,
)

/** Edits one of the user's layouts (presets are duplicated before editing). */
@HiltViewModel(assistedFactory = LayoutEditorViewModel.Factory::class)
class LayoutEditorViewModel @AssistedInject constructor(
    @Assisted val layoutId: Long,
    private val repository: LayoutRepository,
    private val backgrounds: BackgroundStore,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(layoutId: Long): LayoutEditorViewModel
    }

    private val _state = MutableStateFlow(LayoutEditorState())
    val state: StateFlow<LayoutEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val layout = repository.get(layoutId)
            _state.update { it.copy(loaded = true, name = layout?.name.orEmpty(), spec = layout?.spec ?: LayoutSpec()) }
        }
    }

    /** Selecting an element on the canvas also shows its options; empty space keeps the tab. */
    fun select(id: LayoutElementId?) = _state.update { if (id == null) it.copy(selected = null) else it.copy(selected = id, tab = id) }

    fun showTab(tab: LayoutElementId?) = _state.update { it.copy(tab = tab, selected = tab) }

    fun rename(name: String) {
        if (name.isNotBlank()) _state.update { it.copy(name = name.trim(), dirty = true) }
    }

    fun setPlacement(id: LayoutElementId, placement: Placement) = edit { spec ->
        when (id) {
            LayoutElementId.CLOCK -> spec.copy(clock = spec.clock.copy(placement = placement))
            LayoutElementId.DATE -> spec.copy(date = spec.date.copy(placement = placement))
            LayoutElementId.LABEL -> spec.copy(label = spec.label.copy(placement = placement))
            LayoutElementId.CONTROLS -> spec.copy(controls = spec.controls.copy(placement = placement))
        }
    }

    fun setScale(id: LayoutElementId, scale: Float) = setPlacement(id, _state.value.spec.placementOf(id).copy(scale = scale))


    fun setClockStyle(style: ClockStyle) = edit { it.copy(clock = it.clock.copy(style = style)) }

    /** A new font starts at its own default weight. */
    fun setClockFont(font: ClockFont) = edit { it.copy(clock = it.clock.copy(font = font, weight = null)) }

    fun setClockWeight(weight: Int) = edit { it.copy(clock = it.clock.copy(weight = weight)) }

    fun setClockRoundness(roundness: Int) = edit { it.copy(clock = it.clock.copy(roundness = roundness)) }

    /** [argb] `null` returns the element to the automatic colour. */
    fun setColor(id: LayoutElementId, argb: Int?) = edit { spec ->
        when (id) {
            LayoutElementId.CLOCK -> spec.copy(clock = spec.clock.copy(color = argb))
            LayoutElementId.DATE -> spec.copy(date = spec.date.copy(color = argb))
            LayoutElementId.LABEL -> spec.copy(label = spec.label.copy(color = argb))
            LayoutElementId.CONTROLS -> spec
        }
    }

    fun setVisible(id: LayoutElementId, visible: Boolean) = edit { spec ->
        when (id) {
            LayoutElementId.DATE -> spec.copy(date = spec.date.copy(visible = visible))
            LayoutElementId.LABEL -> spec.copy(label = spec.label.copy(visible = visible))
            else -> spec
        }
    }

    fun setPreset(kind: PresetKind) = edit { it.copy(background = Background.Preset(kind)) }

    fun setBackground(background: Background) = edit { it.copy(background = background) }

    /** Drag and pinch on a photo or video background (docs/PLANO.md 3.7). */
    fun transformBackground(dx: Float, dy: Float, zoom: Float, area: Size2, mediaAspect: Float) = edit { spec ->
        fun Frame.moved() = LayoutGeometry.transformFrame(this, dx, dy, zoom, area, mediaAspect)
        when (val background = spec.background) {
            is Background.Image -> spec.copy(background = background.copy(frame = background.frame.moved()))
            is Background.Video -> spec.copy(background = background.copy(frame = background.frame.moved()))
            is Background.Preset -> spec
        }
    }

    /** Copies a picked photo or video into the app; [onFailed] if it cannot be used. */
    fun importBackground(uri: Uri, video: Boolean, onFailed: () -> Unit) {
        viewModelScope.launch {
            val path = backgrounds.import(uri, video) ?: return@launch onFailed()
            edit { it.copy(background = if (video) Background.Video(path) else Background.Image(path)) }
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val current = _state.value
            repository.save(layoutId, current.name, current.spec)
            _state.update { it.copy(dirty = false) }
            onSaved()
        }
    }

    private fun edit(transform: (LayoutSpec) -> LayoutSpec) = _state.update { it.copy(spec = transform(it.spec), dirty = true) }
}

/** Where [id] sits in this layout. */
fun LayoutSpec.placementOf(id: LayoutElementId): Placement = when (id) {
    LayoutElementId.CLOCK -> clock.placement
    LayoutElementId.DATE -> date.placement
    LayoutElementId.LABEL -> label.placement
    LayoutElementId.CONTROLS -> controls.placement
}
