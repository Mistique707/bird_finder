package com.example.birdfinder.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.birdfinder.R
import com.example.birdfinder.ui.theme.Brand
import kotlinx.coroutines.delay

private const val AUTO_ADVANCE_MS = 5000L

/**
 * Intro screen shown once on launch: app identity + a random bird fact. Auto-advances
 * after a few seconds, or tap anywhere to continue immediately.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val fact = remember { BirdFacts.random() }
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = AUTO_ADVANCE_MS.toInt(), easing = LinearEasing),
        label = "splashProgress",
    )

    LaunchedEffect(Unit) {
        delay(AUTO_ADVANCE_MS)
        onDone()
    }

    val bg = Brush.verticalGradient(
        listOf(Color(0xFF10283F), Color(0xFF1C466E), Color(0xFF2E6BA8)),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth(0.78f),
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_wordmark),
                    contentDescription = "Bird Finder",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                )
            }
            Text(
                "On-device bird call identification",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "DID YOU KNOW?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Brand.Glow,
                    )
                    Text(
                        fact,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Text(
                "Tap to continue",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            color = Brand.Glow,
            trackColor = Color.White.copy(alpha = 0.15f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 40.dp)
                .clip(RoundedCornerShape(50)),
        )
    }
}
