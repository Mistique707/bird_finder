package com.example.birdfinder.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Streams raw mono PCM 16-bit from the microphone at [sampleRateHz].
 * Emits `ShortArray` chunks sized to be read efficiently from [AudioRecord]; downstream
 * stages own buffering/windowing.
 *
 * Cancelling the collecting coroutine stops and releases the [AudioRecord].
 */
class AudioCaptureSource(
    val sampleRateHz: Int = SAMPLE_RATE_HZ,
) {

    /**
     * @param preferredDevice route capture through this input (e.g. a USB shotgun mic);
     *   null uses the system default. If the device can't be honored, capture continues
     *   on the default mic.
     */
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun pcmStream(preferredDevice: AudioDeviceInfo? = null): Flow<ShortArray> = callbackFlow {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuf > 0) { "AudioRecord.getMinBufferSize failed: $minBuf" }
        // Read ~100 ms per pull so the window builder gets new data quickly.
        val readChunk = (sampleRateHz / 10).coerceAtLeast(minBuf / 2)
        val bufferBytes = (readChunk * 2 * 4)  // 4× headroom

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize (state=${recorder.state})"
        }

        // Route through the chosen external mic if possible (no-op / false → default mic).
        if (preferredDevice != null) {
            runCatching { recorder.setPreferredDevice(preferredDevice) }
        }

        val buf = ShortArray(readChunk)
        recorder.startRecording()

        val thread = Thread({
            try {
                while (!Thread.currentThread().isInterrupted &&
                    recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
                ) {
                    val n = recorder.read(buf, 0, buf.size)
                    when {
                        n > 0 -> trySend(buf.copyOf(n))
                        // After stop(), AudioRecord.read may return 0 or ERROR_INVALID_OPERATION (-3)
                        // briefly; exit cleanly rather than treating it as a failure.
                        n == 0 -> Unit
                        else -> return@Thread
                    }
                }
            } catch (t: Throwable) {
                close(t)
            }
        }, "bird-finder-mic").apply {
            isDaemon = true
            start()
        }

        awaitClose {
            // CRITICAL order: stop() returns the in-flight read(), then we wait for the
            // thread to fully exit its read loop BEFORE release()ing the native handle.
            // Calling release() while another thread is inside read() crashes natively.
            try { recorder.stop() } catch (_: Throwable) {}
            thread.interrupt()
            try { thread.join(500L) } catch (_: Throwable) {}
            try { recorder.release() } catch (_: Throwable) {}
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SAMPLE_RATE_HZ = 48_000
    }
}
