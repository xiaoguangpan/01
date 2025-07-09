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
    private val TAG = Constants.LogTags.MOCK_LOCATION_MANAGER
    private val ALL_PROVIDERS = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
    )

    @Volatile
    private var executor: ScheduledExecutorService? = null

    @Volatile
    private var isRunning = false

    fun start(context: Context, lat: Double, lng: Double): Boolean {
        Log.d(TAG, "🚀 开始设置模拟定位: $lat, $lng")

        if (Shizuku.checkSelfPermission() != Constants.RequestCodes.SHIZUKU_PERMISSION) {
            Log.w(TAG, "❌ Shizuku权限不足，无法启动")
            return false
        }

        // 确保先停止之前的任务
        stop(context)

        synchronized(this) {
            if (isRunning) {
                Log.w(TAG, "模拟定位已在运行中")
                return true
            }

            isRunning = true
            executor = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "MockLocationThread").apply {
                    isDaemon = true
                }
            }

            executor?.scheduleAtFixedRate({
                if (!isRunning) return@scheduleAtFixedRate

                try {
                    ALL_PROVIDERS.forEach { provider ->
                        val location = createLocation(provider, lat, lng)
                        setLocationForProvider(context, provider, location)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 设置模拟位置失败: ${e.message}", e)
                }
            }, 0, Constants.Timing.LOCATION_UPDATE_INTERVAL, TimeUnit.MILLISECONDS)
        }

        Log.d(TAG, "🎯 模拟定位已启动")
        return true
    }

    private fun setLocationForProvider(context: Context, provider: String, location: Location) {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.setTestProviderLocation(provider, location)
        } else {
            ShizukuManager.setMockLocation(location)
        }
    }

    fun stop(context: Context) {
        synchronized(this) {
            isRunning = false

            executor?.let { exec ->
                try {
                    exec.shutdown()
                    if (!exec.awaitTermination(1, TimeUnit.SECONDS)) {
                        exec.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    exec.shutdownNow()
                    Thread.currentThread().interrupt()
                }
            }
            executor = null
        }

        try {
            ALL_PROVIDERS.forEach { provider ->
                removeTestProviderForProvider(context, provider)
            }
            Log.d(TAG, "🛑 模拟定位已停止")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 停止模拟定位失败: ${e.message}", e)
        }
    }

    private fun removeTestProviderForProvider(context: Context, provider: String) {
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                locationManager.removeTestProvider(provider)
            } else {
                ShizukuManager.removeTestProvider(provider)
            }
        } catch (e: Exception) {
            Log.w(TAG, "移除测试提供者失败 $provider: ${e.message}")
        }
    }

    fun isRunning(): Boolean = isRunning

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
