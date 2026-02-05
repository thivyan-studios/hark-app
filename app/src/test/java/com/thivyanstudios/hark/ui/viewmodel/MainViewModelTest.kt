package com.thivyanstudios.hark.ui.viewmodel

import app.cash.turbine.test
import com.thivyanstudios.hark.audio.AudioEngine
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.data.model.UserPreferences
import com.thivyanstudios.hark.service.AudioServiceManager
import com.thivyanstudios.hark.service.AudioStreamingController
import com.thivyanstudios.hark.ui.MainUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
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
    fun `uiState initially reflects default preferences when service is null`() = runTest {
        viewModel.uiState.test {
            val item = awaitItem()
            assertEquals(false, item.isStreaming)
            assertEquals(false, item.hapticFeedbackEnabled)
            assertEquals(false, item.keepScreenOn)
        }
    }

    @Test
    fun `uiState updates when preferences change and service is null`() = runTest {
        viewModel.uiState.test {
            awaitItem() // Initial
            
            prefsFlow.value = UserPreferences(hapticFeedbackEnabled = true, keepScreenOn = true)
            
            val updated = awaitItem()
            assertEquals(true, updated.hapticFeedbackEnabled)
            assertEquals(true, updated.keepScreenOn)
        }
    }

    @Test
    fun `uiState reflects service state when service is bound`() = runTest {
        val mockService = mock<AudioStreamingController> {
            on { isStreaming } doReturn MutableStateFlow(true)
            on { hearingAidConnected } doReturn MutableStateFlow(true)
        }
        
        viewModel.uiState.test {
            awaitItem() // Initial
            
            serviceFlow.value = mockService
            
            val updated = awaitItem()
            assertEquals(true, updated.isStreaming)
            assertEquals(true, updated.hearingAidConnected)
        }
    }

    @Test
    fun `snackbarEvents receives errors from audioEngine`() = runTest {
        viewModel.snackbarEvents.test {
            errorEvents.emit("Test Error")
            assertEquals("Test Error", awaitItem())
        }
    }
}
