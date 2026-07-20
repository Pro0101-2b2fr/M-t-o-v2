package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.CachedWeatherEntity
import com.example.data.model.FavoriteCity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {

    // Favorite Cities
    @Query("SELECT * FROM favorite_cities ORDER BY addedAt DESC")
    fun getAllFavoriteCitiesFlow(): Flow<List<FavoriteCity>>

    @Query("SELECT * FROM favorite_cities ORDER BY addedAt DESC")
    suspend fun getAllFavoriteCities(): List<FavoriteCity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteCity(city: FavoriteCity): Long

    @Delete
    suspend fun deleteFavoriteCity(city: FavoriteCity)

    @Query("DELETE FROM favorite_cities WHERE name = :name")
    suspend fun deleteFavoriteCityByName(name: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_cities WHERE name = :name LIMIT 1)")
    suspend fun isCityFavorite(name: String): Boolean

    // Weather Cache
    @Query("SELECT * FROM weather_cache WHERE cityId = :cityId LIMIT 1")
    suspend fun getCachedWeather(cityId: String): CachedWeatherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedWeather(cache: CachedWeatherEntity)

    @Query("DELETE FROM weather_cache WHERE cityId = :cityId")
    suspend fun deleteCachedWeather(cityId: String)

    @Query("DELETE FROM weather_cache")
    suspend fun clearCache()
}
