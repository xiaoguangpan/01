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

                // 关键：尝试禁用真实的位置提供者，让测试提供者优先
                try {
                    disableRealProviderForProvider(context, provider)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ 无法禁用真实提供者 $provider: ${e.message}")
                    // 不抛出异常，因为这不是致命错误
                }
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
                    Log.d(TAG, "🔄 更新模拟位置: $lat, $lng")
                    var successCount = 0
                    var failureCount = 0

                    ALL_PROVIDERS.forEach { provider ->
                        try {
                            val location = createLocation(provider, lat, lng)
                            setLocationForProvider(context, provider, location)
                            successCount++
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 提供者 $provider 位置设置失败: ${e.message}")
                            failureCount++
                        }
                    }

                    Log.d(TAG, "📊 位置更新结果: 成功=$successCount, 失败=$failureCount")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 模拟位置更新循环异常: ${e.message}", e)
                }
            }, 0, Constants.Timing.LOCATION_UPDATE_INTERVAL, TimeUnit.MILLISECONDS)
        }

        Log.d(TAG, "🎯 模拟定位已启动")
        return true
    }

    private fun addTestProviderForProvider(context: Context, provider: String) {
        try {
            // 先尝试移除可能存在的旧测试提供者
            try {
                if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    locationManager.removeTestProvider(provider)
                } else {
                    ShizukuManager.removeTestProvider(provider)
                }
                Log.d(TAG, "🔧 移除旧测试提供者: $provider")
            } catch (e: Exception) {
                // 忽略移除失败，可能是因为提供者不存在
                Log.d(TAG, "🔧 移除旧测试提供者失败（可能不存在）: $provider")
            }

            // 添加新的测试提供者
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                locationManager.addTestProvider(provider, false, false, false, false, true, true, true, 1, 1)
            } else {
                ShizukuManager.addTestProvider(provider)
            }
            Log.d(TAG, "✅ 添加测试提供者成功: $provider")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 添加测试提供者失败 $provider: ${e.javaClass.simpleName} - ${e.message}", e)
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

    private fun disableRealProviderForProvider(context: Context, provider: String) {
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                // 对于旧版本Shizuku，我们无法直接禁用真实提供者
                Log.d(TAG, "🔧 旧版Shizuku，跳过禁用真实提供者: $provider")
            } else {
                // 使用Shizuku尝试禁用真实的位置提供者
                ShizukuManager.disableRealProvider(provider)
                Log.d(TAG, "✅ 尝试禁用真实提供者: $provider")
            }
        } catch (e: Exception) {
            Log.w(TAG, "禁用真实提供者失败 $provider: ${e.message}")
            throw e
        }
    }

    private fun enableRealProviderForProvider(context: Context, provider: String) {
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                // 对于旧版本Shizuku，我们无法直接控制真实提供者
                Log.d(TAG, "🔧 旧版Shizuku，跳过启用真实提供者: $provider")
            } else {
                // 使用Shizuku尝试重新启用真实的位置提供者
                ShizukuManager.enableRealProvider(provider)
                Log.d(TAG, "✅ 尝试重新启用真实提供者: $provider")
            }
        } catch (e: Exception) {
            Log.w(TAG, "重新启用真实提供者失败 $provider: ${e.message}")
            throw e
        }
    }

    private fun setLocationForProvider(context: Context, provider: String, location: Location) {
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                locationManager.setTestProviderLocation(provider, location)
                Log.d(TAG, "✅ 设置模拟位置成功 (旧版Shizuku): $provider -> ${location.latitude}, ${location.longitude}")
            } else {
                // 确保location对象的provider字段正确设置
                val locationWithProvider = Location(provider).apply {
                    latitude = location.latitude
                    longitude = location.longitude
                    accuracy = location.accuracy
                    altitude = location.altitude
                    time = location.time
                    elapsedRealtimeNanos = location.elapsedRealtimeNanos
                }
                ShizukuManager.setMockLocation(locationWithProvider)
                Log.d(TAG, "✅ 设置模拟位置成功 (Shizuku增强): $provider -> ${location.latitude}, ${location.longitude}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 设置模拟位置失败 $provider: ${e.javaClass.simpleName} - ${e.message}", e)
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
                // 先禁用测试提供者
                try {
                    enableTestProviderForProvider(context, provider, false)
                } catch (e: Exception) {
                    Log.w(TAG, "禁用测试提供者失败 $provider: ${e.message}")
                }

                // 移除测试提供者
                removeTestProviderForProvider(context, provider)

                // 尝试重新启用真实提供者
                try {
                    enableRealProviderForProvider(context, provider)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ 无法重新启用真实提供者 $provider: ${e.message}")
                    // 不抛出异常，因为这不是致命错误
                }
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
