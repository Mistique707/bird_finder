package com.example.birdfinder.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.birdfinder.BuildConfig
import com.example.birdfinder.ui.common.GlassCard

private const val PRIVACY_URL =
    "https://github.com/your-repo/bird_finder/blob/main/PRIVACY.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("About & licenses") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Bird Finder ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "A personal, non-commercial bird-call identifier. This app is free and not " +
                    "monetised; it relies on the components below, each under its own license.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CreditCard(
                title = "BirdNET (identification model)",
                body = "Species identification uses the BirdNET GLOBAL 6K v2.4 TFLite model. " +
                    "© Cornell Lab of Ornithology & Chemnitz University of Technology. " +
                    "Licensed CC BY-NC-SA 4.0 — non-commercial use only.",
                links = listOf(
                    "BirdNET" to "https://birdnet.cornell.edu",
                    "License (CC BY-NC-SA 4.0)" to "https://creativecommons.org/licenses/by-nc-sa/4.0/",
                ),
                uri = uri,
            )
            CreditCard(
                title = "whoBIRD (model packaging)",
                body = "The TFLite models and labels are distributed by the whoBIRD project " +
                    "(GPLv3), used here only as the model source / technical reference. This app's " +
                    "own code is original.",
                links = listOf("whoBIRD" to "https://github.com/woheller69/whoBIRD"),
                uri = uri,
            )
            CreditCard(
                title = "Xeno-canto (reference calls)",
                body = "Reference recordings come from Xeno-canto. Each recording is © its " +
                    "recordist under its own Creative Commons license (mostly non-commercial); " +
                    "attribution is shown with each call.",
                links = listOf("Xeno-canto" to "https://xeno-canto.org"),
                uri = uri,
            )
            CreditCard(
                title = "Wikipedia / Wikimedia Commons (photos & text)",
                body = "Bird photos and descriptions come from Wikipedia and Wikimedia Commons, " +
                    "licensed CC BY-SA 4.0 by their respective authors.",
                links = listOf(
                    "Wikipedia" to "https://en.wikipedia.org",
                    "License (CC BY-SA 4.0)" to "https://creativecommons.org/licenses/by-sa/4.0/",
                ),
                uri = uri,
            )
            CreditCard(
                title = "OpenWeatherMap (weather)",
                body = "Optional weather enrichment is provided by OpenWeatherMap.",
                links = listOf("OpenWeatherMap" to "https://openweathermap.org"),
                uri = uri,
            )
            CreditCard(
                title = "Privacy",
                body = "Audio is processed on-device. Species names and your coordinates are sent " +
                    "to Wikipedia, Xeno-canto and (optionally) OpenWeatherMap only to fetch photos, " +
                    "calls and weather. See the privacy policy for details.",
                links = listOf("Privacy policy" to PRIVACY_URL),
                uri = uri,
            )

            Text(
                "Non-commercial use only, per the BirdNET model license.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CreditCard(
    title: String,
    body: String,
    links: List<Pair<String, String>>,
    uri: androidx.compose.ui.platform.UriHandler,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            links.forEach { (label, url) ->
                Text(
                    "$label ↗",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .clickable { uri.openUri(url) },
                )
            }
        }
    }
}
