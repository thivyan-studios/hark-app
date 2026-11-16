package com.thivyanstudios.hark.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.thivyanstudios.hark.ui.theme.HarkText
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
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
                actions = {
                    if (transcription.isNotEmpty()) {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(transcription))
                            showCopyFeedback = true
                        }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy text",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onShareTranscription(transcription) }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share transcription",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onClearTranscription) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Clear transcription",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (transcription.isNotEmpty()) {
                val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Column(
                    modifier = Modifier.padding(bottom = 90.dp + navigationBarsPadding),
                    horizontalAlignment = Alignment.End
                ) {
                    AnimatedVisibility(
                        visible = showCopyFeedback,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 6.dp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                "Copied to clipboard",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(transcription))
                            showCopyFeedback = true
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp)
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
                        .padding(horizontal = 20.dp)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        SelectionContainer {
                            Box(modifier = Modifier.padding(20.dp)) {
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
                    }
                    
                    Spacer(modifier = Modifier.height(160.dp))
                }
            }
        }
    }
}
