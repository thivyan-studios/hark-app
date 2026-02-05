package com.thivyanstudios.hark.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.ui.theme.SquishyBox
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    isStreaming: Boolean,
    onStreamButtonClick: () -> Unit,
    hapticFeedbackEnabled: Boolean,
) {
    var isButtonEnabled by remember { mutableStateOf(true) }
    
    LaunchedEffect(isStreaming) {
        // Re-enable button immediately when streaming state changes
        isButtonEnabled = true
    }

    LaunchedEffect(isButtonEnabled) {
        if (!isButtonEnabled) {
            // Safety timeout to re-enable button if something goes wrong
            delay(5000L)
            isButtonEnabled = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SquishyBox(
            onClick = {
                if (isButtonEnabled) {
                    isButtonEnabled = false
                    onStreamButtonClick()
                }
            },
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.Center),
            // QC: Removed disabledBackgroundColor (Gray) to prevent "laggy" visual transition.
            // The button will now stay Red or Green while transitioning, providing a snappier feel.
            backgroundColor = if (isStreaming) Color(0xFF4B5320) else Color.Red,
            enabled = isButtonEnabled,
            hapticFeedbackEnabled = hapticFeedbackEnabled
        ) {
            Image(
                painter = if (isStreaming) {
                    painterResource(id = R.drawable.ic_mic_on)
                } else {
                    painterResource(id = R.drawable.ic_mic_off)
                },
                contentDescription = stringResource(R.string.cd_stream_button),
                modifier = Modifier.size(100.dp)
            )
        }
    }
}
