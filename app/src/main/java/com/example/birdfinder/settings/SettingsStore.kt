package com.example.birdfinder.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Persistent app settings backed by DataStore Preferences. Defaults target Pune, India
 * (fallback only) and BirdNET-friendly timing: 6 s saved clips classified on 3 s windows
 * with a 1.5 s hop.
 */
class SettingsStore(context: Context) {

    private val ds = context.applicationContext.dataStore

    val state: Flow<Settings> = ds.data.map { it.toSettings() }

    suspend fun update(transform: Settings.() -> Settings) {
        ds.edit { prefs ->
            val next = prefs.toSettings().transform()
            prefs[Keys.threshold] = next.confidenceThreshold
            prefs[Keys.lat] = next.fallbackLatitude
            prefs[Keys.lon] = next.fallbackLongitude
            prefs[Keys.clipSec] = next.clipSeconds
            prefs[Keys.stepSec] = next.stepSeconds
            prefs[Keys.useDeviceGps] = next.useDeviceGps
            prefs[Keys.useMetaModel] = next.useMetaModel
            prefs[Keys.owmEnabled] = next.owmEnabled
            prefs[Keys.owmKey] = next.owmApiKey
            prefs[Keys.themeMode] = next.themeMode.name
            prefs[Keys.showBirdImages] = next.showBirdImages
            prefs[Keys.referenceCalls] = next.referenceCallsEnabled
            prefs[Keys.xenoKey] = next.xenoCantoApiKey
            prefs[Keys.dedupeSec] = next.dedupeWindowSeconds
            prefs[Keys.regionalLang] = next.regionalLanguage
            prefs[Keys.audioInputId] = next.audioInputId
            prefs[Keys.audioInputName] = next.audioInputName
        }
    }

    private fun Preferences.toSettings() = Settings(
        confidenceThreshold = this[Keys.threshold] ?: DEFAULT.confidenceThreshold,
        fallbackLatitude = this[Keys.lat] ?: DEFAULT.fallbackLatitude,
        fallbackLongitude = this[Keys.lon] ?: DEFAULT.fallbackLongitude,
        clipSeconds = this[Keys.clipSec] ?: DEFAULT.clipSeconds,
        stepSeconds = this[Keys.stepSec] ?: DEFAULT.stepSeconds,
        useDeviceGps = this[Keys.useDeviceGps] ?: DEFAULT.useDeviceGps,
        useMetaModel = this[Keys.useMetaModel] ?: DEFAULT.useMetaModel,
        owmEnabled = this[Keys.owmEnabled] ?: DEFAULT.owmEnabled,
        owmApiKey = this[Keys.owmKey] ?: DEFAULT.owmApiKey,
        themeMode = runCatching { ThemeMode.valueOf(this[Keys.themeMode] ?: DEFAULT.themeMode.name) }
            .getOrDefault(DEFAULT.themeMode),
        showBirdImages = this[Keys.showBirdImages] ?: DEFAULT.showBirdImages,
        referenceCallsEnabled = this[Keys.referenceCalls] ?: DEFAULT.referenceCallsEnabled,
        xenoCantoApiKey = this[Keys.xenoKey] ?: DEFAULT.xenoCantoApiKey,
        dedupeWindowSeconds = this[Keys.dedupeSec] ?: DEFAULT.dedupeWindowSeconds,
        regionalLanguage = this[Keys.regionalLang] ?: DEFAULT.regionalLanguage,
        audioInputId = this[Keys.audioInputId] ?: DEFAULT.audioInputId,
        audioInputName = this[Keys.audioInputName] ?: DEFAULT.audioInputName,
    )

    private object Keys {
        val threshold = floatPreferencesKey("confidence_threshold")
        val lat = doublePreferencesKey("latitude")
        val lon = doublePreferencesKey("longitude")
        val clipSec = doublePreferencesKey("clip_seconds")
        val stepSec = doublePreferencesKey("step_seconds")
        val useDeviceGps = booleanPreferencesKey("use_device_gps")
        val useMetaModel = booleanPreferencesKey("use_meta_model")
        val owmEnabled = booleanPreferencesKey("owm_enabled")
        val owmKey = stringPreferencesKey("owm_api_key")
        val themeMode = stringPreferencesKey("theme_mode")
        val showBirdImages = booleanPreferencesKey("show_bird_images")
        val referenceCalls = booleanPreferencesKey("reference_calls_enabled")
        val xenoKey = stringPreferencesKey("xeno_canto_api_key")
        val dedupeSec = intPreferencesKey("dedupe_window_seconds")
        val regionalLang = stringPreferencesKey("regional_language")
        val audioInputId = intPreferencesKey("audio_input_id")
        val audioInputName = stringPreferencesKey("audio_input_name")
    }

