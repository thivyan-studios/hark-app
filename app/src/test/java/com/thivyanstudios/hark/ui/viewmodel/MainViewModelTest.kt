package com.thivyanstudios.hark.ui.viewmodel

import app.cash.turbine.test
import com.thivyanstudios.hark.audio.AudioEngine
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.data.model.UserPreferences
import com.thivyanstudios.hark.service.AudioServiceManager
import com.thivyanstudios.hark.service.AudioStreamingController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    
    private lateinit var audioServiceManager: AudioServiceManager
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var audioEngine: AudioEngine
    private lateinit var viewModel: MainViewModel

    private val serviceFlow = MutableStateFlow<AudioStreamingController?>(null)
    private val prefsFlow = MutableStateFlow(UserPreferences())
    private val errorEvents = MutableSharedFlow<String>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        audioServiceManager = mock {
            on { service } doReturn serviceFlow
        }
        userPreferencesRepository = mock {
            on { userPreferencesFlow } doReturn prefsFlow
        }
        audioEngine = mock {
            on { errorEvents } doReturn errorEvents
        }
        
        viewModel = MainViewModel(audioServiceManager, userPreferencesRepository, audioEngine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleStreaming stops streaming if already streaming`() = runTest {
        val mockService = mock<AudioStreamingController> {
            on { isStreaming } doReturn MutableStateFlow(true)
        }
        serviceFlow.value = mockService
        
        viewModel.toggleStreaming("Connect Message")
        
        verify(mockService).stopStreaming()
    }

    @Test
    fun `toggleStreaming starts streaming if not streaming and hearing aid connected`() = runTest {
        val mockService = mock<AudioStreamingController> {
            on { isStreaming } doReturn MutableStateFlow(false)
            on { hearingAidConnected } doReturn MutableStateFlow(true)
        }
        serviceFlow.value = mockService
        
        viewModel.toggleStreaming("Connect Message")
        
        verify(mockService).startStreaming()
    }

    @Test
    fun `toggleStreaming starts streaming if not streaming and bypass is enabled even if not connected`() = runTest {
        val mockService = mock<AudioStreamingController> {
            on { isStreaming } doReturn MutableStateFlow(false)
            on { hearingAidConnected } doReturn MutableStateFlow(false)
        }
        serviceFlow.value = mockService
        
        // Use turbine to wait for the UI state to actually reflect the preference change
        viewModel.uiState.test {
            awaitItem() // Initial state
            
            // Enable bypass
            prefsFlow.value = UserPreferences(bypassBluetoothChecks = true)
            
            val updatedState = awaitItem()
            assertEquals(true, updatedState.bypassBluetoothChecks)
            
            // Now that we KNOW the state has updated, call the action
            viewModel.toggleStreaming("Connect Message")
            
            verify(mockService).startStreaming()
        }
    }

    @Test
    fun `toggleStreaming shows error and does not start if not connected and no bypass`() = runTest {
        val mockService = mock<AudioStreamingController> {
            on { isStreaming } doReturn MutableStateFlow(false)
            on { hearingAidConnected } doReturn MutableStateFlow(false)
        }
        serviceFlow.value = mockService
        
        // Ensure bypass is disabled
        prefsFlow.value = UserPreferences(bypassBluetoothChecks = false)
        
        viewModel.snackbarEvents.test {
            viewModel.toggleStreaming("Connect Message")
            
            assertEquals("Connect Message", awaitItem())
            verify(mockService, org.mockito.kotlin.never()).startStreaming()
        }
    }

    @Test
    fun `uiState correctly merges service and preference states`() = runTest {
        val mockService = mock<AudioStreamingController> {
            on { isStreaming } doReturn MutableStateFlow(true)
            on { hearingAidConnected } doReturn MutableStateFlow(false)
        }
        
        viewModel.uiState.test {
            awaitItem() // Initial empty state
            
            serviceFlow.value = mockService
            var item = awaitItem()
            assertEquals(true, item.isStreaming)
            assertEquals(false, item.hearingAidConnected)
            
            prefsFlow.value = UserPreferences(hapticFeedbackEnabled = true)
            item = awaitItem()
            assertEquals(true, item.hapticFeedbackEnabled)
            // The isStreaming should remain true as it comes from the service
            assertEquals(true, item.isStreaming)
        }
    }
}
