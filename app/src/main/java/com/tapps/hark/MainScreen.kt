package com.tapps.hark

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

@Composable
fun MainScreen(
    isStreaming: Boolean,
    versionName: String,
    onStreamButtonClick: () -> Unit
) {
    // This Box is similar to your ConstraintLayout, allowing layers and alignment.
    Box(
        modifier = Modifier
            .fillMaxSize() // This is like layout_width/height="match_parent"
            .padding(16.dp) // Optional: Add some padding
    ) {
        // This is your stream button (ImageView)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp) // Sets a fixed size for the image
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .align(Alignment.Center) // Centers the image in the Box
                .clickable { onStreamButtonClick() } // Makes the image clickable
        ) {
            Image(
                painter = if (isStreaming) {
                    painterResource(id = R.drawable.ic_mic_on)
                } else {
                    painterResource(id = R.drawable.ic_mic_off)
                },
                contentDescription = "Button to stream",
                modifier = Modifier.size(100.dp)
            )
        }

        // This is your trademark TextView
        Text(
            text = versionName,
            color = if (isSystemInDarkTheme()) {
                Color.White
            } else {
                Color.Black
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter) // Aligns the text to the bottom center of the Box
                .padding(bottom = 32.dp) // Adds some space from the absolute bottom
        )
    }
}

// This is a Preview function so you can see your UI in Android Studio without running the app.
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MainScreen(
        isStreaming = false,
        versionName = "Developed by Thivyan Pillay (Pre-release v1.0)",
        onStreamButtonClick = {}
    )
}
