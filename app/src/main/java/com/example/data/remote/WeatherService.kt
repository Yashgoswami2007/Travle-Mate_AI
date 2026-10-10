package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LiveWeatherResult(
    val temperature: String,
    val summary: String,
    val rainProbability: String
)

class WeatherService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun getLiveWeather(latitude: Double, longitude: Double): LiveWeatherResult = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current_weather=true&daily=precipitation_probability_max&timezone=auto"
            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val root = JSONObject(body)
                    val current = root.optJSONObject("current_weather")
                    val temp = current?.optDouble("temperature", 26.0) ?: 26.0
                    val weatherCode = current?.optInt("weathercode", 0) ?: 0

                    val daily = root.optJSONObject("daily")
                    val rainProbArray = daily?.optJSONArray("precipitation_probability_max")
                    val rainProb = rainProbArray?.optInt(0, 10) ?: 10

                    val summary = weatherCodeToString(weatherCode)
                    return@withContext LiveWeatherResult(
                        temperature = "${temp.toInt()}°C",
                        summary = summary,
                        rainProbability = "$rainProb%"
                    )
                }
            }
        } catch (_: Exception) {}

        // Fallback default if network unavailable
        LiveWeatherResult(
            temperature = "--",
            summary = "Weather unavailable",
            rainProbability = "--"
        )
    }

    private fun weatherCodeToString(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1, 2, 3 -> "Mainly clear, pleasant"
            45, 48 -> "Misty & foggy"
            51, 53, 55 -> "Light drizzle"
            61, 63, 65 -> "Rainy intervals"
            71, 73, 75 -> "Light snow"
            80, 81, 82 -> "Scattered rain showers"
            95, 96, 99 -> "Thunderstorm warning"
            else -> "Partly cloudy"
        }
    }
}
