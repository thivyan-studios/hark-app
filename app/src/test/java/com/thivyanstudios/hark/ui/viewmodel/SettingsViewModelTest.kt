package com.thivyanstudios.hark.ui.viewmodel

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.thivyanstudios.hark.BuildConfig
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.audio.AudioEngine
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.data.model.UserPreferences
import com.thivyanstudios.hark.service.AudioServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var audioServiceManager: AudioServiceManager
    private lateinit var audioEngine: AudioEngine
    private lateinit var application: Application
    private lateinit var viewModel: SettingsViewModel

    private val prefsFlow = MutableStateFlow(UserPreferences())
    private val audioEngineEvents = Channel<AudioEngineEvent>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        userPreferencesRepository = mock {
            on { userPreferencesFlow } doReturn prefsFlow
        }
        audioServiceManager = mock()
        audioEngine = mock {
            on { events } doReturn audioEngineEvents
        }
        
        val packageManager = mock<PackageManager>()
        val packageInfo = PackageInfo().apply { versionName = "1.0.0" }
        whenever(packageManager.getPackageInfo(anyString(), anyInt())).thenReturn(packageInfo)
        
        application = mock {
            on { packageName } doReturn "com.thivyanstudios.hark"
            on { getPackageManager() } doReturn packageManager
            on { getString(R.string.version_text, BuildConfig.BUILD_STATUS, "1.0.0") } doReturn "Release-Candidate 1.0.0"
        }

        viewModel = SettingsViewModel(userPreferencesRepository, audioServiceManager, audioEngine, application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState reflects user preferences`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(false, initial.hapticFeedbackEnabled)

            prefsFlow.value = UserPreferences(hapticFeedbackEnabled = true)
            val updated = awaitItem()
            assertEquals(true, updated.hapticFeedbackEnabled)
        }
    }

    @Test
    fun `setHapticFeedbackEnabled calls repository`() = runTest {
        viewModel.setHapticFeedbackEnabled(true)
        advanceUntilIdle()
        verify(userPreferencesRepository).setHapticFeedbackEnabled(true)
    }

    @Test
    fun `setMicrophoneGain calls repository`() = runTest {
        viewModel.setMicrophoneGain(10f)
        advanceUntilIdle()
        verify(userPreferencesRepository).setMicrophoneGain(10f)
    }

    @Test
    fun `audio engine availability events update uiState`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial
            
            audioEngineEvents.send(AudioEngineEvent.NoiseSuppressorAvailability(false))
            
            val updated = awaitItem()
            assertEquals(false, updated.isNoiseSuppressionSupported)
        }
    }
}
