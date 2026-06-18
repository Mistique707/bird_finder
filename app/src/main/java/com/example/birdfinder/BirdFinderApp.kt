package com.example.birdfinder

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.example.birdfinder.data.db.AppDatabase
import com.example.birdfinder.data.repo.DetectionRepository
import com.example.birdfinder.location.LocationProvider
import com.example.birdfinder.media.BirdMediaClient
import com.example.birdfinder.names.RegionalNames
import com.example.birdfinder.pipeline.DetectionPipeline
import com.example.birdfinder.settings.SettingsStore
import com.example.birdfinder.weather.WeatherClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/**
 * Minimal manual-DI container. Subsystems are lazy so the app cold-starts fast
 * and TFLite interpreters / Room aren't touched until something uses them.
 *
 * Also supplies the app-wide Coil [ImageLoader]: Wikimedia returns HTTP 403 for
 * Coil's default `okhttp/x.y` User-Agent, so we attach a descriptive one — without
 * this, bird photos silently fail to load.
 */
class BirdFinderApp : Application(), ImageLoaderFactory {

    val settings: SettingsStore by lazy { SettingsStore(this) }
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val detections: DetectionRepository by lazy { DetectionRepository(database.detectionDao()) }
    val location: LocationProvider by lazy { LocationProvider(this) }
    val weather: WeatherClient by lazy { WeatherClient.build(BuildConfig.OWM_API_KEY) }
    val media: BirdMediaClient by lazy { BirdMediaClient.build(enabled = true) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val regionalNames: RegionalNames by lazy { RegionalNames(this, settings.state, appScope) }

    val pipeline: DetectionPipeline by lazy {
        DetectionPipeline(
            context = this,
            settings = settings,
            repository = detections,
            location = location,
            weather = weather,
        )
    }

    override fun newImageLoader(): ImageLoader {
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "BirdFinder/0.1 (personal hobby app)")
                    .build()
                chain.proceed(req)
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(http)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("bird_images"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_LISTENING = "listening"
    }
}
