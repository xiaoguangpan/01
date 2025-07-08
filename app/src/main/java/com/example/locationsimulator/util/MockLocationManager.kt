package com.example.locationsimulator.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object MockLocationManager {
    private const val TAG = "MockLocationManager"
    private val ALL_PROVIDERS = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
    )

    private var executor: ScheduledExecutorService? = null

    fun start(context: Context, lat: Double, lng: Double) {
        Log.d(TAG, "🚀 开始设置模拟定位: $lat, $lng")

        if (Shizuku.checkSelfPermission() != 0) {
            Shizuku.requestPermission(0)
            return
        }

        stop(context)

        executor = Executors.newSingleThreadScheduledExecutor()
        executor?.scheduleAtFixedRate({
            try {
                ALL_PROVIDERS.forEach { provider ->
                    val location = createLocation(provider, lat, lng)
                    if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        locationManager.setTestProviderLocation(provider, location)
                    } else {
                        ShizukuManager.setMockLocation(location)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 设置模拟位置失败: ${e.message}", e)
            }
        }, 0, 1, TimeUnit.SECONDS)

        Log.d(TAG, "🎯 模拟定位已启动")
    }

    fun stop(context: Context) {
        executor?.shutdownNow()
        executor = null

        try {
            ALL_PROVIDERS.forEach { provider ->
                if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    locationManager.removeTestProvider(provider)
                } else {
                    ShizukuManager.removeTestProvider(provider)
                }
            }
            Log.d(TAG, "🛑 模拟定位已停止")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 停止模拟定位失败: ${e.message}", e)
        }
    }

    private fun createLocation(provider: String, lat: Double, lng: Double): Location {
        return Location(provider).apply {
            latitude = lat
            longitude = lng
            accuracy = 1.0f
            altitude = 50.0
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
    }
}
