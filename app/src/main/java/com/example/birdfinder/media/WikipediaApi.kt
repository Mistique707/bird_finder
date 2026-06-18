package com.example.birdfinder.media

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Minimal Wikipedia clients used to fetch a representative photo for a species and the
 * localized common name (via language links). Wikipedia asks for a descriptive User-Agent
 * (added by an OkHttp interceptor). No API key is required.
 */
interface WikipediaApi {
    /** REST summary: fast, returns a thumbnail for the (possibly redirected) page. */
    @GET("api/rest_v1/page/summary/{title}")
    suspend fun summary(@Path("title") title: String): WikiSummaryDto

    /**
     * Action API fallback. `redirects=1` resolves common↔scientific redirects and
     * `pageimages` returns a thumbnail even when the REST summary has none.
     */
    @GET("w/api.php?action=query&prop=pageimages&format=json&redirects=1&pithumbsize=480")
    suspend fun pageImage(@Query("titles") titles: String): WikiQueryDto

    /**
     * Language link: the article title in [lllang] = the localized common name, when the
     * species has an article in that language. Used for Indian languages that aren't in
     * the bundled BirdNET label set.
     */
    @GET("w/api.php?action=query&prop=langlinks&format=json&redirects=1&lllimit=1")
    suspend fun langLink(
        @Query("titles") titles: String,
        @Query("lllang") lllang: String,
    ): WikiLangDto
}

@JsonClass(generateAdapter = false)
data class WikiLangDto(val query: WikiLangQuery? = null)

@JsonClass(generateAdapter = false)
data class WikiLangQuery(val pages: Map<String, WikiLangPage> = emptyMap())

@JsonClass(generateAdapter = false)
data class WikiLangPage(val langlinks: List<WikiLangLink> = emptyList())

@JsonClass(generateAdapter = false)
data class WikiLangLink(@Json(name = "*") val title: String? = null)

@JsonClass(generateAdapter = false)
data class WikiSummaryDto(
    val type: String? = null,
    val title: String? = null,
    val extract: String? = null,
    val thumbnail: WikiImage? = null,
    val originalimage: WikiImage? = null,
)

@JsonClass(generateAdapter = false)
data class WikiImage(
    val source: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

@JsonClass(generateAdapter = false)
data class WikiQueryDto(
    val query: WikiQueryBody? = null,
)

@JsonClass(generateAdapter = false)
data class WikiQueryBody(
    /** Keyed by numeric page id as a string. */
    val pages: Map<String, WikiPage> = emptyMap(),
)

@JsonClass(generateAdapter = false)
data class WikiPage(
    val title: String? = null,
    val thumbnail: WikiImage? = null,
)
