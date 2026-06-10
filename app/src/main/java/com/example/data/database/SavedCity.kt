package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_cities")
data class SavedCity(
    @PrimaryKey val id: String, // Combined string as key, e.g. "tehran" or "lat_lon"
    val nameEn: String,
    val nameFa: String,
    val latitude: Double,
    val longitude: Double,
    val country: String = "Iran",
    val adminArea: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
