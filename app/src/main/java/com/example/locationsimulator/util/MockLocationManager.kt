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
        Log.d(TAG, "🚀 开始设置Shizuku增强模式模拟定位: $lat, $lng")

        // 检查Shizuku权限（正确的权限检查方式）
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "❌ Shizuku权限不足，无法启动增强模式")
            Log.w(TAG, "💡 当前权限状态: ${Shizuku.checkSelfPermission()}")
            return false
        }

        Log.d(TAG, "✅ Shizuku权限检查通过")

        // 确保先停止之前的任务
        stop(context)

        // 首先添加测试提供者
        Log.d(TAG, "🔧 添加测试提供者...")
        try {
            ALL_PROVIDERS.forEach { provider ->
                addTestProviderForProvider(context, provider)
                enableTestProviderForProvider(context, provider, true)
            }
            Log.d(TAG, "✅ 测试提供者添加完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 添加测试提供者失败: ${e.message}", e)
            return false
        }

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

    private fun addTestProviderForProvider(context: Context, provider: String) {
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                locationManager.addTestProvider(provider, false, false, false, false, true, true, true, 1, 1)
            } else {
                ShizukuManager.addTestProvider(provider)
            }
            Log.d(TAG, "✅ 添加测试提供者成功: $provider")
        } catch (e: Exception) {
            Log.w(TAG, "添加测试提供者失败 $provider: ${e.message}")
            throw e
        }
    }

    private fun enableTestProviderForProvider(context: Context, provider: String, enabled: Boolean) {
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                locationManager.setTestProviderEnabled(provider, enabled)
            } else {
                ShizukuManager.setProviderEnabled(provider, enabled)
            }
            Log.d(TAG, "✅ 设置测试提供者状态成功: $provider = $enabled")
        } catch (e: Exception) {
            Log.w(TAG, "设置测试提供者状态失败 $provider: ${e.message}")
        }
    }

    private fun setLocationForProvider(context: Context, provider: String, location: Location) {
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                locationManager.setTestProviderLocation(provider, location)
            } else {
                ShizukuManager.setMockLocation(location)
            }
        } catch (e: Exception) {
            Log.w(TAG, "设置模拟位置失败 $provider: ${e.message}")
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
            Log.d(TAG, "🔧 清理测试提供者...")
            ALL_PROVIDERS.forEach { provider ->
                // 先禁用提供者
                try {
                    enableTestProviderForProvider(context, provider, false)
                } catch (e: Exception) {
                    Log.w(TAG, "禁用测试提供者失败 $provider: ${e.message}")
                }

                // 然后移除提供者
                removeTestProviderForProvider(context, provider)
            }
            Log.d(TAG, "🛑 Shizuku增强模式模拟定位已停止")
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
