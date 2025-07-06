package com.example.locationsimulator.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock

object MockLocationManager {
    private const val PROVIDER_NAME = LocationManager.GPS_PROVIDER

    fun start(context: Context, lat: Double, lng: Double) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 移除旧的，以防万一
        if (locationManager.getProvider(PROVIDER_NAME) != null) {
            locationManager.removeTestProvider(PROVIDER_NAME)
        }

        // 添加测试提供者
        locationManager.addTestProvider(
            PROVIDER_NAME,
            false, // requiresNetwork
            false, // requiresSatellite
            false, // requiresCell
            false, // hasMonetaryCost
            true,  // supportsAltitude
            true,  // supportsSpeed
            true,  // supportsBearing
            0,     // powerRequirement
            5      // accuracy
        )
        locationManager.setTestProviderEnabled(PROVIDER_NAME, true)

        // 创建并设置位置信息
        val mockLocation = Location(PROVIDER_NAME).apply {
            latitude = lat
            longitude = lng
            altitude = 0.0
            accuracy = 1.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }

        locationManager.setTestProviderLocation(PROVIDER_NAME, mockLocation)
    }

    fun stop(context: Context) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            if (locationManager.getProvider(PROVIDER_NAME) != null) {
                locationManager.removeTestProvider(PROVIDER_NAME)
            }
        } catch (e: Exception) {
            // Ignore, provider may not exist
        }
    }
}
