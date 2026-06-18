package com.example.birdfinder.classify

/**
 * One species detected above the caller-supplied confidence threshold.
 */
data class Detection(
    val speciesCommon: String,
    val speciesScientific: String,
    val confidence: Float,
)

/**
 * Pluggable bird species classifier. v1 is backed by BirdNET 2.4 (CC BY-NC-SA);
 * the abstraction exists so a future commercial-friendly model (e.g. Perch) can
 * be dropped in without touching capture, persistence, or UI.
 *
 * Implementations must be thread-confined to a single classifying coroutine —
 * callers are expected to invoke [classify] serially.
 */
interface BirdClassifier : AutoCloseable {

    /** Human-readable model identifier persisted with each detection (e.g. `BirdNET`). */
    val modelName: String

    /** Version string persisted with each detection (e.g. `GLOBAL_6K_V2.4_FP16`). */
    val modelVersion: String

    /** Required audio sample rate in Hz. BirdNET expects 48 000. */
    val sampleRateHz: Int

    /** Required samples per window. BirdNET expects 144 000 (= 3 s at 48 kHz). */
    val inputSamples: Int

    /**
     * Classify a single window of mono PCM in the model's native scale.
     *
     * @param window length must equal [inputSamples].
     * @param minConfidence drop any detection at or below this value AFTER any
     *   location/season prior has been applied.
     * @return all species above [minConfidence], multi-label by design.
     */
    suspend fun classify(window: FloatArray, minConfidence: Float): List<Detection>

    /**
     * Optionally set a location/season prior. Implementations that don't model this
     * may simply ignore it (default no-op).
     *
     * @param latitude WGS-84 degrees.
     * @param longitude WGS-84 degrees.
     * @param dayOfYearUtc 1..366 — pipeline converts to whatever encoding the model expects.
     */
    suspend fun setLocationPrior(latitude: Double, longitude: Double, dayOfYearUtc: Int) {
        // default: no-op
    }
}