    companion object {
        val DEFAULT = Settings(
            confidenceThreshold = 0.7f,
            fallbackLatitude = 18.5204,
            fallbackLongitude = 73.8567,
            clipSeconds = 6.0,
            stepSeconds = 1.5,
            useDeviceGps = true,
            useMetaModel = true,
            owmEnabled = true,
            owmApiKey = "",
            themeMode = ThemeMode.LIGHT,
            showBirdImages = true,
            referenceCallsEnabled = true,
            xenoCantoApiKey = "",
            dedupeWindowSeconds = 180,
            regionalLanguage = "",
            audioInputId = 0,
            audioInputName = "",
        )
    }
}

data class Settings(
    val confidenceThreshold: Float,
    /** Fallback when [useDeviceGps] is false or the device returns no fix. */
    val fallbackLatitude: Double,
    /** Fallback when [useDeviceGps] is false or the device returns no fix. */
    val fallbackLongitude: Double,
    /** Length of the saved/review clip in seconds. Inference always uses 3 s of it. */
    val clipSeconds: Double,
    val stepSeconds: Double,
    val useDeviceGps: Boolean,
    val useMetaModel: Boolean,
    val owmEnabled: Boolean,
    val owmApiKey: String,
    val themeMode: ThemeMode,
    val showBirdImages: Boolean,
    val referenceCallsEnabled: Boolean,
    /** User-entered Xeno-canto v3 key; falls back to BuildConfig when blank (see [effectiveXenoKey]). */
    val xenoCantoApiKey: String,
    /** Ignore the same species if re-heard within this many seconds (0 = log every time). */
    val dedupeWindowSeconds: Int,
    /** ISO code of a second language to show species names in ("" = English only). */
    val regionalLanguage: String,
    /** Preferred audio input device id (0 = automatic/default mic). */
    val audioInputId: Int,
    /** Human label of the chosen input, used to re-match it after reconnect. */
    val audioInputName: String,
)

/**
 * Languages the app can show species names in.
 *
 * Codes in [bundledCodes] ship a `labels_<code>.txt` and resolve instantly offline. The
 * extra Indian languages have no bundled BirdNET labels (only Malayalam exists), so they are
 * resolved live from Wikipedia language links — see [isLive].
 */
object RegionalLanguages {
    /** Languages with a bundled label file (instant, offline). */
    val bundledCodes: Set<String> =
        setOf("ml", "es", "fr", "de", "it", "nl", "ru", "ar", "zh", "ja", "ko", "th")

    /** Ordered options for the picker; `code` "" means English only. */
    val options: List<Pair<String, String>> = listOf(
        "" to "English only",
        // Indian languages (live from Wikipedia — coverage varies by species).
        "hi" to "Hindi (हिन्दी)",
        "mr" to "Marathi (मराठी)",
        "ta" to "Tamil (தமிழ்)",
        "te" to "Telugu (తెలుగు)",
        "bn" to "Bengali (বাংলা)",
        "kn" to "Kannada (ಕನ್ನಡ)",
        "gu" to "Gujarati (ગુજરાતી)",
        "pa" to "Punjabi (ਪੰਜਾਬੀ)",
        "ml" to "Malayalam (മലയാളം)",
        // World languages (bundled, offline).
        "es" to "Spanish (Español)",
        "fr" to "French (Français)",
        "de" to "German (Deutsch)",
        "it" to "Italian (Italiano)",
        "nl" to "Dutch (Nederlands)",
        "ru" to "Russian (Русский)",
        "ar" to "Arabic (العربية)",
        "zh" to "Chinese (中文)",
        "ja" to "Japanese (日本語)",
        "ko" to "Korean (한국어)",
        "th" to "Thai (ไทย)",
    )

    /** True when the language has no bundled file and must be resolved online. */
    fun isLive(code: String): Boolean = code.isNotBlank() && code !in bundledCodes

    fun displayFor(code: String): String =
        options.firstOrNull { it.first == code }?.second ?: "English only"
}

private val Context.dataStore by preferencesDataStore(name = "bird_finder_settings")
