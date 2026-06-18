package com.example.birdfinder.media

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Xeno-canto API v3 — community archive of bird sound recordings. Used to fetch a
 * reference call so the user can compare it against their own recording.
 *
 * NOTE: v3 requires a free API key (`key`). The legacy v2 endpoint was retired.
 * Get a key at https://xeno-canto.org/account .
 */
interface XenoCantoApi {
    @GET("api/3/recordings")
    suspend fun recordings(
        @Query("query") query: String,
        @Query("key") key: String,
    ): XcResponse
}

@JsonClass(generateAdapter = false)
data class XcResponse(
    @Json(name = "numRecordings") val numRecordings: String? = null,
    val recordings: List<XcRecording> = emptyList(),
)

@JsonClass(generateAdapter = false)
data class XcRecording(
    val id: String? = null,
    /** English common name as catalogued by Xeno-canto. */
    val en: String? = null,
    /** Direct audio URL; may be protocol-relative ("//…"). */
    val file: String? = null,
    /** Quality rating "A".."E" (A best). */
    val q: String? = null,
    /** Recordist attribution. */
    val rec: String? = null,
    /** Vocalisation type, e.g. "song", "call", "alarm call". */
    val type: String? = null,
)
