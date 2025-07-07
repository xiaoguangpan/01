package com.example.locationsimulator.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

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
