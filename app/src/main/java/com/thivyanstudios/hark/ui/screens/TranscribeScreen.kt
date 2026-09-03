package com.thivyanstudios.hark.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.thivyanstudios.hark.ui.theme.HarkText

@Composable
fun TranscribeScreen(
    isStreaming: Boolean,
    transcription: String,
    onClearTranscription: () -> Unit,
    onShareTranscription: (String) -> Unit,
    fontSize: Float = 22f
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    var showCopyFeedback by remember { mutableStateOf(false) }

    // Automatically scroll to the bottom when new text arrives
    LaunchedEffect(transcription) {
        if (transcription.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Scaffold(
        topBar = {
            if (isStreaming) {
                TranscriptionHeader()
            }
        },
        floatingActionButton = {
            if (transcription.isNotEmpty()) {
                val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Column(
                    modifier = Modifier.padding(bottom = 100.dp + navigationBarsPadding), // Lift FABs above the floating navbar
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedVisibility(
                        visible = showCopyFeedback,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            tonalElevation = 4.dp
                        ) {
                            Text(
                                "Copied!",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    
                    FloatingActionButton(
                        onClick = onClearTranscription,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear transcription")
                    }

                    FloatingActionButton(
                        onClick = { onShareTranscription(transcription) },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share transcription")
                    }

                    FloatingActionButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(transcription))
                            showCopyFeedback = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy text")
                    }
                }
            }
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
                    val icon = Icons.Rounded.RecordVoiceOver
                    val title = if (isStreaming) stringResource(R.string.transcribe_empty_text) else stringResource(R.string.transcribe_mic_off)
                    val subtitle = if (isStreaming) "Speak clearly to begin real-time transcription" else "Tap the mic on Stream to start"

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    HarkText(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HarkText(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    SelectionContainer {
                        Column {
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
                    
                    Spacer(modifier = Modifier.height(180.dp)) // Leave room for navbar and FAB
                }
            }

            // Subtle gradient at the top to indicate more content/scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.background, Color.Transparent)
                        )
                    )
            )
        }
    }

    if (showCopyFeedback) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            showCopyFeedback = false
        }
    }
}

@Composable
fun TranscriptionHeader() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Static, calm indicator instead of a jumping bar
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "Live Transcription",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            BetaBadge()
        }
    }
}

