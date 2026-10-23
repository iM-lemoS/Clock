package io.github.imlemos.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.core.data.prefs.AppearanceSettings
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    /** `null` until the first value is read from disk; the splash screen stays up meanwhile. */
    val appearance: StateFlow<AppearanceSettings?> =
        settings.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val showReliabilityIntro: StateFlow<Boolean> = settings.reliabilityIntroShown
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onReliabilityIntroShown() {
        viewModelScope.launch { settings.setReliabilityIntroShown() }
    }
}
