package com.example.birdfinder.classify

import kotlin.math.ceil

/**
 * BirdNET meta model's expected week-of-year input.
 *
 * The V2 meta model (`MData_Model_V2`) takes the raw 48-week index:
 * `week = ceil(dayOfYear * 48 / 366)` ∈ [1, 48], passed in input slot 2 (after lat, lon).
 *
 * The `cos(toRadians(week * 7.5)) + 1.0` encoding belongs to the older V1 meta model. Fed to
 * V2 it collapses the season signal and suppresses migrants/seasonal species (e.g. the
 * Common Cuckoo in Europe in May scored ~0.00004 instead of ~0.45).
 */
object WeekEncoding {
    fun encode(dayOfYear: Int): Float {
        require(dayOfYear in 1..366) { "dayOfYear out of range: $dayOfYear" }
        return ceil(dayOfYear * 48.0 / 366.0).toInt().coerceIn(1, 48).toFloat()
    }
}
