package com.example.birdfinder.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes a single mono 16-bit PCM WAV file from a FloatArray window in BirdNET-Lite
 * native scale (int16 cast to float, NOT normalized).
 */
object WavWriter {

    fun writeMono16(
        file: File,
        samples: FloatArray,
        sampleRateHz: Int,
    ) {
        file.parentFile?.mkdirs()
        val pcmBytes = samples.size * 2

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write(header(pcmBytes, sampleRateHz))
            val out = ByteBuffer.allocate(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                val clamped = when {
                    s > Short.MAX_VALUE.toFloat() -> Short.MAX_VALUE
                    s < Short.MIN_VALUE.toFloat() -> Short.MIN_VALUE
                    else -> s.toInt().toShort()
                }
                out.putShort(clamped)
            }
            raf.write(out.array())
        }
    }

    private fun header(pcmBytes: Int, sampleRateHz: Int): ByteArray {
        val totalDataLen = pcmBytes + 36
        val byteRate = sampleRateHz * 2  // mono, 16-bit
        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(totalDataLen)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)                  // fmt chunk size
        buf.putShort(1)                 // PCM
        buf.putShort(1)                 // mono
        buf.putInt(sampleRateHz)
        buf.putInt(byteRate)
        buf.putShort(2)                 // block align (mono * 16-bit / 8)
        buf.putShort(16)                // bits per sample
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(pcmBytes)
        return buf.array()
    }
}
