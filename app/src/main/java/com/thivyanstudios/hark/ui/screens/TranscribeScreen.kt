package com.thivyanstudios.hark.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.ui.theme.HarkText

@Composable
fun TranscribeScreen(
    transcription: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopStart
    ) {
        val displayText = if (transcription.isEmpty()) {
            stringResource(R.string.transcribe_empty_text)
        } else {
            transcription
        }

        HarkText(
            text = displayText,
            modifier = Modifier.verticalScroll(rememberScrollState())
        )
    }
}
