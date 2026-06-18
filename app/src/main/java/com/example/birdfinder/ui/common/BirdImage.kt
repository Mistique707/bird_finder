package com.example.birdfinder.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.birdfinder.BirdFinderApp

/**
 * Resolves a species photo URL from the app's [com.example.birdfinder.media.BirdMediaClient],
 * off the main thread, cached. Returns null until resolved or if none exists.
 */
@Composable
fun rememberBirdImageUrl(scientific: String, common: String, enabled: Boolean): String? {
    if (!enabled) return null
    val app = LocalContext.current.applicationContext as BirdFinderApp
    return produceState<String?>(initialValue = null, scientific, common, enabled) {
        value = runCatching { app.media.imageUrl(scientific, common) }.getOrNull()
    }.value
}

/** Small rounded thumbnail for list rows. Falls back to a placeholder icon. */
@Composable
fun BirdThumbnail(
    scientific: String,
    common: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    size: Int = 56,
) {
    val url = rememberBirdImageUrl(scientific, common, enabled)
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = common,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Large hero image for the detail screen. */
@Composable
fun BirdHeroImage(
    scientific: String,
    common: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val url = rememberBirdImageUrl(scientific, common, enabled)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = common,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}
