package com.example.locationsimulator.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

object MockLocationManager {
    private const val TAG = "MockLocationManager"

    // 支持的所有定位提供者 - 覆盖所有可能的定位源
    private val ALL_PROVIDERS = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )

    fun start(context: Context, lat: Double, lng: Double) {
        try {
            Log.d(TAG, "🚀 开始设置全面系统级模拟定位: $lat, $lng")
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // 检查权限
            if (!isCurrentAppSelectedAsMockLocationApp(context)) {
                Log.e(TAG, "❌ 应用未被设置为模拟定位应用")
                throw SecurityException("应用未被设置为模拟定位应用")
            }

            // 为所有提供者设置模拟位置
            var successCount = 0
            ALL_PROVIDERS.forEach { provider ->
                try {
                    setupProviderMockLocation(locationManager, provider, lat, lng)
                    successCount++
                    Log.d(TAG, "✅ $provider 模拟定位设置成功")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ $provider 模拟定位设置失败: ${e.message}")
                }
            }

            if (successCount > 0) {
                Log.d(TAG, "🎯 模拟定位设置完成，成功设置 $successCount/${ ALL_PROVIDERS.size} 个提供者")

                // 持续更新位置信息，确保所有应用都能获取到
                startContinuousLocationUpdate(context, lat, lng)
            } else {
                throw Exception("所有定位提供者设置失败")
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ 权限不足，无法设置模拟位置: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ 设置模拟位置失败: ${e.message}")
            throw e
        }
    }

    private fun setupProviderMockLocation(
        locationManager: LocationManager,
        provider: String,
        lat: Double,
        lng: Double
    ) {
        // 移除现有的测试提供者
        try {
            locationManager.removeTestProvider(provider)
            Log.d(TAG, "🗑️ 移除现有的 $provider 测试提供者")
        } catch (e: Exception) {
            // 忽略，可能不存在
        }

        // 添加测试提供者
        locationManager.addTestProvider(
            provider,
            false, // requiresNetwork
            false, // requiresSatellite
            false, // requiresCell
            false, // hasMonetaryCost
            true,  // supportsAltitude
            true,  // supportsSpeed
            true,  // supportsBearing
            android.location.Criteria.POWER_LOW,
            android.location.Criteria.ACCURACY_FINE
        )

        // 启用测试提供者
        locationManager.setTestProviderEnabled(provider, true)

        // 创建高精度模拟位置
        val mockLocation = createHighPrecisionLocation(provider, lat, lng)

        // 设置模拟位置
        locationManager.setTestProviderLocation(provider, mockLocation)

        Log.d(TAG, "📍 $provider 位置已设置: ($lat, $lng)")
    }

    private fun createHighPrecisionLocation(provider: String, lat: Double, lng: Double): Location {
        return Location(provider).apply {
            latitude = lat
            longitude = lng

            // 根据提供者类型设置不同的精度
            accuracy = when (provider) {
                LocationManager.GPS_PROVIDER -> 1.0f      // GPS最高精度
                LocationManager.NETWORK_PROVIDER -> 5.0f   // 网络定位中等精度
                LocationManager.PASSIVE_PROVIDER -> 3.0f   // 被动定位
                else -> 1.0f
            }

            altitude = 50.0 // 模拟海拔50米
            bearing = 0.0f
            speed = 0.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

            // Android 8.0+ 的额外精度信息
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                verticalAccuracyMeters = 3.0f
                speedAccuracyMetersPerSecond = 0.1f
                bearingAccuracyDegrees = 1.0f
            }
        }
    }

    private fun startContinuousLocationUpdate(context: Context, lat: Double, lng: Double) {
        // 使用Handler持续更新位置，确保所有应用都能获取到最新位置
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val updateRunnable = object : Runnable {
            override fun run() {
                try {
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    ALL_PROVIDERS.forEach { provider ->
                        try {
                            val location = createHighPrecisionLocation(provider, lat, lng)
                            locationManager.setTestProviderLocation(provider, location)
                        } catch (e: Exception) {
                            // 忽略单个提供者的错误
                        }
                    }
                    // 每5秒更新一次
                    handler.postDelayed(this, 5000)
                } catch (e: Exception) {
                    Log.w(TAG, "持续位置更新失败: ${e.message}")
                }
            }
        }
        handler.post(updateRunnable)
        Log.d(TAG, "🔄 开始持续位置更新")
    }

    fun stop(context: Context) {
        try {
            Log.d(TAG, "🛑 停止所有模拟定位提供者")
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            var stoppedCount = 0
            ALL_PROVIDERS.forEach { provider ->
                try {
                    locationManager.removeTestProvider(provider)
                    stoppedCount++
                    Log.d(TAG, "✅ 已停止 $provider")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ 停止 $provider 失败: ${e.message}")
                }
            }

            Log.d(TAG, "🏁 模拟定位停止完成，成功停止 $stoppedCount/${ALL_PROVIDERS.size} 个提供者")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 停止模拟定位失败: ${e.message}")
        }
    }

    fun isMockLocationEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION) != 0
        } catch (e: Exception) {
            // 在Android 6.0+，这个设置可能不存在，检查开发者选项
            try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) != 0
            } catch (e2: Exception) {
                false
            }
        }
    }

    fun isCurrentAppSelectedAsMockLocationApp(context: Context): Boolean {
        return try {
            Log.d("MockLocationManager", "检查模拟定位应用状态...")

            // Android 6.0+ 检查应用是否有系统级权限
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                android.os.Process.myUid(),
                context.packageName
            )

            val isAllowed = mode == android.app.AppOpsManager.MODE_ALLOWED
            Log.d("MockLocationManager", "AppOps检查结果: mode=$mode, isAllowed=$isAllowed")

            if (isAllowed) {
                return true
            }

            // 如果AppOps检查失败，尝试其他方法
            try {
                val selectedApp = Settings.Secure.getString(context.contentResolver, "mock_location_app")
                Log.d("MockLocationManager", "Settings检查结果: selectedApp=$selectedApp, packageName=${context.packageName}")
                val isSelected = selectedApp == context.packageName
                if (isSelected) return true
            } catch (e2: Exception) {
                Log.w("MockLocationManager", "Settings检查失败: ${e2.message}")
            }

            // 最后检查开发者选项是否开启
            val devEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
            Log.d("MockLocationManager", "开发者选项状态: $devEnabled")

            return false // 明确返回false，不再假设已设置

        } catch (e: Exception) {
            Log.e("MockLocationManager", "检查模拟定位应用状态失败: ${e.message}")
            return false
        }
    }
}
