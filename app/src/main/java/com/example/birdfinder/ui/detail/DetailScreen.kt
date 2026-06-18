package com.example.birdfinder.ui.detail

import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.birdfinder.ui.common.AudioScrubBar
import com.example.birdfinder.ui.common.AudioTimeRow
import com.example.birdfinder.ui.common.BirdHeroImage
import com.example.birdfinder.ui.common.GlassCard
import com.example.birdfinder.ui.common.rememberLocalName
import com.example.birdfinder.ui.theme.Brand
import com.example.birdfinder.ui.theme.photoScrim
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Length of the BirdNET inference window (the detected-bird region at the clip's end). */
private const val INFERENCE_MS = 3000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    detectionId: Long,
    onBack: () -> Unit,
    vm: DetailViewModel = viewModel(factory = DetailViewModel.Factory),
) {
    val row by vm.state.collectAsStateWithLifecycle()
    val refCall by vm.refCall.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle(initialValue = vm.defaultSettings)
    val preparingShare by vm.preparingShare.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editing by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // Dedicated players: the local recording (scrubbable) and the streamed reference.
    val clipPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    var clipPlaying by remember { mutableStateOf(false) }
    var clipPositionMs by remember { mutableIntStateOf(0) }
    var clipDurationMs by remember { mutableIntStateOf(0) }

    val refPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    var refPlaying by remember { mutableStateOf(false) }
    var refPreparing by remember { mutableStateOf(false) }

    LaunchedEffect(detectionId) { vm.load(detectionId) }

    LaunchedEffect(row?.id, settings.referenceCallsEnabled) {
        val r = row
        if (r != null && settings.referenceCallsEnabled) {
            vm.loadReferenceCall(r.speciesScientific, r.speciesCommon)
        }
    }

    // Read clip duration up-front (without playing) so the bar + highlight are correct.
    LaunchedEffect(row?.clipPath) {
        val r = row ?: return@LaunchedEffect
        val f = File(context.filesDir, r.clipPath)
        clipDurationMs = if (f.isFile) withContext(Dispatchers.IO) { durationOf(f) } else 0
    }

    // Poll playback position while the clip is playing.
    LaunchedEffect(clipPlaying) {
        while (clipPlaying) {
            clipPlayer.value?.let { clipPositionMs = it.currentPosition }
            delay(50)
        }
    }

    fun stopRef() {
        refPlayer.value?.release()
        refPlayer.value = null
        refPlaying = false
        refPreparing = false
    }

    fun ensureClipPlayer(r: com.example.birdfinder.data.db.DetectionEntity): MediaPlayer {
        clipPlayer.value?.let { return it }
        val mp = MediaPlayer().apply {
            setDataSource(File(context.filesDir, r.clipPath).absolutePath)
            setOnCompletionListener {
                clipPlaying = false
                clipPositionMs = clipDurationMs
            }
            prepare()
        }
        if (clipDurationMs <= 0) clipDurationMs = mp.duration
        clipPlayer.value = mp
        return mp
    }

    fun toggleClip(r: com.example.birdfinder.data.db.DetectionEntity) {
        stopRef()
        val mp = ensureClipPlayer(r)
        if (clipPlaying) {
            mp.pause()
            clipPlaying = false
        } else {
            if (clipPositionMs >= clipDurationMs - 20) mp.seekTo(0)
            mp.start()
            clipPlaying = true
        }
    }

    fun seekClip(r: com.example.birdfinder.data.db.DetectionEntity, ms: Int) {
        val mp = ensureClipPlayer(r)
        mp.seekTo(ms.coerceIn(0, clipDurationMs))
        clipPositionMs = ms.coerceIn(0, clipDurationMs)
    }

    fun toggleRef(url: String) {
        if (refPreparing || refPlaying) {
            stopRef()
            return
        }
        clipPlayer.value?.pause()
        clipPlaying = false
        refPreparing = true
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            setDataSource(url)
            setOnPreparedListener {
                refPreparing = false
                refPlaying = true
                it.start()
            }
            setOnCompletionListener { refPlaying = false }
            setOnErrorListener { _, _, _ ->
                refPreparing = false
                refPlaying = false
                true
            }
            prepareAsync()
        }
        refPlayer.value = mp
    }

    DisposableEffect(detectionId) {
        onDispose {
            clipPlayer.value?.release(); clipPlayer.value = null
            refPlayer.value?.release(); refPlayer.value = null
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Detection") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    row?.let {
                        if (preparingShare) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp).padding(end = 4.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(onClick = { vm.shareCard(context) }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share card")
                            }
                        }
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { inner ->
        val r = row
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            ) {
                BirdHeroImage(
                    scientific = r.speciesScientific,
                    common = r.speciesCommon,
                    enabled = settings.showBirdImages,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(photoScrim()))
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                ) {
                    Text(
                        r.speciesCommon,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    rememberLocalName(r.speciesScientific, r.speciesCommon)?.let { local ->
                        Text(
                            local,
                            style = MaterialTheme.typography.titleMedium,
                            color = Brand.SkyBlue,
                        )
                    }
                    Text(
                        r.speciesScientific,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = Brand.SkyBlue) {
                        Text(
                            "%.0f%% confidence".format(r.confidence * 100f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ---- About this bird ----
                info?.let { text ->
                    SectionTitle("About this bird")
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Source: Wikipedia",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ---- Your recording (scrubbable) ----
                SectionTitle("Your recording")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { toggleClip(r) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (clipPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (clipPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        // Post-roll centres the call, so highlight the middle 3 s of the clip.
                        val hlStart = ((clipDurationMs - INFERENCE_MS) / 2).coerceAtLeast(0)
                        val hlEnd = hlStart + INFERENCE_MS
                        val showHl = clipDurationMs > INFERENCE_MS + 400
                        AudioScrubBar(
                            positionMs = clipPositionMs,
                            durationMs = clipDurationMs,
                            highlightStartMs = if (showHl) hlStart else null,
                            highlightEndMs = if (showHl) hlEnd else null,
                            onSeek = { seekClip(r, it) },
                        )
                        AudioTimeRow(clipPositionMs, clipDurationMs)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(12.dp).background(Brand.Glow.copy(alpha = 0.55f), CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "highlighted = where the bird was detected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ---- Reference call ----
                SectionTitle("Reference call")
                ReferenceCallControl(
                    state = refCall,
                    enabled = settings.referenceCallsEnabled,
                    preparing = refPreparing,
                    playing = refPlaying,
                    onToggle = { url -> toggleRef(url) },
                )

                // ---- Details ----
                SectionTitle("Details")
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetaLine("Recorded", formatInstant(r.timestampUtc))
                        val coords = if (r.latitude != null && r.longitude != null)
                            "%.5f, %.5f".format(r.latitude, r.longitude) else "—"
                        MetaLine("Location", coords)
                        val tempC = r.weatherTempC?.let { "%.1f °C".format(it) } ?: "—"
                        MetaLine("Weather", "${r.weatherCondition ?: "—"} ($tempC)")
                        MetaLine("Model", "${r.modelName} ${r.modelVersion}")
                    }
                }

                OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text("  Edit location")
                }

                val uriHandler = LocalUriHandler.current
                Text(
                    "Photo & info from Wikipedia (CC BY-SA) ↗",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable {
                        uriHandler.openUri(
                            "https://en.wikipedia.org/wiki/" + r.speciesScientific.trim().replace(' ', '_'),
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (editing) {
            EditLocationDialog(
                initialLat = r.latitude,
                initialLon = r.longitude,
                onCancel = { editing = false },
                onSave = { lat, lon ->
                    vm.updateLocation(r.id, lat, lon)
                    editing = false
                },
            )
        }
        if (confirmingDelete) {
            AlertDialog(
                onDismissRequest = { confirmingDelete = false },
                title = { Text("Delete this detection?") },
                text = {
                    Text(
                        "Removes the row and the saved clip (if no other detection shares it). " +
                            "This cannot be undone.",
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            confirmingDelete = false
                            clipPlayer.value?.release(); clipPlayer.value = null
                            stopRef()
                            vm.delete(r.id) { onBack() }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun ReferenceCallControl(
    state: RefCallState,
    enabled: Boolean,
    preparing: Boolean,
    playing: Boolean,
    onToggle: (String) -> Unit,
) {
    when {
        !enabled -> AudioButtonDisabled("Reference call off (enable in Settings)")
        state is RefCallState.NeedsKey -> AudioButtonDisabled("Add a Xeno-canto key in Settings → Advanced")
        state is RefCallState.Loading -> AudioButtonLoading("Finding reference call…")
        state is RefCallState.Unavailable -> AudioButtonDisabled("No reference call found")
        state is RefCallState.Ready -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val typeLabel = state.call.type
                    ?.takeIf { it.isNotBlank() }
                    ?.replaceFirstChar { it.uppercase() }
                if (typeLabel != null) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Brand.SkyBlue) {
                        Text(
                            typeLabel.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
                OutlinedButton(onClick = { onToggle(state.call.audioUrl) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (playing) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                    )
                    Text(
                        when {
                            preparing -> "  Buffering reference…"
                            playing -> "  Stop reference call"
                            else -> "  Play reference call"
                        },
                    )
                }
                Text(
                    buildString {
                        append(state.call.source)
                        state.call.recordist?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        else -> AudioButtonLoading("Finding reference call…")
    }
}

@Composable
private fun AudioButtonLoading(label: String) {
    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text("  $label")
    }
}

@Composable
private fun AudioButtonDisabled(label: String) {
    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
        Text("  $label")
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EditLocationDialog(
    initialLat: Double?,
    initialLon: Double?,
    onCancel: () -> Unit,
    onSave: (Double?, Double?) -> Unit,
) {
    var lat by remember { mutableStateOf(initialLat?.toString() ?: "") }
    var lon by remember { mutableStateOf(initialLon?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Edit location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("Latitude") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = lon,
                    onValueChange = { lon = it },
                    label = { Text("Longitude") },
                    singleLine = true,
                )
                Text("Leave both blank to clear the location.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(lat.toDoubleOrNull(), lon.toDoubleOrNull()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(86.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Read a clip's duration in ms without starting playback. */
private fun durationOf(file: File): Int {
    val mmr = MediaMetadataRetriever()
    return try {
        mmr.setDataSource(file.absolutePath)
        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
    } catch (_: Throwable) {
        0
    } finally {
        mmr.release()
    }
}

private val detailFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault())

private fun formatInstant(epochMillis: Long): String =
    detailFormatter.format(Instant.ofEpochMilli(epochMillis))
