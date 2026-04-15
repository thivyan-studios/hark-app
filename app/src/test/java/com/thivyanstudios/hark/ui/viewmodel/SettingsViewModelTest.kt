package com.thivyanstudios.hark.ui.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import app.cash.turbine.test
import com.thivyanstudios.hark.audio.AudioEngine
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.data.model.UserPreferences
import com.thivyanstudios.hark.util.SystemSettingsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var audioEngine: AudioEngine
    private lateinit var application: Application
    private lateinit var systemSettingsProvider: SystemSettingsProvider
    private lateinit var contentResolver: ContentResolver
    private lateinit var viewModel: SettingsViewModel

    private val prefsFlow = MutableStateFlow(UserPreferences())
    private val audioEngineEvents = Channel<AudioEngineEvent>(Channel.UNLIMITED)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        userPreferencesRepository = mock {
            on { userPreferencesFlow } doReturn prefsFlow
        }
        audioEngine = mock {
            on { events } doReturn audioEngineEvents
        }
        
        systemSettingsProvider = mock {
            on { developmentSettingsUri } doReturn Uri.parse("content://settings/global/development_settings_enabled")
            on { isDeveloperOptionsEnabled() } doReturn true
        }
        
        contentResolver = mock()
        val packageManager = mock<PackageManager>()
        val packageInfo = PackageInfo().apply { versionName = "1.0.0" }
        whenever(packageManager.getPackageInfo(anyString(), anyInt())).thenReturn(packageInfo)
        
        application = mock {
            on { packageName } doReturn "com.thivyanstudios.hark"
            on { getPackageManager() } doReturn packageManager
            on { getContentResolver() } doReturn contentResolver
            on { getString(anyInt(), anyString(), anyString()) } doReturn "Release-Candidate 1.0.0"
        }

        // Inject the testDispatcher as the ioDispatcher
        viewModel = SettingsViewModel(
            userPreferencesRepository, 
            audioEngine, 
            application, 
            systemSettingsProvider,
            testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState initially reflects user preferences`() = runTest {
        viewModel.uiState.test {
            val item = awaitItem()
            assertEquals(false, item.hapticFeedbackEnabled)
        }
    }

    @Test
    fun `preference setter methods call repository correctly`() = runTest {
        viewModel.setHapticFeedbackEnabled(true)
        verify(userPreferencesRepository).setHapticFeedbackEnabled(true)

        viewModel.setMicrophoneGain(1.5f)
        verify(userPreferencesRepository).setMicrophoneGain(1.5f)
    }

    @Test
    fun `disabling developer options forces bypassBluetoothChecks to false`() = runTest {
        // Setup mock to return false for developer options
        whenever(systemSettingsProvider.isDeveloperOptionsEnabled()).thenReturn(false)
        
        // Re-init with the testDispatcher to trigger the check on startup synchronously
        SettingsViewModel(
            userPreferencesRepository, 
            audioEngine, 
            application, 
            systemSettingsProvider,
            testDispatcher
        )
        
        // Verify that the repository was called to disable the bypass because dev options are off
        verify(userPreferencesRepository).setBypassBluetoothChecks(false)
    }

    @Test
    fun `audio engine events update support flags in uiState`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial
            
            audioEngineEvents.send(AudioEngineEvent.NoiseSuppressorAvailability(false))
            assertEquals(false, awaitItem().isNoiseSuppressionSupported)

            audioEngineEvents.send(AudioEngineEvent.DynamicsProcessingAvailability(false))
            assertEquals(false, awaitItem().isDynamicsProcessingSupported)
        }
    }
}
