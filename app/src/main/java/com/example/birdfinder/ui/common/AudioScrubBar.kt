package com.example.birdfinder.ui.common

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.birdfinder.ui.theme.Brand

/**
 * A tappable / draggable audio progress bar. The segment of the clip where the bird was
 * detected (the 3 s inference window, i.e. the end of the clip) is highlighted so the user
 * can see where to listen.
 *
 * @param positionMs current playback position.
 * @param durationMs total clip length (0 until known).
 * @param highlightStartMs start of the detected-bird region (null to hide highlight).
 * @param highlightEndMs end of the detected-bird region.
 * @param onSeek invoked with a target position in ms when the user taps/drags.
 */
@Composable
fun AudioScrubBar(
    positionMs: Int,
    durationMs: Int,
    highlightStartMs: Int?,
    highlightEndMs: Int?,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val highlightColor = Brand.Glow

    val safeDuration = durationMs.coerceAtLeast(1)
    val posFrac = (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val hlStartFrac = highlightStartMs?.let { (it.toFloat() / safeDuration).coerceIn(0f, 1f) }
    val hlEndFrac = highlightEndMs?.let { (it.toFloat() / safeDuration).coerceIn(0f, 1f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(safeDuration) {
                detectTapGestures { offset ->
                    onSeek(((offset.x / size.width) * safeDuration).toInt())
                }
            }
            .pointerInput(safeDuration) {
                detectHorizontalDragGestures { change, _ ->
                    val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeek((frac * safeDuration).toInt())
                }
            }
            .drawBehind {
                val barH = 10.dp.toPx()
                val top = (size.height - barH) / 2f
                val radius = CornerRadius(barH / 2f, barH / 2f)

                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, barH),
                    cornerRadius = radius,
                )
                if (hlStartFrac != null && hlEndFrac != null && hlEndFrac > hlStartFrac) {
                    val left = hlStartFrac * size.width
                    val w = (hlEndFrac - hlStartFrac) * size.width
                    drawRoundRect(
                        color = highlightColor.copy(alpha = 0.55f),
                        topLeft = Offset(left, top),
                        size = Size(w, barH),
                        cornerRadius = radius,
                    )
                }
                drawRoundRect(
                    color = progressColor,
                    topLeft = Offset(0f, top),
                    size = Size(posFrac * size.width, barH),
                    cornerRadius = radius,
                )
                drawCircle(
                    color = progressColor,
                    radius = barH,
                    center = Offset(posFrac * size.width, size.height / 2f),
                )
            },
    )
}

@Composable
fun AudioTimeRow(positionMs: Int, durationMs: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(formatMs(positionMs), style = MaterialTheme.typography.labelSmall)
        Box(Modifier.weight(1f))
        Text(formatMs(durationMs), style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
