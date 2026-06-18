package com.example.birdfinder.weather

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class CurrentWeatherDto(
    val weather: List<WeatherCondition> = emptyList(),
    val main: MainBlock = MainBlock(),
)

@JsonClass(generateAdapter = false)
data class WeatherCondition(
    val id: Int = 0,
    val main: String = "",
    val description: String = "",
)

@JsonClass(generateAdapter = false)
data class MainBlock(
    val temp: Double = Double.NaN,
)

data class WeatherSnapshot(
    val temperatureC: Float?,
    val condition: String?,
)
