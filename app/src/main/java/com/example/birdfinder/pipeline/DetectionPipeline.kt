package com.example.birdfinder.pipeline

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.birdfinder.audio.AudioCaptureSource
import com.example.birdfinder.audio.AudioDevices
import com.example.birdfinder.audio.AudioWindow
import com.example.birdfinder.audio.SlidingWindow
import com.example.birdfinder.audio.WavWriter
import com.example.birdfinder.classify.BirdClassifier
import com.example.birdfinder.classify.BirdNetClassifier
import com.example.birdfinder.classify.Detection
import com.example.birdfinder.data.db.DetectionEntity
import com.example.birdfinder.data.repo.DetectionRepository
import com.example.birdfinder.location.GeoFix
import com.example.birdfinder.location.LocationProvider
import com.example.birdfinder.settings.Settings
import com.example.birdfinder.settings.SettingsStore
import com.example.birdfinder.weather.WeatherClient
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A persisted detection, suitable for showing in the Listen "saved" dashboard. */
data class SavedDetection(
    val id: Long,
    val timestampUtc: Long,
    val speciesCommon: String,
    val speciesScientific: String,
    val confidence: Float,
    val latitude: Double?,
    val longitude: Double?,
    val weatherTempC: Float?,
    val weatherCondition: String?,
    val modelName: String,
    val modelVersion: String,
    val clipPath: String,
)

/** Snapshot of the live listening pipeline for UI consumption. */
data class ListenState(
    val running: Boolean = false,
    val rms: Float = 0f,
    val locationAccuracyM: Float? = null,
    val recentDetections: List<Detection> = emptyList(),
    val recentlySaved: List<SavedDetection> = emptyList(),
    val error: String? = null,
)

/**
 * Orchestrates: mic → sliding window → classifier (+ meta prior) → enrich → Room.
 *
 * Inference runs on fixed 3 s windows; the saved WAV is the configurable-length clip
 * (default 6 s). A fresh high-accuracy GPS fix is cached and refreshed at most every
 * [LOCATION_TTL_MS] to balance accuracy and battery.
 */
