package com.example.birdfinder.classify

import kotlin.math.cos
import kotlin.math.ceil

/**
 * BirdNET meta model's expected week-of-year encoding.
 *
 * From the upstream reference: integer week = `ceil(dayOfYear * 48 / 366)` ∈ [1, 48],
 * encoded as `cos(toRadians(week * 7.5)) + 1.0`. This is what the meta interpreter
 * expects in input slot 2 (after lat, lon).
 */
object WeekEncoding {
    fun encode(dayOfYear: Int): Float {
        require(dayOfYear in 1..366) { "dayOfYear out of range: $dayOfYear" }
        val week = ceil(dayOfYear * 48.0 / 366.0).toInt().coerceIn(1, 48)
        return (cos(Math.toRadians(week * 7.5)) + 1.0).toFloat()
    }
}
