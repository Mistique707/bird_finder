package com.example.birdfinder.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A soft, slightly translucent card.
 *
 * - **Light** mode: nearly opaque with a drop shadow and no border — shadows define the
 *   edges cleanly (a visible border read as ugly "thick margins").
 * - **Dark** mode: a more opaque raised surface with **no shadow and no border** — on a dark
 *   background a drop shadow reads as a muddy dark ring, so we use tonal elevation instead.
 *
 * `contentColor` is set explicitly to `onSurface` so text inside is always readable; passing
 * a custom (alpha-adjusted) container color otherwise leaves content at the default black.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    corner: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(corner)
    val color = cs.surface.copy(alpha = if (dark) 0.82f else 0.94f)
    val shadow = if (dark) 0.dp else 5.dp
    val tonal = if (dark) 4.dp else 0.dp

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = cs.onSurface,
            tonalElevation = tonal,
            shadowElevation = shadow,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = cs.onSurface,
            tonalElevation = tonal,
            shadowElevation = shadow,
            content = content,
        )
    }
}
