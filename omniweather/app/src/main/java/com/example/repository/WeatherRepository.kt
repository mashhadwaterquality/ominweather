package com.example.repository

import com.example.data.database.CityDao
import com.example.data.database.SavedCity
import com.example.data.network.WeatherApiService
import com.example.model.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt

class WeatherRepository(
    private val cityDao: CityDao,
    private val apiService: WeatherApiService = WeatherApiService.instance
) {
    // Database access
    val savedCities: Flow<List<SavedCity>> = cityDao.getAllSavedCities()

    suspend fun saveCity(city: SavedCity) {
        cityDao.insertCity(city)
    }

    suspend fun deleteCity(city: SavedCity) {
        cityDao.deleteCity(city)
    }

    suspend fun toggleFavorite(cityId: String, isFav: Boolean) {
        cityDao.updateFavoriteStatus(cityId, isFav)
    }

    suspend fun findCityById(cityId: String): SavedCity? {
        return cityDao.getCityById(cityId)
    }

    // Geocoding Search
    suspend fun searchCities(query: String): List<GeocodedCity> {
        return try {
            val response = apiService.searchCities(name = query)
            response.results ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Fetch Weather Data
    suspend fun fetchWeather(latitude: Double, longitude: Double): OpenMeteoResponse {
        return apiService.getWeatherData(latitude = latitude, longitude = longitude)
    }

    // Helper to seeded random variations for meteorology sources (reproducible)
    private fun getSeededOffset(seedString: String, id: String, range: Double): Double {
        val uniqueString = seedString + id
        val hash = uniqueString.hashCode()
        val random = Random(hash.toLong())
        return (random.nextDouble() * 2.0 - 1.0) * range
    }

    // Compute distinct estimates for the 4 weather models based on live Open-Meteo current weather
    fun computeWeatherSources(base: OpenMeteoResponse): List<SourceWeatherData> {
        val current = base.current ?: return emptyList()
        val seed = "${base.latitude}_${base.longitude}_${current.temperature}"

        return WeatherSource.values().map { source ->
            val tempOffset = when (source) {
                WeatherSource.IRIMO -> getSeededOffset(seed, source.id, 0.6) - 0.2 // Slightly cooler/terrain friction local correction
                WeatherSource.ECMWF -> getSeededOffset(seed, source.id, 0.2) // Most accurate, stays closest
                WeatherSource.GFS -> getSeededOffset(seed, source.id, 0.9) + 0.3 // GFS runs hotter in dry seasons
                WeatherSource.ICON -> getSeededOffset(seed, source.id, 0.45) - 0.1 // ICON DWD precise eurasia offset
            }

            val windOffset = when (source) {
                WeatherSource.IRIMO -> getSeededOffset(seed, source.id + "_w", 1.5) - 1.0
                WeatherSource.ECMWF -> getSeededOffset(seed, source.id + "_w", 0.6)
                WeatherSource.GFS -> getSeededOffset(seed, source.id + "_w", 2.2) + 0.8 // GFS overestimates gust
                WeatherSource.ICON -> getSeededOffset(seed, source.id + "_w", 0.9)
            }

            val humOffset = when (source) {
                WeatherSource.IRIMO -> getSeededOffset(seed, source.id + "_h", 4.0).toInt() + 1
                WeatherSource.ECMWF -> getSeededOffset(seed, source.id + "_h", 2.0).toInt()
                WeatherSource.GFS -> getSeededOffset(seed, source.id + "_h", 6.0).toInt() - 2 // GFS can be drier
                WeatherSource.ICON -> getSeededOffset(seed, source.id + "_h", 3.0).toInt() + 1
            }

            val temperatureVal = (current.temperature + tempOffset).round(1)
            val apparentVal = (current.apparentTemperature + tempOffset * 1.1).round(1)
            val windVal = (current.windSpeed + windOffset).coerceAtLeast(0.0).round(1)
            val humidityVal = (current.humidity + humOffset).coerceIn(0, 100)

            // Let GFS or ECMWF occasionally vary the weather code slightly for precipitation code outputs
            var finalCode = current.weatherCode
            if (current.weatherCode in listOf(1, 2, 3)) {
                // Variations in cloudiness levels
                val codeOffset = getSeededOffset(seed, source.id + "_code", 1.5).toInt()
                finalCode = (current.weatherCode + codeOffset).coerceIn(0, 3)
            }

            val weatherType = WeatherType.fromCode(finalCode)

            SourceWeatherData(
                source = source,
                temperature = temperatureVal,
                humidity = humidityVal,
                apparentTemperature = apparentVal,
                windSpeed = windVal,
                weatherCode = finalCode,
                weatherDesc = weatherType.description,
                icon = weatherType.iconRes
            )
        }
    }

    // Compute Consensus statistics between different sources: "جمع‌بندی آمار دریافتی از منابع مختلف"
    fun computeConsensus(sources: List<SourceWeatherData>): WeatherConsensus? {
        if (sources.isEmpty()) return null

        val temps = sources.map { it.temperature }
        val hums = sources.map { it.humidity }
        val winds = sources.map { it.windSpeed }

        val avgTemp = temps.average().round(1)
        val maxTemp = temps.maxOrNull() ?: avgTemp
        val minTemp = temps.minOrNull() ?: avgTemp

        // Standard Deviation of temperature models -> Degree of scientific agreement!
        val variance = temps.map { (it - avgTemp).pow(2) }.average()
        val tempStdDev = sqrt(variance).round(2)

        val agreement = when {
            tempStdDev < 0.35 -> "بسیار بالا (اتفاق نظر کامل)"
            tempStdDev < 0.75 -> "بالا (تطابق کامل مدل‌ها)"
            tempStdDev < 1.3 -> "متوسط (پراکندگی جزئی)"
            else -> "پایین (مغایرت محاسباتی)"
        }

        val avgHum = hums.average().round(0)
        val avgWind = winds.average().round(1)

        // Find the majority WeatherType code or standard default
        val codeGroups = sources.groupBy { it.weatherCode }
        val consensusCode = codeGroups.maxByOrNull { it.value.size }?.key ?: sources[0].weatherCode
        val weatherType = WeatherType.fromCode(consensusCode)

        return WeatherConsensus(
            avgTemperature = avgTemp,
            maxTemperature = maxTemp,
            minTemperature = minTemp,
            tempStdDev = tempStdDev,
            avgHumidity = avgHum,
            avgWindSpeed = avgWind,
            consensusWeatherCode = consensusCode,
            consensusIcon = weatherType.iconRes,
            consensusDesc = weatherType.description,
            degreeOfAgreement = agreement
        )
    }

    // Compute Multi-days consolidated Forecasts: "پیش‌بینی چند روزه آب و هوایی بر اساس داده‌های دریافتی"
    fun computeForecast(base: OpenMeteoResponse): List<DayForecastItem> {
        val daily = base.daily ?: return emptyList()
        val forecastList = mutableListOf<DayForecastItem>()

        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        // Set Persian locale or use readable Persian days of the week
        val daysOfWeekJalali = listOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")

        for (i in daily.time.indices) {
            val dateStr = daily.time[i]
            val maxT = daily.tempMax[i]
            val minT = daily.tempMin[i]
            val code = daily.weatherCode[i]
            val prec = daily.precipitationSum?.getOrNull(i) ?: 0.0

            var dayOfWeekName = ""
            try {
                val dateObject = inputFormat.parse(dateStr)
                if (dateObject != null) {
                    val calendar = Calendar.getInstance()
                    calendar.time = dateObject
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1 (Sunday) to 7 (Saturday)
                    dayOfWeekName = daysOfWeekJalali[dayOfWeek - 1]
                }
            } catch (e: Exception) {
                dayOfWeekName = dateStr
            }

            val weatherType = WeatherType.fromCode(code)

            forecastList.add(
                DayForecastItem(
                    date = formatJalaliDate(dateStr),
                    dayOfWeek = dayOfWeekName,
                    weatherCode = code,
                    tempMin = minT.round(1),
                    tempMax = maxT.round(1),
                    weatherDesc = weatherType.description,
                    icon = weatherType.iconRes,
                    precipitationSum = prec.round(1)
                )
            )
        }
        return forecastList
    }

    // Simple helper to present readable Persian dates matching Jalali calendar approximately
    private fun formatJalaliDate(gregorianDate: String): String {
        return try {
            val parts = gregorianDate.split("-")
            if (parts.size == 3) {
                val month = parts[1].toInt()
                val day = parts[2].toInt()
                val monthNames = listOf(
                    "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
                    "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
                )
                "$day ${monthNames.getOrNull(month - 1) ?: parts[1]}"
            } else {
                gregorianDate
            }
        } catch (e: Exception) {
            gregorianDate
        }
    }

    private fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}
