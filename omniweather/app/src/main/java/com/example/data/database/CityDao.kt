package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CityDao {
    @Query("SELECT * FROM saved_cities ORDER BY isFavorite DESC, createdAt DESC")
    fun getAllSavedCities(): Flow<List<SavedCity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCity(city: SavedCity)

    @Delete
    suspend fun deleteCity(city: SavedCity)

    @Query("UPDATE saved_cities SET isFavorite = :isFav WHERE id = :cityId")
    suspend fun updateFavoriteStatus(cityId: String, isFav: Boolean)

    @Query("SELECT * FROM saved_cities WHERE id = :cityId LIMIT 1")
    suspend fun getCityById(cityId: String): SavedCity?
}
