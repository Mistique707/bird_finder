package com.example.birdfinder.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared brand bits used to make the app feel lively and cohesive. */
object Brand {
    /** Accent blue used for highlights, the mic button, selected nav, badges (white text on it). */
    val SkyBlue = Color(0xFF2E6BA8)

    /** Brighter sky-blue used for the listening glow/animation. */
    val Glow = Color(0xFF3F86D6)
}

/** Subtle full-screen vertical gradient that sits behind every screen. */
@Composable
fun appBackgroundBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    // Keep the top tint light so headline text stays high-contrast in both modes.
    return Brush.verticalGradient(
        colors = listOf(
            cs.primaryContainer.copy(alpha = 0.22f),
            cs.background,
            cs.background,
        ),
    )
}

/** Translucent "glass" fill for cards layered over the gradient background. */
@Composable
fun glassColor(alpha: Float = 0.55f): Color = MaterialTheme.colorScheme.surface.copy(alpha = alpha)

/** Dark bottom-up scrim so text is readable over a photo. */
fun photoScrim(): Brush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color(0x00000000), Color(0xCC0A0A0A)),
)

/** A reusable rounded translucent card surface. */
@Composable
fun glassCardModifier(corner: Int = 20, alpha: Float = 0.55f): Modifier =
    Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(corner.dp))
        .background(glassColor(alpha))
