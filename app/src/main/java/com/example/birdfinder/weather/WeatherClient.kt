package com.example.birdfinder.weather

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * OpenWeatherMap "current weather" caller, throttled to one network request per
 * [MIN_INTERVAL_MILLIS] per `(roundedLat, roundedLon)` pair. Returns null if the
 * key is empty or the call fails — the pipeline writes nullable weather columns.
 */
class WeatherClient internal constructor(
    private val api: WeatherApi?,
    private val apiKey: String,
) {

    private val cache = HashMap<String, CacheEntry>()

    suspend fun fetch(latitude: Double, longitude: Double): WeatherSnapshot? {
        if (api == null || apiKey.isBlank()) return null
        val key = "%.2f,%.2f".format(latitude, longitude)
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { now - it.timestampMillis < MIN_INTERVAL_MILLIS }?.let { return it.snapshot }
        return try {
            val dto = api.current(latitude, longitude, apiKey)
            val snap = WeatherSnapshot(
                temperatureC = dto.main.temp.takeIf { !it.isNaN() }?.toFloat(),
                condition = dto.weather.firstOrNull()?.main,
            )
            cache[key] = CacheEntry(now, snap)
            snap
        } catch (_: Throwable) {
            null
        }
    }

    private data class CacheEntry(val timestampMillis: Long, val snapshot: WeatherSnapshot)

    companion object {
        private const val MIN_INTERVAL_MILLIS = 10 * 60 * 1000L

        fun build(apiKey: String): WeatherClient {
            if (apiKey.isBlank()) return WeatherClient(api = null, apiKey = "")
            val http = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
                .build()
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val api = Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/")
                .client(http)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(WeatherApi::class.java)
            return WeatherClient(api, apiKey)
        }
    }
}
