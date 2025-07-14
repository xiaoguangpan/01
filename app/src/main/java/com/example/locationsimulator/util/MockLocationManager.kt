package com.example.locationsimulator.util

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.util.Log
import rikka.shizuku.Shizuku
import androidx.compose.runtime.NoLiveLiterals

@NoLiveLiterals
object MockLocationManager {
    private val TAG = Constants.LogTags.MOCK_LOCATION_MANAGER

    @Volatile
    private var isRunning = false

    fun start(context: Context, lat: Double, lng: Double): Boolean {
        // 强制输出日志 - 确保代码更新
        android.util.Log.e("FORCE_DEBUG", "🚀🚀🚀 MockLocationManager.start() 被调用！ [版本2024-12-14-11:15]")
        android.util.Log.e("FORCE_DEBUG", "📍 目标坐标: lat=$lat, lng=$lng")
        android.util.Log.e("FORCE_DEBUG", "🔧 使用Shizuku增强模式进行位置模拟")

        Log.e(TAG, "🚀🚀🚀 MockLocationManager.start() 被调用！")
        Log.e(TAG, "📍 目标坐标: lat=$lat, lng=$lng")
        Log.e(TAG, "🔧 使用Shizuku增强模式进行位置模拟")

        // 检查Shizuku连接状态
        try {
            val binderAlive = Shizuku.pingBinder()
            Log.e(TAG, "🔗 Shizuku Binder状态: $binderAlive")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Shizuku Binder检查失败: ${e.message}")
        }

        // 检查Shizuku权限（正确的权限检查方式）
        val permissionStatus = try {
            Shizuku.checkSelfPermission()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Shizuku权限检查异常: ${e.message}")
            return false
        }
        Log.e(TAG, "🔐 Shizuku权限检查结果: $permissionStatus")

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
            // 检查是否需要使用Shizuku特殊调用方式
            Log.e(TAG, "🔧 检查Shizuku权限状态...")
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ Shizuku权限不足，无法使用增强模式")
                val permissionResult = false
                return permissionResult
            }

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
    }

    /**
     * 使用旧的Shizuku实现进行位置模拟
     */
    private fun startShizukuMockLocation(context: Context, lat: Double, lng: Double): Boolean {
        Log.e(TAG, "🔧🔧🔧 开始Shizuku增强模式位置模拟...")
        Log.e(TAG, "📍 目标坐标: lat=$lat, lng=$lng")

        return try {
            // 使用Shizuku权限添加测试提供者
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            Log.e(TAG, "✅ 获取LocationManager成功")

            // 为所有提供者添加测试提供者
            val providers = listOf(
                "gps",
                "network",
                "passive"
            )

            var successCount = 0
            for (provider in providers) {
                try {
                    Log.e(TAG, "🔧 开始设置提供者: $provider")

                    // 使用Shizuku权限调用系统API
                    locationManager.addTestProvider(
                        provider,
                        false, false, false, false, false, true, true,
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_FINE
                    )
                    Log.e(TAG, "✅ addTestProvider成功: $provider")

                    locationManager.setTestProviderEnabled(provider, true)
                    Log.e(TAG, "✅ setTestProviderEnabled成功: $provider")

                    // 创建位置对象
                    val location = Location(provider).apply {
                        latitude = lat
                        longitude = lng
                        accuracy = 1.0f
                        time = System.currentTimeMillis()
                        elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                    }
                    Log.e(TAG, "✅ Location对象创建成功: $provider")

                    locationManager.setTestProviderLocation(provider, location)
                    Log.e(TAG, "✅ setTestProviderLocation成功: $provider")

                    successCount++
                    Log.e(TAG, "✅✅✅ Shizuku增强模式: $provider 提供者设置成功")
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
                "gps",
                "network",
                "passive"
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
