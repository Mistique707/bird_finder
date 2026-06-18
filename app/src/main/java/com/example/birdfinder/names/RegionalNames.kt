package com.example.birdfinder.names

import android.content.Context
import com.example.birdfinder.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Maps a species' scientific name to its name in the user-selected regional language,
 * loaded from the bundled `labels_<code>.txt` (same line order / format as English).
 * The scientific name is language-independent, so detections logged in English still
 * resolve to a localized name. Emits an empty map when no language is selected.
 */
class RegionalNames(
    context: Context,
    settings: Flow<Settings>,
    scope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    /** Currently selected language code ("" = English only). */
    val languageCode: StateFlow<String> = settings
        .map { it.regionalLanguage }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, "")

    val names: StateFlow<Map<String, String>> = languageCode
        .map { code -> if (code.isBlank()) emptyMap() else load(code) }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private suspend fun load(code: String): Map<String, String> = withContext(Dispatchers.IO) {
        runCatching {
            val map = HashMap<String, String>(6600)
            appContext.assets.open("labels_$code.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val t = line.trim()
                    if (t.isEmpty()) return@forEach
                    val ix = t.indexOf('_')
                    if (ix in 1 until t.lastIndex) {
                        map[t.substring(0, ix)] = t.substring(ix + 1)
                    }
                }
            }
            map
        }.getOrDefault(emptyMap())
    }
}
