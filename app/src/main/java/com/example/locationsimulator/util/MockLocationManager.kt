package com.example.locationsimulator.util

import android.content.Context
import android.util.Log
import rikka.shizuku.Shizuku
import androidx.compose.runtime.NoLiveLiterals

@NoLiveLiterals
object MockLocationManager {
    private val TAG = Constants.LogTags.MOCK_LOCATION_MANAGER

    @Volatile
    private var isRunning = false

    fun start(context: Context, lat: Double, lng: Double): Boolean {
        Log.e(TAG, "🚀🚀🚀 MockLocationManager.start() 被调用！")
        Log.e(TAG, "📍 目标坐标: lat=$lat, lng=$lng")
        Log.e(TAG, "🔧 使用Shizuku UserService模式进行位置模拟")

        // 检查Shizuku权限（正确的权限检查方式）
        val permissionStatus = Shizuku.checkSelfPermission()
        Log.e(TAG, "🔐 Shizuku权限检查: $permissionStatus")

        if (permissionStatus != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ Shizuku权限不足，无法启动增强模式")
            Log.e(TAG, "💡 当前权限状态: $permissionStatus")
            Log.e(TAG, "💡 期望状态: ${android.content.pm.PackageManager.PERMISSION_GRANTED}")
            return false
        }

        Log.e(TAG, "✅ Shizuku权限检查通过，开始使用增强模式")

        // 暂时使用旧的Shizuku实现，而不是UserService
        Log.e(TAG, "🔧 使用Shizuku增强模式（旧实现）")

        return try {
            // 使用旧的Shizuku实现进行位置模拟
            val result = startShizukuMockLocation(context, lat, lng)
            if (result) {
                isRunning = true
                Log.e(TAG, "🎯🎯🎯 Shizuku增强模式启动成功！")
            } else {
                Log.e(TAG, "❌❌❌ Shizuku增强模式启动失败")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ Shizuku增强模式异常: ${e.javaClass.simpleName} - ${e.message}", e)
            val fallbackResult = false
            fallbackResult
        }

        /*
        return try {
            // 绑定UserService
            if (!ShizukuUserServiceManager.isServiceBound()) {
                Log.e(TAG, "🔗 绑定UserService...")
                if (!ShizukuUserServiceManager.bindUserService(context)) {
                    Log.e(TAG, "❌ UserService绑定失败")
                    return userServiceResult
                }
            }

            // 启动位置模拟
            val result = ShizukuUserServiceManager.startMockLocation(lat, lng)
            if (result) {
                isRunning = true
                Log.e(TAG, "🎯🎯🎯 UserService位置模拟启动成功！")
            } else {
                Log.e(TAG, "❌❌❌ UserService位置模拟启动失败")
            }
            result

        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ UserService模式异常: ${e.javaClass.simpleName} - ${e.message}", e)
            userServiceResult
        }
        */

        // UserService模式已经在上面处理完成
        Log.e(TAG, "🎯🎯🎯 MockLocationManager.start() 完成")
        val finalResult = true
        return finalResult
    }

    /**
     * 使用旧的Shizuku实现进行位置模拟
     */
    private fun startShizukuMockLocation(context: Context, lat: Double, lng: Double): Boolean {
        Log.e(TAG, "🔧 开始Shizuku增强模式位置模拟...")

        return try {
            // 使用Shizuku权限添加测试提供者
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // 为所有提供者添加测试提供者
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            var successCount = 0
            for (provider in providers) {
                try {
                    // 使用Shizuku权限调用系统API
                    locationManager.addTestProvider(
                        provider,
                        false, false, false, false, false, true, true,
                        android.location.Criteria.POWER_LOW,
                        android.location.Criteria.ACCURACY_FINE
                    )
                    locationManager.setTestProviderEnabled(provider, true)

                    // 创建位置对象
                    val location = android.location.Location(provider).apply {
                        latitude = lat
                        longitude = lng
                        accuracy = 1.0f
                        time = System.currentTimeMillis()
                        elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                    }

                    locationManager.setTestProviderLocation(provider, location)
                    successCount++
                    Log.e(TAG, "✅ Shizuku增强模式: $provider 提供者设置成功")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Shizuku增强模式: $provider 提供者设置失败: ${e.message}")
                }
            }

            val success = successCount > 0
            if (success) {
                Log.e(TAG, "🎯 Shizuku增强模式: 成功设置 $successCount/$providers.size 个提供者")
            } else {
                Log.e(TAG, "❌ Shizuku增强模式: 所有提供者设置失败")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Shizuku增强模式异常: ${e.message}", e)
            val errorResult = false
            errorResult
        }
    }

    // UserService模式下，所有提供者操作都在UserService中处理
    // 这些方法不再需要

    // UserService模式下，位置设置在UserService中处理

    fun stop(context: Context) {
        synchronized(this) {
            isRunning = false
            // UserService模式不使用executor
        }

        try {
            Log.e(TAG, "🛑🛑🛑 停止Shizuku增强模式模拟定位...")

            // 停止旧的Shizuku实现
            stopShizukuMockLocation(context)

            Log.e(TAG, "🛑🛑🛑 Shizuku增强模式模拟定位已停止")
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ 停止模拟定位失败: ${e.javaClass.simpleName} - ${e.message}", e)
        }
    }

    fun isRunning(): Boolean = isRunning

    /**
     * 停止旧的Shizuku实现
     */
    private fun stopShizukuMockLocation(context: Context) {
        Log.e(TAG, "🛑 停止Shizuku增强模式...")

        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            for (provider in providers) {
                try {
                    locationManager.removeTestProvider(provider)
                    Log.e(TAG, "✅ Shizuku增强模式: $provider 提供者已移除")
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ Shizuku增强模式: $provider 提供者移除失败: ${e.message}")
                }
            }

            Log.e(TAG, "✅ Shizuku增强模式停止完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 停止Shizuku增强模式失败: ${e.message}", e)
        }
    }

    // UserService模式下，所有位置操作都在UserService中处理
}
