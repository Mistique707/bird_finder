package com.example.birdfinder.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.birdfinder.audio.AudioDevices
import com.example.birdfinder.settings.RegionalLanguages
import com.example.birdfinder.settings.ThemeMode
import com.example.birdfinder.ui.common.GlassCard
import com.example.birdfinder.ui.theme.Brand
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAbout: () -> Unit = {},
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val current = s ?: return
    val context = LocalContext.current
    var confirmingClearAll by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.size(4.dp))
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // ---- Appearance ----
        Section("Appearance") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = current.themeMode == mode,
                        onClick = { vm.update { copy(themeMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        colors = limeSegmentedColors(),
                    ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }
        }

        // ---- Detection sensitivity ----
        Section("Detection") {
            Text(
                "Sensitivity",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                sensitivityLabel(current.confidenceThreshold),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = current.confidenceThreshold,
                onValueChange = { vm.update { copy(confidenceThreshold = it) } },
                valueRange = 0.3f..0.95f,
            )
            Spacer(Modifier.size(4.dp))
            Text("Recording length", style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(3.0, 6.0, 9.0)
                options.forEachIndexed { i, secs ->
                    SegmentedButton(
                        selected = current.clipSeconds.roundToInt() == secs.roundToInt(),
                        onClick = { vm.update { copy(clipSeconds = secs) } },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        colors = limeSegmentedColors(),
                    ) { Text("${secs.toInt()}s") }
                }
            }
            Text(
                "Saved clip length. Identification always analyses 3-second windows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(4.dp))
            Text("Ignore repeats of the same bird", style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(0 to "Off", 60 to "1 min", 180 to "3 min", 300 to "5 min")
                options.forEachIndexed { i, (secs, label) ->
                    SegmentedButton(
                        selected = current.dedupeWindowSeconds == secs,
                        onClick = { vm.update { copy(dedupeWindowSeconds = secs) } },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        colors = limeSegmentedColors(),
                    ) { Text(label) }
                }
            }
            Text(
                "Keeps the log tidy when a bird calls continuously.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Microphone ----
        Section("Microphone") {
            Text("Audio input", style = MaterialTheme.typography.bodyLarge)
            var micMenu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { micMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Text("  " + current.audioInputName.ifBlank { "Automatic (default mic)" })
                }
                DropdownMenu(expanded = micMenu, onDismissRequest = { micMenu = false }) {
                    AudioDevices.list(context).forEach { dev ->
                        DropdownMenuItem(
                            text = { Text(dev.label) },
                            onClick = {
                                vm.update {
                                    copy(
                                        audioInputId = dev.id,
                                        audioInputName = if (dev.id == 0) "" else dev.label,
                                    )
                                }
                                micMenu = false
                            },
                        )
                    }
                }
            }
            Text(
                "Route capture through an external mic (e.g. a USB-C shotgun mic). " +
                    "Plug it in first, then pick it here. Applies next time you start listening.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Identification media ----
        Section("Identification") {
            SwitchRow("Show bird photos", current.showBirdImages) {
                vm.update { copy(showBirdImages = it) }
            }
            SwitchRow("Reference calls to compare", current.referenceCallsEnabled) {
                vm.update { copy(referenceCallsEnabled = it) }
            }

            Text("Also show names in", style = MaterialTheme.typography.bodyLarge)
            var langMenu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { langMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Language, contentDescription = null)
                    Text("  " + RegionalLanguages.displayFor(current.regionalLanguage))
                }
                DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                    RegionalLanguages.options.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                vm.update { copy(regionalLanguage = code) }
                                langMenu = false
                            },
                        )
                    }
                }
            }

            Text(
                "Photos come from Wikipedia (no key). Reference calls come from Xeno-canto, " +
                    "which now needs a free API key — add it under Advanced. Regional names " +
                    "are shown alongside the English name where a translation exists.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Location ----
        Section("Location") {
            SwitchRow("Use device GPS", current.useDeviceGps) {
                vm.update { copy(useDeviceGps = it) }
            }
            Text(
                if (current.useDeviceGps)
                    "Each detection uses your phone's GPS. Override per-recording from its detail screen."
                else "Using the manual coordinates set in Advanced.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Data ----
        Section("Data") {
            OutlinedButton(
                onClick = {
                    vm.exportCsv(context) { n ->
                        Toast.makeText(
                            context,
                            if (n == 0) "No detections to export" else "Exported $n detections",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text("  Export CSV (share)")
            }
            Button(
                onClick = { confirmingClearAll = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null)
                Text("  Clear all detections")
            }
        }

        // ---- About ----
        OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Info, contentDescription = null)
            Text("  About & licenses")
        }

        // ---- Advanced (collapsed) ----
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { advancedOpen = !advancedOpen },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Advanced", style = MaterialTheme.typography.titleMedium)
                    Icon(
                        if (advancedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
                AnimatedVisibility(visible = advancedOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(Modifier.size(4.dp))
                        SwitchRow("Location/season prior (BirdNET meta)", current.useMetaModel) {
                            vm.update { copy(useMetaModel = it) }
                        }
                        DoubleField("Window step (seconds)", current.stepSeconds) {
                            vm.update { copy(stepSeconds = it) }
                        }
                        Text("Manual coordinates (fallback)", style = MaterialTheme.typography.bodyMedium)
                        DoubleField("Latitude", current.fallbackLatitude) {
                            vm.update { copy(fallbackLatitude = it) }
                        }
                        DoubleField("Longitude", current.fallbackLongitude) {
                            vm.update { copy(fallbackLongitude = it) }
                        }
                        SwitchRow("Weather (OpenWeatherMap)", current.owmEnabled) {
                            vm.update { copy(owmEnabled = it) }
                        }
                        OutlinedTextField(
                            value = current.owmApiKey,
                            onValueChange = { vm.update { copy(owmApiKey = it) } },
                            label = { Text("OpenWeatherMap API key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = current.xenoCantoApiKey,
                            onValueChange = { vm.update { copy(xenoCantoApiKey = it) } },
                            label = { Text("Xeno-canto API key (for reference calls)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Get a free Xeno-canto key at xeno-canto.org/account. Leave blank to " +
                                "use a key baked in via local.properties.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Raw confidence threshold: %.2f".format(current.confidenceThreshold),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.size(110.dp))
    }

    if (confirmingClearAll) {
        AlertDialog(
            onDismissRequest = { confirmingClearAll = false },
            title = { Text("Clear ALL detections?") },
            text = {
                Text(
                    "This permanently deletes every detection row AND every saved clip. There is no undo.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmingClearAll = false
                        vm.clearAll { n ->
                            Toast.makeText(context, "Deleted $n detections", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { confirmingClearAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { content() }
        }
    }
}

@Composable
private fun limeSegmentedColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = Brand.SkyBlue,
    activeContentColor = Color.White,
    inactiveContainerColor = Color.Transparent,
)

@Composable
private fun DoubleField(label: String, value: Double, onChange: (Double) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { it.toDoubleOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

private fun sensitivityLabel(threshold: Float): String = when {
    threshold >= 0.85f -> "Strict — only very confident matches (${"%.2f".format(threshold)})"
    threshold >= 0.6f -> "Balanced — recommended (${"%.2f".format(threshold)})"
    else -> "Lenient — more matches, more false positives (${"%.2f".format(threshold)})"
}
