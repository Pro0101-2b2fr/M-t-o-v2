package com.example

import android.content.Context
import com.example.data.api.ExternalWeatherService
import com.example.data.api.OpenMeteoService
import com.example.data.db.WeatherDatabase
import com.example.data.manager.ApiQuotaManager
import com.example.data.manager.LocationManager
import com.example.data.repository.WeatherRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AppContainer(private val context: Context) {
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val openMeteoService: OpenMeteoService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/v1/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenMeteoService::class.java)
    }

    val externalService: ExternalWeatherService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ExternalWeatherService::class.java)
    }

    val database: WeatherDatabase by lazy {
        WeatherDatabase.getDatabase(context)
    }

    val weatherDao by lazy {
        database.weatherDao()
    }

    val quotaManager: ApiQuotaManager by lazy {
        ApiQuotaManager(context)
    }

    val locationManager: LocationManager by lazy {
        LocationManager(context)
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepository(
            openMeteoService = openMeteoService,
            externalService = externalService,
            weatherDao = weatherDao,
            quotaManager = quotaManager
        )
    }
}
