package com.example.birdfinder.ui.listen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.birdfinder.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.birdfinder.classify.Detection
import com.example.birdfinder.pipeline.SavedDetection
import com.example.birdfinder.ui.common.BirdThumbnail
import com.example.birdfinder.ui.common.GlassCard
import com.example.birdfinder.ui.common.rememberLocalName
import com.example.birdfinder.ui.theme.Brand
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Dark text color for elements drawn directly over the light landscape backdrop. */
private val OnScene = Color(0xFF15293A)

@Composable
fun ListenScreen(
    onOpenDetail: (Long) -> Unit,
    vm: ListenViewModel = viewModel(factory = ListenViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle(initialValue = vm.defaultSettings)
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) vm.start()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ListenBackdrop(modifier = Modifier.fillMaxSize())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))
        Image(
            painter = painterResource(R.drawable.logo_wordmark),
            contentDescription = "Bird Finder",
            modifier = Modifier.height(42.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Every bird has a voice. Let's discover who's calling.",
            style = MaterialTheme.typography.bodyLarge,
            color = OnScene,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))
        MicButton(
            running = state.running,
            level = state.rms,
            onStart = {
                permLauncher.launch(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ),
                )
            },
            onStop = { vm.stop() },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(12.dp))
        StatusLine(
            running = state.running,
            accuracyM = state.locationAccuracyM,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Error: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Instruction tab: slides away while recording, slides back to fill the gap when stopped.
        AnimatedVisibility(
            visible = !state.running,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            InstructionCard(modifier = Modifier.padding(top = 16.dp))
        }

        AnimatedVisibility(visible = state.recentDetections.isNotEmpty()) {
            Column {
                Spacer(Modifier.height(20.dp))
                SectionLabel("Now hearing")
                Spacer(Modifier.height(8.dp))
                state.recentDetections.forEach { d -> LiveRow(d) }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Recently logged")
        Spacer(Modifier.height(8.dp))
        if (state.recentlySaved.isEmpty()) {
            Text(
                if (state.running) "Listening… logged birds will appear here."
                else "Press the mic to start. Detected birds get logged with photo, time and place.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnScene,
                textAlign = TextAlign.Center,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 110.dp),
            ) {
                items(state.recentlySaved, key = { it.id }) { saved ->
                    SavedCard(
                        saved = saved,
                        showImages = settings.showBirdImages,
                        modifier = Modifier.animateItem(),
                        onClick = { onOpenDetail(saved.id) },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    }
}

@Composable
private fun InstructionCard(modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "HOW IT WORKS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            InstructionStep("Listen to Nature", "Point your phone toward the sounds around you.")
            InstructionStep("Find the Bird", "Bird Finder identifies likely species from their calls.")
            InstructionStep("Build Your Bird Log", "Keep a record of each discovery with photos, location, and date.")
        }
    }
}

@Composable
private fun InstructionStep(title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MicButton(
    running: Boolean,
    level: Float,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(220.dp)) {
        // Soft static glow ring for life even when idle.
        Box(
            Modifier
                .size(186.dp)
                .alpha(0.14f)
                .background(Brand.Glow, CircleShape),
        )
        // The animated rings (and the infinite transition driving them) only exist while
        // listening — otherwise the transition recomposes every frame and causes idle jank.
        if (running) {
            PulseRings(level = level)
        }
        Surface(
            onClick = { if (running) onStop() else onStart() },
            shape = CircleShape,
            color = if (running) MaterialTheme.colorScheme.error else Brand.SkyBlue,
            shadowElevation = 12.dp,
            modifier = Modifier.size(140.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (running) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (running) "Stop" else "Start listening",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
private fun PulseRings(level: Float) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "pulseScale",
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "pulseAlpha",
    )
    val levelFrac = (kotlin.math.ln(1.0 + level.toDouble()) / kotlin.math.ln(33000.0))
        .toFloat().coerceIn(0f, 1f)

    Box(
        Modifier
            .size(160.dp)
            .scale(pulse)
            .alpha(pulseAlpha)
            .background(Brand.Glow, CircleShape),
    )
    Box(
        Modifier
            .size((150 + levelFrac * 60).dp)
            .alpha(0.24f)
            .background(Brand.Glow, CircleShape),
    )
}

@Composable
private fun StatusLine(running: Boolean, accuracyM: Float?, modifier: Modifier = Modifier) {
    val text = when {
        running && accuracyM != null -> "Listening · GPS ±${accuracyM.toInt()} m"
        running -> "Listening…"
        else -> "Idle"
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (running) Color(0xFF1B466E) else OnScene,
        modifier = modifier,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = OnScene,
    )
}

@Composable
private fun LiveRow(d: Detection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(d.speciesCommon, style = MaterialTheme.typography.titleMedium, color = OnScene)
            Text(
                d.speciesScientific,
                style = MaterialTheme.typography.bodySmall,
                color = OnScene.copy(alpha = 0.7f),
            )
        }
        ConfidenceBadge(d.confidence)
    }
}

@Composable
private fun SavedCard(
    saved: SavedDetection,
    showImages: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val regionalName = rememberLocalName(saved.speciesScientific, saved.speciesCommon)
    GlassCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BirdThumbnail(
                scientific = saved.speciesScientific,
                common = saved.speciesCommon,
                enabled = showImages,
                size = 60,
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(saved.speciesCommon, style = MaterialTheme.typography.titleMedium)
                if (regionalName != null) {
                    Text(
                        regionalName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    saved.speciesScientific,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val tempC = saved.weatherTempC?.let { " · %.0f°C".format(it) } ?: ""
                Text(
                    formatInstant(saved.timestampUtc) + tempC,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            ConfidenceBadge(saved.confidence)
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: Float) {
    val high = confidence >= 0.85f
    val color = when {
        high -> Brand.SkyBlue
        confidence >= 0.7f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Surface(shape = RoundedCornerShape(12.dp), color = color) {
        Text(
            "%.0f%%".format(confidence * 100f),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private val listenFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun formatInstant(epochMillis: Long): String =
    listenFormatter.format(Instant.ofEpochMilli(epochMillis))
