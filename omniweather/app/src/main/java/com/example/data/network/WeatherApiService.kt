package com.example.data.network

import com.example.model.GeocodingResponse
import com.example.model.OpenMeteoResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface WeatherApiService {
    @GET
    suspend fun getWeatherData(
        @Url url: String = "https://api.open-meteo.com/v1/forecast",
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_sum",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse

    @GET
    suspend fun searchCities(
        @Url url: String = "https://geocoding-api.open-meteo.com/v1/search",
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponse

    companion object {
        private val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        private val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        private val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()

        val instance: WeatherApiService by lazy {
            Retrofit.Builder()
                // Base URL is required by Retrofit but overridden by passing @Url parameters
                .baseUrl("https://api.open-meteo.com/")
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(WeatherApiService::class.java)
        }
    }
}
