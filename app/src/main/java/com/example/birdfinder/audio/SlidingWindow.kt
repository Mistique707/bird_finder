package com.example.birdfinder.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A single audio frame ready for classification + saving.
 *
 * @param samples the inference window: mono PCM, length = `inferenceSamples` (144 000 = 3 s
 *   for BirdNET). **Native scale:** int16 cast to float, NOT divided by 32768.
 * @param clipSamples a longer window for saving/review/comparison, length up to
 *   `clipSamples` (e.g. 288 000 = 6 s). Superset of [samples] ending at the same instant.
 * @param startTimestampMillis epoch millis (UTC) when the inference window began.
 * @param rms RMS amplitude of the inference window, for UI level indicators.
 */
data class AudioWindow(
    val samples: FloatArray,
    val clipSamples: FloatArray,
    val startTimestampMillis: Long,
    val rms: Float,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/**
 * Builds overlapping inference windows from a stream of PCM chunks, while also keeping a
 * longer rolling buffer so each emission carries a [AudioWindow.clipSamples] clip.
 *
 * Inference must run on a fixed 3 s window (BirdNET model constraint), but the saved clip
 * can be longer — they are decoupled here.
 *
 * @param sampleRateHz 48 000 for BirdNET.
 * @param inferenceSamples samples per inference window (144 000 = 3 s).
 * @param clipSamples samples per saved clip (>= inferenceSamples; e.g. 288 000 = 6 s).
 * @param stepSamples hop between emissions (72 000 = 1.5 s).
 */
class SlidingWindow(
    private val sampleRateHz: Int,
    private val inferenceSamples: Int,
    private val clipSamples: Int,
    private val stepSamples: Int,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    init {
        require(inferenceSamples > 0)
        require(clipSamples >= inferenceSamples)
        require(stepSamples in 1..inferenceSamples)
    }

    fun windows(source: Flow<ShortArray>): Flow<AudioWindow> = flow {
        val ring = FloatArray(clipSamples)
        var filled = 0L         // total samples written since start (monotonic)
        var sinceLastEmit = 0L  // samples since the last window we emitted

        source.collect { chunk ->
            for (i in chunk.indices) {
                // CRITICAL: int16 cast to float, NOT divided by 32768 — matches BirdNET-Lite scale.
                ring[(filled % clipSamples).toInt()] = chunk[i].toFloat()
                filled++
                sinceLastEmit++

                if (filled >= inferenceSamples && sinceLastEmit >= stepSamples) {
                    sinceLastEmit = 0L
                    val inference = lastN(ring, filled, inferenceSamples)
                    val clipLen = minOf(filled, clipSamples.toLong()).toInt()
                    val clip = lastN(ring, filled, clipLen)
                    val rms = rms(inference)
                    val inferenceDurationMillis = inferenceSamples * 1000L / sampleRateHz
                    emit(AudioWindow(inference, clip, nowMillis() - inferenceDurationMillis, rms))
                }
            }
        }
    }

    /** Copy the most recent [count] samples from the ring, ordered oldest → newest. */
    private fun lastN(ring: FloatArray, filled: Long, count: Int): FloatArray {
        val n = ring.size
        val out = FloatArray(count)
        var src = (((filled - count) % n + n) % n).toInt()
        for (i in 0 until count) {
            out[i] = ring[src]
            src++
            if (src == n) src = 0
        }
        return out
    }

    private fun rms(samples: FloatArray): Float {
        var sumSq = 0.0
        for (s in samples) sumSq += (s.toDouble() * s.toDouble())
        return kotlin.math.sqrt(sumSq / samples.size).toFloat()
    }
}
