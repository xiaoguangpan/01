package com.example.locationsimulator.network

import retrofit2.http.GET
import retrofit2.http.Query

interface BaiduApiService {
    // Using Place Suggestion API now
    @GET("/place/v3/suggestion")
    suspend fun getSuggestions(
        @Query("query") query: String,
        @Query("region") region: String, // We can limit search to a city
        @Query("ak") ak: String,
        @Query("sn") sn: String,
        @Query("output") output: String = "json",
        @Query("city_limit") cityLimit: Boolean = true
    ): SuggestionResponse
}
