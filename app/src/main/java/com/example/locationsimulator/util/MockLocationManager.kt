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
        LocationManager.PASSIVE_PROVIDER,
        "fused" // Google Play Services Fused Location Provider
    )

    fun start(context: Context, lat: Double, lng: Double) {
        try {
            Log.d(TAG, "🚀 开始设置全面系统级模拟定位: $lat, $lng")

            // 设备兼容性检查
            val systemInfo = DeviceCompatibilityManager.getSystemInfo()
            Log.d(TAG, "检测到设备: ${systemInfo.brand} - ${systemInfo.systemName} ${systemInfo.systemVersion}")

            val (hasLimitations, limitationMsg) = DeviceCompatibilityManager.hasKnownLimitations()
            if (hasLimitations) {
                Log.w(TAG, "设备限制警告: $limitationMsg")
            }

            // HyperOS特殊处理
            if (systemInfo.brand == DeviceCompatibilityManager.DeviceBrand.XIAOMI_HYPEROS) {
                Log.w(TAG, "检测到HyperOS ${systemInfo.hyperOSVersion}，启用特殊处理模式")
                return startHyperOSCompatibleMockLocation(context, lat, lng, locationManager)
            }

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // 增强权限检查
            if (!isCurrentAppSelectedAsMockLocationApp(context)) {
                Log.e(TAG, "❌ 应用未被设置为模拟定位应用")
                Log.d(TAG, "设备特定设置指导:\n${DeviceCompatibilityManager.getBrandSpecificInstructions(context)}")
                throw SecurityException("应用未被设置为模拟定位应用")
            }

            // 启动传感器模拟
            SensorSimulationManager.startSensorSimulation(context, lat, lng)
            Log.d(TAG, "传感器模拟已启动")

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

    /**
     * HyperOS兼容的模拟定位启动方法
     */
    private fun startHyperOSCompatibleMockLocation(
        context: Context,
        lat: Double,
        lng: Double,
        locationManager: LocationManager
    ) {
        try {
            Log.d(TAG, "🔧 启动HyperOS兼容模式")

            // HyperOS需要更严格的权限检查
            if (!isHyperOSMockLocationEnabled(context)) {
                Log.e(TAG, "❌ HyperOS模拟定位权限未正确配置")
                throw SecurityException("HyperOS模拟定位权限未正确配置，请按照设置指导完成配置")
            }

            // 使用增强的提供者列表，包括HyperOS特有的
            val hyperOSProviders = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
                "fused"
            )

            var successCount = 0
            hyperOSProviders.forEach { provider ->
                try {
                    // HyperOS需要更精确的位置设置
                    setupHyperOSProviderMockLocation(locationManager, provider, lat, lng)
                    successCount++
                    Log.d(TAG, "✅ HyperOS模式: $provider 提供者设置成功")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ HyperOS模式: $provider 提供者设置失败: ${e.message}")
                }
            }

            if (successCount == 0) {
                throw Exception("所有HyperOS定位提供者设置失败")
            }

            // 启动HyperOS特殊的持续更新
            startHyperOSLocationUpdates(context, lat, lng, locationManager)

            Log.d(TAG, "🎯 HyperOS兼容模式启动成功，设置了 $successCount/${hyperOSProviders.size} 个提供者")

        } catch (e: Exception) {
            Log.e(TAG, "❌ HyperOS兼容模式启动失败: ${e.message}")
            throw e
        }
    }

    /**
     * 检查HyperOS模拟定位是否正确启用
     */
    private fun isHyperOSMockLocationEnabled(context: Context): Boolean {
        return try {
            // 基础权限检查
            val basicCheck = isCurrentAppSelectedAsMockLocationApp(context)
            if (!basicCheck) return false

            // HyperOS特殊检查
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                android.os.Process.myUid(),
                context.packageName
            )

            val isAllowed = mode == android.app.AppOpsManager.MODE_ALLOWED
            Log.d(TAG, "HyperOS权限检查: AppOps=$isAllowed")

            // 检查位置权限
            val hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            Log.d(TAG, "HyperOS权限检查: 位置权限=$hasLocationPermission")

            return isAllowed && hasLocationPermission

        } catch (e: Exception) {
            Log.e(TAG, "HyperOS权限检查失败: ${e.message}")
            false
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

        // 创建具有反检测特性的高精度模拟位置
        val mockLocation = AntiDetectionManager.createAntiDetectionLocation(provider, lat, lng)

        // 设置模拟位置
        locationManager.setTestProviderLocation(provider, mockLocation)

        Log.d(TAG, "📍 $provider 位置已设置: ($lat, $lng)")
    }

    /**
     * HyperOS特殊的提供者设置
     */
    private fun setupHyperOSProviderMockLocation(
        locationManager: LocationManager,
        provider: String,
        lat: Double,
        lng: Double
    ) {
        try {
            // 移除现有的测试提供者
            try {
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                // 忽略移除错误
            }

            // 添加测试提供者（HyperOS需要更完整的参数）
            locationManager.addTestProvider(
                provider,
                true,  // requiresNetwork
                true,  // requiresSatellite
                true,  // requiresCell
                false, // hasMonetaryCost
                true,  // supportsAltitude
                true,  // supportsSpeed
                true,  // supportsBearing
                android.location.Criteria.POWER_MEDIUM, // powerRequirement
                android.location.Criteria.ACCURACY_FINE  // accuracy
            )

            // 启用测试提供者
            locationManager.setTestProviderEnabled(provider, true)

            // 创建HyperOS优化的位置对象
            val location = createHyperOSOptimizedLocation(provider, lat, lng)

            // 设置位置
            locationManager.setTestProviderLocation(provider, location)

            Log.d(TAG, "HyperOS提供者 $provider 设置完成")

        } catch (e: Exception) {
            Log.e(TAG, "HyperOS提供者 $provider 设置失败: ${e.message}")
            throw e
        }
    }

    /**
     * 创建HyperOS优化的位置对象
     */
    private fun createHyperOSOptimizedLocation(provider: String, lat: Double, lng: Double): Location {
        return Location(provider).apply {
            latitude = lat
            longitude = lng
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

            // HyperOS需要更真实的精度值
            accuracy = when (provider) {
                LocationManager.GPS_PROVIDER -> 3.0f
                LocationManager.NETWORK_PROVIDER -> 10.0f
                "fused" -> 5.0f
                else -> 8.0f
            }

            // 设置其他参数以提高真实性
            speed = 0.0f
            bearing = 0.0f
            altitude = 50.0

            // Android 8.0+ 的额外参数
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                verticalAccuracyMeters = accuracy * 1.5f
                speedAccuracyMetersPerSecond = 0.1f
                bearingAccuracyDegrees = 10.0f
            }
        }
    }

    /**
     * 启动HyperOS特殊的位置更新
     */
    private fun startHyperOSLocationUpdates(
        context: Context,
        lat: Double,
        lng: Double,
        locationManager: LocationManager
    ) {
        // HyperOS需要持续的位置更新来维持模拟定位
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val updateRunnable = object : Runnable {
            override fun run() {
                try {
                    listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused").forEach { provider ->
                        try {
                            val location = createHyperOSOptimizedLocation(provider, lat, lng)
                            locationManager.setTestProviderLocation(provider, location)
                        } catch (e: Exception) {
                            // 忽略单个提供者的错误
                        }
                    }
                    // 每30秒更新一次
                    handler.postDelayed(this, 30000)
                } catch (e: Exception) {
                    Log.e(TAG, "HyperOS位置更新失败: ${e.message}")
                }
            }
        }
        handler.post(updateRunnable)

        Log.d(TAG, "HyperOS持续位置更新已启动")
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
                            val location = AntiDetectionManager.createAntiDetectionLocation(provider, lat, lng)
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

            // 停止传感器模拟
            SensorSimulationManager.stopSensorSimulation()
            Log.d(TAG, "传感器模拟已停止")

            // 清除反检测历史
            AntiDetectionManager.clearLocationHistory()
            Log.d(TAG, "反检测历史已清除")

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

            // Android 10+ 增强权限检查
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                Log.d("MockLocationManager", "Android 10+ 权限检查模式")

                // 检查后台定位权限
                val hasBackgroundLocation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else true

                Log.d("MockLocationManager", "后台定位权限: $hasBackgroundLocation")
            }

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
