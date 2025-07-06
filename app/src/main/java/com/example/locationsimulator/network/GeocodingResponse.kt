package com.example.locationsimulator.network

import com.google.gson.annotations.SerializedName

data class GeocodingResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("result") val result: GeocodingResult?
)

data class GeocodingResult(
    @SerializedName("location") val location: Location,
    @SerializedName("precise") val precise: Int,
    @SerializedName("confidence") val confidence: Int,
    @SerializedName("comprehension") val comprehension: Int,
    @SerializedName("level") val level: String
)

data class Location(
    @SerializedName("lng") val lng: Double,
    @SerializedName("lat") val lat: Double
)