class DetectionPipeline(
    private val context: Context,
    private val settings: SettingsStore,
    private val repository: DetectionRepository,
    private val location: LocationProvider,
    private val weather: WeatherClient,
) {

    private val _state = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + Dispatchers.Default)
    private var loopJob: Job? = null
    private var classifier: BirdClassifier? = null
    private val lastPriorDay = AtomicLong(-1)
    private val lifecycleMutex = Mutex()

    private var cachedFix: GeoFix? = null
    private var cachedFixAtMs: Long = 0

    /** scientificName → epoch millis it was last persisted, for the repeat-suppression window. */
    private val lastLoggedAtMs = HashMap<String, Long>()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (loopJob?.isActive == true) return
        _state.value = _state.value.copy(running = true, error = null)
        loopJob = scope.launch {
            try {
                runPipeline()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Pipeline crashed", t)
                _state.value = _state.value.copy(running = false, error = t.message ?: t.toString())
            }
        }
    }

    suspend fun stop() = lifecycleMutex.withLock {
        val job = loopJob
        loopJob = null
        job?.cancelAndJoin()
        try { classifier?.close() } catch (t: Throwable) { Log.w(TAG, "Closing classifier failed", t) }
        classifier = null
        lastPriorDay.set(-1)
        cachedFix = null
        lastLoggedAtMs.clear()
        _state.value = _state.value.copy(running = false, rms = 0f, recentDetections = emptyList())
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun runPipeline() {
        val s0 = settings.state.first()
        val cls = BirdNetClassifier(context, useMetaModel = s0.useMetaModel).also { classifier = it }

        val sampleRate = cls.sampleRateHz
        val inferenceSamples = cls.inputSamples
        val clipSamples = (s0.clipSeconds * sampleRate).toInt().coerceAtLeast(inferenceSamples)
        val stepSamples = (s0.stepSeconds * sampleRate).toInt().coerceIn(1, inferenceSamples)

        val mic = AudioCaptureSource(sampleRate)
        val preferredDevice = AudioDevices.resolve(context, s0.audioInputId, s0.audioInputName)
        val splitter = SlidingWindow(sampleRate, inferenceSamples, clipSamples, stepSamples)

        // Deferred save: when a detection fires, the bird is in the *last* 3 s of the buffer.
        // We wait POST_ROLL_WINDOWS more windows and then save, so the captured 6 s clip ends
        // with ~1.5 s of trailing audio and the call sits near the middle — otherwise the call
        // is at the very end of the clip and gets clipped by some players (e.g. WhatsApp).
        var pending: PendingSave? = null

        splitter.windows(mic.pcmStream(preferredDevice)).collect { window ->
            val s = settings.state.first()
            ensurePrior(cls, s)

            // Flush a pending save once enough trailing audio has been captured.
            pending?.let { p ->
                p.windowsLeft -= 1
                if (p.windowsLeft <= 0) {
                    persist(window, p.detections, p.settings, cls, p.timestampUtc)
                    pending = null
                }
            }

            val detections = cls.classify(window.samples, s.confidenceThreshold)
            // Live feed always reflects what's heard this window…
            _state.value = _state.value.copy(rms = window.rms, recentDetections = detections.take(5))
            // …but only species not heard within the cooldown get logged, to avoid clogging.
            val toLog = filterRepeats(detections, window.startTimestampMillis, s.dedupeWindowSeconds)
            if (toLog.isNotEmpty()) {
                val current = pending
                if (current == null) {
                    pending = PendingSave(toLog, window.startTimestampMillis, s, POST_ROLL_WINDOWS)
                } else {
                    // Merge species heard while a save is already pending.
                    val merged = (current.detections + toLog).distinctBy { it.speciesScientific }
                    pending = current.copy(detections = merged)
                }
            }
        }
    }

    private data class PendingSave(
        val detections: List<Detection>,
        val timestampUtc: Long,
        val settings: Settings,
        var windowsLeft: Int,
    )

    /** Drop detections whose species was logged within [windowSeconds]; record the rest. */
    private fun filterRepeats(
        detections: List<Detection>,
        nowMs: Long,
        windowSeconds: Int,
    ): List<Detection> {
        if (windowSeconds <= 0) return detections
        val windowMs = windowSeconds * 1000L
        val fresh = ArrayList<Detection>(detections.size)
        for (d in detections) {
            val last = lastLoggedAtMs[d.speciesScientific]
            if (last == null || nowMs - last >= windowMs) {
                lastLoggedAtMs[d.speciesScientific] = nowMs
                fresh += d
            }
        }
        return fresh
    }

    private suspend fun ensurePrior(classifier: BirdClassifier, s: Settings) {
        val today = ZonedDateTime.now(ZoneOffset.UTC).dayOfYear.toLong()
        if (lastPriorDay.get() == today) return
        val (lat, lon) = resolveCoordinates(s)
        classifier.setLocationPrior(lat, lon, today.toInt())
        lastPriorDay.set(today)
    }

    /** Returns lat/lon, using a cached fresh GPS fix when [Settings.useDeviceGps] is on. */
    private suspend fun resolveCoordinates(s: Settings): Pair<Double, Double> {
        if (!s.useDeviceGps) {
            _state.value = _state.value.copy(locationAccuracyM = null)
            return s.fallbackLatitude to s.fallbackLongitude
        }
        val now = System.currentTimeMillis()
        val cached = cachedFix
        val fix = if (cached != null && now - cachedFixAtMs < LOCATION_TTL_MS) {
            cached
        } else {
            val fresh = runCatching { location.freshFix() }.getOrNull()
            if (fresh != null) {
                cachedFix = fresh
                cachedFixAtMs = now
            }
            fresh ?: cached
        }
        return if (fix != null) {
            _state.value = _state.value.copy(locationAccuracyM = fix.accuracyMeters)
            fix.latitude to fix.longitude
        } else {
            s.fallbackLatitude to s.fallbackLongitude
        }
    }

    private suspend fun persist(
        window: AudioWindow,
        detections: List<Detection>,
        s: Settings,
        cls: BirdClassifier,
        timestampUtc: Long,
    ) {
        val (lat, lon) = resolveCoordinates(s)
        val weatherSnapshot = if (s.owmEnabled) weather.fetch(lat, lon) else null

        val clipDir = File(context.filesDir, "clips").apply { mkdirs() }
        val clipFile = File(clipDir, "$timestampUtc.wav")
        // Save the longer review clip (post-roll buffer), not just the 3 s inference window.
        WavWriter.writeMono16(clipFile, window.clipSamples, cls.sampleRateHz)
        val relative = "clips/${clipFile.name}"

        val rows = detections.map { d ->
            DetectionEntity(
                timestampUtc = timestampUtc,
                speciesCommon = d.speciesCommon,
                speciesScientific = d.speciesScientific,
                confidence = d.confidence,
                latitude = lat,
                longitude = lon,
                modelName = cls.modelName,
                modelVersion = cls.modelVersion,
                clipPath = relative,
                weatherTempC = weatherSnapshot?.temperatureC,
                weatherCondition = weatherSnapshot?.condition,
            )
        }
        val newIds = repository.insertAll(rows)
        val savedFresh = rows.zip(newIds) { row, id ->
            SavedDetection(
                id = id,
                timestampUtc = row.timestampUtc,
                speciesCommon = row.speciesCommon,
                speciesScientific = row.speciesScientific,
                confidence = row.confidence,
                latitude = row.latitude,
                longitude = row.longitude,
                weatherTempC = row.weatherTempC,
                weatherCondition = row.weatherCondition,
                modelName = row.modelName,
                modelVersion = row.modelVersion,
                clipPath = row.clipPath,
            )
        }
        val merged = (savedFresh + _state.value.recentlySaved).take(RECENTLY_SAVED_CAP)
        _state.value = _state.value.copy(recentlySaved = merged)
    }

    companion object {
        private const val TAG = "DetectionPipeline"
        private const val RECENTLY_SAVED_CAP = 12
        private const val LOCATION_TTL_MS = 15_000L

        /** Windows of trailing audio to capture before saving, so the call sits mid-clip. */
        private const val POST_ROLL_WINDOWS = 1
    }
}
