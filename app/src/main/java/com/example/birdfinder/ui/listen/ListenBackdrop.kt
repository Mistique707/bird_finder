package com.example.birdfinder.ui.listen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.birdfinder.R

/**
 * The Listen screen's nature backdrop: a full-bleed landscape photo with two swallows
 * overlaid, positioned over the mountains like the design reference. The artwork is a light
 * scene, so the screen's foreground text uses dark colors in both light and dark mode.
 */
@Composable
fun ListenBackdrop(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight

        Image(
            painter = painterResource(R.drawable.bg_landscape),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Large swallow, low-left over the peaks (kept below the instruction card).
        Image(
            painter = painterResource(R.drawable.bird_swallow_1),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = w * 0.04f, y = h * 0.70f)
                .width(w * 0.28f),
            contentScale = ContentScale.FillWidth,
        )
        // Small swallow, low-right.
        Image(
            painter = painterResource(R.drawable.bird_swallow_2),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = w * 0.70f, y = h * 0.78f)
                .width(w * 0.12f),
            contentScale = ContentScale.FillWidth,
        )
    }
}
