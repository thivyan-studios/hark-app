package com.thivyanstudios.hark.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.ui.theme.BetaBadge
import com.thivyanstudios.hark.ui.theme.GlassActionBar
import com.thivyanstudios.hark.ui.theme.GlassActionButton
import com.thivyanstudios.hark.ui.theme.HarkText
import com.thivyanstudios.hark.ui.theme.glassSurface
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscribeScreen(
    isStreaming: Boolean,
    transcription: String,
    onClearTranscription: () -> Unit,
    onShareTranscription: (String) -> Unit,
    fontSize: Float = 22f,
    hapticFeedbackEnabled: Boolean = false,
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    var showCopyFeedback by remember { mutableStateOf(false) }
    // The transcript scrolls behind the action bar, so the bar blurs it like the navbar does
    val hazeState = rememberHazeState()

    // Automatically scroll to bottom when new transcription arrives
    LaunchedEffect(transcription) {
        if (transcription.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    if (showCopyFeedback) {
        LaunchedEffect(Unit) {
            delay(2000L)
            showCopyFeedback = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isStreaming) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        HarkText(
                            text = "Live Transcription",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BetaBadge()
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (transcription.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    HarkText(
                        text = if (isStreaming) stringResource(R.string.transcribe_empty_text) else stringResource(R.string.transcribe_mic_off),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HarkText(
                        text = if (isStreaming) "Speak clearly to begin real-time transcription" else "Tap the mic on Stream to start",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        .padding(horizontal = 20.dp)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    SelectionContainer {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassSurface(RoundedCornerShape(24.dp), elevation = 4.dp)
                                .padding(20.dp)
                        ) {
                            Text(
                                text = transcription,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * 1.5).sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }

                    // Room to scroll the last lines clear of the action bar and navbar
                    Spacer(modifier = Modifier.height(220.dp))
                }
            }

            // Floating glass action bar, just above the navbar
            val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            AnimatedVisibility(
                visible = transcription.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 104.dp + navigationBarsPadding)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(visible = showCopyFeedback, enter = fadeIn(), exit = fadeOut()) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .glassSurface(RoundedCornerShape(12.dp), elevation = 6.dp)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Copied to clipboard",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    GlassActionBar(hazeState = hazeState) {
                        GlassActionButton(
                            icon = Icons.Rounded.ContentCopy,
                            label = "Copy",
                            hapticFeedbackEnabled = hapticFeedbackEnabled,
                            onClick = {
                                clipboardManager.setText(AnnotatedString(transcription))
                                showCopyFeedback = true
                            }
                        )
                        GlassActionButton(
                            icon = Icons.Rounded.Share,
                            label = "Share",
                            tint = MaterialTheme.colorScheme.tertiary,
                            hapticFeedbackEnabled = hapticFeedbackEnabled,
                            onClick = { onShareTranscription(transcription) }
                        )
                        GlassActionButton(
                            icon = Icons.Rounded.Delete,
                            label = "Clear",
                            tint = MaterialTheme.colorScheme.error,
                            hapticFeedbackEnabled = hapticFeedbackEnabled,
                            // Saves the transcript to History, then clears the screen
                            onClick = onClearTranscription
                        )
                    }
                }
            }
        }
    }
}
