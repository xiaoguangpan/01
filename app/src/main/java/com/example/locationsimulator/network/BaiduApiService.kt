package com.example.locationsimulator.network

import retrofit2.http.GET
import retrofit2.http.Query

interface BaiduApiService {
    @GET("/geocoding/v3/")
    suspend fun geocode(
        @Query("address") address: String,
        @Query("ak") ak: String,
        @Query("sn") sn: String,
        @Query("output") output: String = "json"
    ): GeocodingResponse
}
