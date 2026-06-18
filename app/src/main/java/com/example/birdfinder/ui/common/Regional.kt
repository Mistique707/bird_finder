package com.example.birdfinder.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.birdfinder.BirdFinderApp
import com.example.birdfinder.settings.RegionalLanguages

/** Current scientific-name → regional-name map for bundled languages (empty when none). */
@Composable
fun rememberRegionalNames(): Map<String, String> {
    val app = LocalContext.current.applicationContext as BirdFinderApp
    val map by app.regionalNames.names.collectAsStateWithLifecycle()
    return map
}

/**
 * Regional name for a species, or null if it just duplicates the English common name.
 */
fun regionalNameFor(
    map: Map<String, String>,
    scientific: String,
    englishCommon: String,
): String? {
    val local = map[scientific.trim()] ?: return null
    return local.takeIf { it.isNotBlank() && !it.equals(englishCommon, ignoreCase = true) }
}

/**
 * Resolves a species' name in the selected regional language. Bundled languages resolve
 * instantly from the in-memory map; languages without a bundled label file (the extra
 * Indian languages) are fetched live from Wikipedia and cached, so this may return null at
 * first and then update once resolved.
 */
@Composable
fun rememberLocalName(scientific: String, englishCommon: String): String? {
    val app = LocalContext.current.applicationContext as BirdFinderApp
    val code by app.regionalNames.languageCode.collectAsStateWithLifecycle()
    if (code.isBlank()) return null

    val bundled = rememberRegionalNames()
    regionalNameFor(bundled, scientific, englishCommon)?.let { return it }

    if (!RegionalLanguages.isLive(code)) return null
    val resolved by produceState<String?>(initialValue = null, scientific, code) {
        value = runCatching { app.media.localName(scientific, englishCommon, code) }
            .getOrNull()
            ?.takeIf { !it.equals(englishCommon, ignoreCase = true) }
    }
    return resolved
}
