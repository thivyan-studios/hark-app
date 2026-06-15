package com.thivyanstudios.hark.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thivyanstudios.hark.audio.equalizer.EqualizerManager
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EqualizerUiState(
    val bandGains: List<Float> = List(5) { 0f },
    val selectedPresetIndex: Int = -1,
    val bandLabels: List<String> = listOf("125Hz", "500Hz", "1kHz", "2kHz", "4kHz")
)

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerManager: EqualizerManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val gains = listOf(
                prefs.eqBand1Gain, prefs.eqBand2Gain, prefs.eqBand3Gain,
                prefs.eqBand4Gain, prefs.eqBand5Gain
            )
            _uiState.value = _uiState.value.copy(bandGains = gains)
            equalizerManager.setAllBands(gains)
            equalizerManager.initialize()
        }
    }

    fun setBandGain(band: Int, gain: Float) {
        val g = gain.coerceIn(-12f, 12f)
        val newGains = _uiState.value.bandGains.toMutableList()
        newGains[band] = g
        _uiState.value = _uiState.value.copy(bandGains = newGains, selectedPresetIndex = -1)
        equalizerManager.setBandGain(band, g)
    }

    fun commitBandGain(band: Int) {
        val gains = _uiState.value.bandGains
        viewModelScope.launch(ioDispatcher) {
            saveGains(gains)
        }
    }

    fun applyPreset(presetIndex: Int) {
        if (presetIndex !in equalizerManager.presets.indices) return
        val preset = equalizerManager.presets[presetIndex]
        val gains = preset.gains.toList()
        equalizerManager.setAllBands(gains)
        _uiState.value = _uiState.value.copy(bandGains = gains, selectedPresetIndex = presetIndex)
        viewModelScope.launch(ioDispatcher) { saveGains(gains) }
    }

    fun resetEqualizer() {
        equalizerManager.reset()
        val zeros = List(5) { 0f }
        _uiState.value = _uiState.value.copy(bandGains = zeros, selectedPresetIndex = -1)
        viewModelScope.launch(ioDispatcher) { saveGains(zeros) }
    }

    fun applyHearingTestResults(gains: List<Float>) {
        val clamped = gains.map { it.coerceIn(-12f, 12f) }
        equalizerManager.setAllBands(clamped)
        _uiState.value = _uiState.value.copy(bandGains = clamped, selectedPresetIndex = -1)
        viewModelScope.launch(ioDispatcher) { saveGains(clamped) }
    }

    private suspend fun saveGains(gains: List<Float>) {
        if (gains.size >= 5) {
            userPreferencesRepository.setEqBand1Gain(gains[0])
            userPreferencesRepository.setEqBand2Gain(gains[1])
            userPreferencesRepository.setEqBand3Gain(gains[2])
            userPreferencesRepository.setEqBand4Gain(gains[3])
            userPreferencesRepository.setEqBand5Gain(gains[4])
        }
    }

    override fun onCleared() {
        super.onCleared()
        equalizerManager.release()
    }
}
