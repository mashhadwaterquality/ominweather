package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Standard Weather Codes mapped to icons & Persian descriptions
enum class WeatherType(val code: Int, val description: String, val iconRes: String) {
    CLEAR_SKY(0, "آسمان صاف", "☀️"),
    MAINLY_CLEAR(1, "غالباً صاف", "🌤️"),
    PARTLY_CLOUDY(2, "نیمه ابری", "⛅"),
    OVERCAST(3, "تمام ابری", "☁️"),
    FOGGY(45, "مه‌آلود", "🌫️"),
    DEPOSITING_RIME_FOG(48, "مه غلیظ یخ‌زده", "🌫️❄️"),
    LIGHT_DRIZZLE(51, "نم‌نم باران خفیف", "🌦️"),
    MODERATE_DRIZZLE(53, "نم‌نم باران متوسط", "🌦️"),
    DENSE_DRIZZLE(55, "نم‌نم باران شدید", "🌧️"),
    LIGHT_FREEZING_DRIZZLE(56, "نم‌نم باران یخ‌زده خفیف", "🌧️❄️"),
    DENSE_FREEZING_DRIZZLE(57, "نم‌نم باران یخ‌زده شدید", "🌧️❄️"),
    Slight_RAIN(61, "باران خفیف", "🌧️"),
    MODERATE_RAIN(63, "باران متوسط", "🌧️"),
    HEAVY_RAIN(65, "باران شدید", "🌧️⛈️"),
    LIGHT_FREEZING_RAIN(66, "باران یخ‌زده خفیف", "🌧️❄️"),
    HEAVY_FREEZING_RAIN(67, "باران یخ‌زده شدید", "🌧️❄️"),
    LIGHT_SNOW(71, "بارش برف خفیف", "❄️"),
    MODERATE_SNOW(73, "بارش برف متوسط", "❄️"),
    HEAVY_SNOW(75, "بارش سنگین برف", "❄️☃️"),
    SNOW_Grains(77, "دانه‌های برف", "❄️"),
    LIGHT_RAIN_SHOWERS(80, "رگبار باران خفیف", "🌦️"),
    MODERATE_RAIN_SHOWERS(81, "رگبار باران متوسط", "🌧️"),
    HEAVY_RAIN_SHOWERS(82, "رگبار باران شدید", "🌧️⛈️"),
    LIGHT_SNOW_SHOWERS(85, "رگبار برف خفیف", "❄️"),
    HEAVY_SNOW_SHOWERS(86, "رگبار برف شدید", "❄️☃️"),
    THUNDERSTORM(95, "رعد و برق", "⚡⛈️"),
    THUNDERSTORM_HAIL_LIGHT(96, "رعد و برق خفیف همراه با تگرگ", "⛈️🌨️"),
    THUNDERSTORM_HAIL_HEAVY(99, "رعد و برق شدید همراه با تگرگ", "⛈️🌨️");

    companion object {
        fun fromCode(code: Int): WeatherType {
            return values().firstOrNull { it.code == code } ?: OVERCAST
        }
    }
}

// Retrofit geocoding models to search cities
@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    @Json(name = "results") val results: List<GeocodedCity>?
)

@JsonClass(generateAdapter = true)
data class GeocodedCity(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "country") val country: String?,
    @Json(name = "admin1") val admin1: String? // State/Province
)

// Retrofit weather API responses
@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "current") val current: CurrentWeather?,
    @Json(name = "daily") val daily: DailyForecast?
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    @Json(name = "temperature_2m") val temperature: Double,
    @Json(name = "relative_humidity_2m") val humidity: Int,
    @Json(name = "apparent_temperature") val apparentTemperature: Double,
    @Json(name = "is_day") val isDay: Int,
    @Json(name = "precipitation") val precipitation: Double,
    @Json(name = "weather_code") val weatherCode: Int,
    @Json(name = "wind_speed_10m") val windSpeed: Double
)

@JsonClass(generateAdapter = true)
data class DailyForecast(
    @Json(name = "time") val time: List<String>,
    @Json(name = "weather_code") val weatherCode: List<Int>,
    @Json(name = "temperature_2m_max") val tempMax: List<Double>,
    @Json(name = "temperature_2m_min") val tempMin: List<Double>,
    @Json(name = "sunrise") val sunrise: List<String>?,
    @Json(name = "sunset") val sunset: List<String>?,
    @Json(name = "uv_index_max") val uvIndex: List<Double>?,
    @Json(name = "precipitation_sum") val precipitationSum: List<Double>?
)

// Supported Weather Models / Sources
enum class WeatherSource(
    val id: String,
    val nameFa: String,
    val nameEn: String,
    val description: String,
    val reliabilityIndex: Double // 0.0 to 1.0 representation of relative historical rating
) {
    IRIMO("irimo", "سازمان هواشناسی ایران", "IRIMO", "مدل منطقه‌ای کالیبره‌شده با ایستگاه‌های زمینی کشور", 0.88),
    ECMWF("ecmwf", "مدل اروپایی ECMWF", "ECMWF", "معتبرترین مدل جهانی پیش‌بینی میان‌مدت جوی اروپا", 0.95),
    GFS("gfs", "مدل آمریکایی GFS", "GFS", "مدل جهانی هوانوردی و ایالات متحده با تفکیک باد بالا", 0.91),
    ICON("icon", "مدل آلمانی DWD ICON", "German ICON", "مدل هواشناسی بسیار دقیق آلمان مخصوص منطقه اوراسیا", 0.93)
}

// Specific weather parameters computed for a Source
data class SourceWeatherData(
    val source: WeatherSource,
    val temperature: Double,
    val humidity: Int,
    val apparentTemperature: Double,
    val windSpeed: Double,
    val weatherCode: Int,
    val weatherDesc: String,
    val icon: String
)

// Multi-day forecast items
data class DayForecastItem(
    val date: String,
    val dayOfWeek: String, // e.g. "شنبه"
    val weatherCode: Int,
    val tempMin: Double,
    val tempMax: Double,
    val weatherDesc: String,
    val icon: String,
    val precipitationSum: Double
)

// Consensus Statistics: "جمع‌بندی آمار دریافتی از منابع مختلف در یک نگاه"
data class WeatherConsensus(
    val avgTemperature: Double,
    val maxTemperature: Double, // Highest predicted by any model
    val minTemperature: Double, // Lowest predicted by any model
    val tempStdDev: Double, // Consensus dispersion/agreement metric (standard deviation)
    val avgHumidity: Double,
    val avgWindSpeed: Double,
    val consensusWeatherCode: Int,
    val consensusIcon: String,
    val consensusDesc: String,
    val degreeOfAgreement: String // e.g., "بسیار بالا", "متوسط", "اختلاف نظر جزئی"
)
