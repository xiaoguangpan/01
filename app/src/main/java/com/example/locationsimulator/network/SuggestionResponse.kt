package com.example.locationsimulator.network

import com.google.gson.annotations.SerializedName

// Renamed from GeocodingResponse to reflect the new API
data class SuggestionResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("message") val message: String,
    @SerializedName("result") val result: List<SuggestionResult>?
)

data class SuggestionResult(
    @SerializedName("name") val name: String,
    @SerializedName("location") val location: Location?,
    @SerializedName("uid") val uid: String,
    @SerializedName("province") val province: String,
    @SerializedName("city") val city: String,
    @SerializedName("district") val district: String,
    @SerializedName("address") val address: String?
)

// Location class can be reused
data class Location(
    @SerializedName("lng") val lng: Double,
    @SerializedName("lat") val lat: Double
)
