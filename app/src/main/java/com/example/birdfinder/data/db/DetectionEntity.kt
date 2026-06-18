package com.example.birdfinder.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "detections",
    indices = [Index("timestampUtc"), Index("speciesCommon")],
)
data class DetectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampUtc: Long,
    val speciesCommon: String,
    val speciesScientific: String,
    val confidence: Float,
    val latitude: Double?,
    val longitude: Double?,
    val modelName: String,
    val modelVersion: String,
    /** Path to the saved WAV clip relative to the app's filesDir. */
    val clipPath: String,
    val weatherTempC: Float?,
    val weatherCondition: String?,
)
