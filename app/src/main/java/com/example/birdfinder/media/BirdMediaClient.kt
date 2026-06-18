package com.example.birdfinder.media

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/** A reference recording resolved from Xeno-canto. */
data class ReferenceCall(
    val audioUrl: String,
    val recordist: String?,
    /** Vocalisation type, e.g. "song", "call" — may be null. */
    val type: String?,
    val source: String = "Xeno-canto",
)

/**
 * Fetches a representative photo (Wikipedia, no key) and a reference call (Xeno-canto v3,
 * requires a free key) for a species. Lookups are best-effort and cached in memory keyed
 * by scientific name; any failure (offline, no match, missing key) resolves to null so the
 * UI degrades gracefully. Failures are logged under tag "BirdMedia" for debugging.
 */
class BirdMediaClient internal constructor(
    private val wikipedia: WikipediaApi?,
    private val xeno: XenoCantoApi?,
) {
    // "" sentinel = looked up, nothing found (so we don't retry every recomposition).
    private val imageCache = ConcurrentHashMap<String, String>()
    private val infoCache = ConcurrentHashMap<String, String>()
    private val localNameCache = ConcurrentHashMap<String, String>()
    private val callCache = ConcurrentHashMap<String, ReferenceCall?>()

    suspend fun imageUrl(scientific: String, common: String): String? {
        val key = scientific.lowercase().trim()
        imageCache[key]?.let { return it.ifEmpty { null } }
        val url = fetchImage(scientific) ?: fetchImage(common)
        if (url == null) Log.w(TAG, "No Wikipedia image for '$scientific' / '$common'")
        imageCache[key] = url ?: ""
        return url
    }

    /**
     * Localized common name for a species in language [langCode] via Wikipedia language
     * links. Best-effort + cached; many species lack translations and resolve to null.
     */
    suspend fun localName(scientific: String, common: String, langCode: String): String? {
        if (langCode.isBlank()) return null
        val key = "$langCode|${scientific.lowercase().trim()}"
        localNameCache[key]?.let { return it.ifEmpty { null } }
        val name = fetchLangTitle(scientific, langCode) ?: fetchLangTitle(common, langCode)
        localNameCache[key] = name ?: ""
        return name
    }

    private suspend fun fetchLangTitle(title: String, lang: String): String? =
        withContext(Dispatchers.IO) {
            val api = wikipedia ?: return@withContext null
            runCatching { api.langLink(title.trim(), lang) }
                .onFailure { Log.w(TAG, "Wikipedia langlink failed for '$title' ($lang): ${it.message}") }
                .getOrNull()
                ?.query?.pages?.values
                ?.firstNotNullOfOrNull { it.langlinks.firstOrNull()?.title }
                ?.takeIf { it.isNotBlank() }
        }

    /** Short plain-text description of the species (Wikipedia summary extract). */
    suspend fun info(scientific: String, common: String): String? {
        val key = scientific.lowercase().trim()
        infoCache[key]?.let { return it.ifEmpty { null } }
        val text = fetchExtract(scientific) ?: fetchExtract(common)
        infoCache[key] = text ?: ""
        return text
    }

    /**
     * @param apiKey Xeno-canto v3 key. When blank, returns null (no key configured).
     */
    suspend fun referenceCall(scientific: String, common: String, apiKey: String): ReferenceCall? {
        if (apiKey.isBlank()) {
            Log.w(TAG, "Xeno-canto key not set; skipping reference call lookup")
            return null
        }
        val key = scientific.lowercase().trim()
        if (callCache.containsKey(key)) return callCache[key]
        // Calls are looked up by scientific name only (v3 tag query needs genus+species).
        val call = fetchCall(scientific, apiKey)
        if (call == null) Log.w(TAG, "No Xeno-canto recording for '$scientific'")
        callCache[key] = call
        return call
    }

    private suspend fun fetchImage(title: String): String? = withContext(Dispatchers.IO) {
        val api = wikipedia ?: return@withContext null
        val clean = title.trim()
        // 1) Fast REST summary.
        runCatching { api.summary(clean) }
            .onFailure { Log.w(TAG, "Wikipedia summary failed for '$clean': ${it.message}") }
            .getOrNull()
            ?.let { it.thumbnail?.source ?: it.originalimage?.source }
            ?.let { return@withContext it }
        // 2) Action API fallback (handles redirects + pages lacking a summary thumbnail).
        runCatching { api.pageImage(clean) }
            .onFailure { Log.w(TAG, "Wikipedia pageimages failed for '$clean': ${it.message}") }
            .getOrNull()
            ?.query?.pages?.values
            ?.firstNotNullOfOrNull { it.thumbnail?.source }
    }

    private suspend fun fetchExtract(title: String): String? = withContext(Dispatchers.IO) {
        val api = wikipedia ?: return@withContext null
        runCatching { api.summary(title.trim()) }
            .onFailure { Log.w(TAG, "Wikipedia extract failed for '$title': ${it.message}") }
            .getOrNull()
            ?.takeIf { it.type != "disambiguation" }
            ?.extract
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun fetchCall(scientific: String, apiKey: String): ReferenceCall? =
        withContext(Dispatchers.IO) {
            val api = xeno ?: return@withContext null
            // v3 only accepts tag queries, e.g. `gen:Corvus sp:splendens` (not free text).
            val parts = scientific.trim().split(Regex("\\s+"))
            if (parts.size < 2) return@withContext null
            val base = "gen:${parts[0]} sp:${parts[1]}"

            // Bias toward the vocalisation a bird would most likely make right now
            // (dawn chorus → song, etc.); fall back to any recording if none match.
            val type = contextualCallType()
            val typed = fetchPlayable(api, "$base type:$type", apiKey)
            val pick = typed ?: fetchPlayable(api, base, apiKey) ?: return@withContext null
            ReferenceCall(
                audioUrl = normalizeUrl(pick.file!!),
                recordist = pick.rec,
                type = pick.type,
            )
        }

    private suspend fun fetchPlayable(api: XenoCantoApi, query: String, apiKey: String): XcRecording? {
        val resp = runCatching { api.recordings(query, apiKey) }
            .onFailure { Log.w(TAG, "Xeno-canto failed for '$query': ${it.message}") }
            .getOrNull() ?: return null
        // Some recordings have a null file (restricted); pick the best playable one.
        return resp.recordings.firstOrNull { it.q == "A" && !it.file.isNullOrBlank() }
            ?: resp.recordings.firstOrNull { !it.file.isNullOrBlank() }
    }

    /** A plausible vocalisation type for the current time of day / season. */
    private fun contextualCallType(): String {
        val now = java.time.LocalDateTime.now()
        val hour = now.hour
        val breedingSeason = now.monthValue in 3..7
        return when {
            hour in 4..8 -> "song"                  // dawn chorus
            hour in 17..20 && breedingSeason -> "song"
            else -> "call"
        }
    }

    private fun normalizeUrl(raw: String): String = when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("http://") -> "https://" + raw.removePrefix("http://")
        else -> raw
    }

    companion object {
        private const val TAG = "BirdMedia"

        fun build(enabled: Boolean): BirdMediaClient {
            if (!enabled) return BirdMediaClient(null, null)
            val http = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", "BirdFinder/0.1 (personal hobby app)")
                        .build()
                    chain.proceed(req)
                }
                .build()
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val converter = MoshiConverterFactory.create(moshi)

            val wikipedia = Retrofit.Builder()
                .baseUrl("https://en.wikipedia.org/")
                .client(http)
                .addConverterFactory(converter)
                .build()
                .create(WikipediaApi::class.java)

            val xeno = Retrofit.Builder()
                .baseUrl("https://xeno-canto.org/")
                .client(http)
                .addConverterFactory(converter)
                .build()
                .create(XenoCantoApi::class.java)

            return BirdMediaClient(wikipedia, xeno)
        }
    }
}
