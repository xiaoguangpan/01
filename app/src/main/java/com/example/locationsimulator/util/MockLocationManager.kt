package com.example.locationsimulator.util

import android.content.Context
import android.util.Log
import rikka.shizuku.Shizuku

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

        Log.e(TAG, "✅ Shizuku权限检查通过，开始使用UserService模式")

        // 使用UserService方式（暂时禁用，回退到旧实现）
        Log.e(TAG, "⚠️ UserService模式暂时禁用，回退到旧的Shizuku实现")

        // TODO: 修复UserService API后重新启用
        // 暂时返回false，让UnifiedMockLocationManager尝试其他模式
        return false

        /*
        return try {
            // 绑定UserService
            if (!ShizukuUserServiceManager.isServiceBound()) {
                Log.e(TAG, "🔗 绑定UserService...")
                if (!ShizukuUserServiceManager.bindUserService(context)) {
                    Log.e(TAG, "❌ UserService绑定失败")
                    return false
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
            false
        }
        */

        // UserService模式已经在上面处理完成
        Log.e(TAG, "🎯🎯🎯 MockLocationManager.start() 完成")
        return true
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

            // UserService模式暂时禁用
            Log.e(TAG, "⚠️ UserService模式暂时禁用，无需特殊停止操作")

            Log.e(TAG, "🛑🛑🛑 Shizuku增强模式模拟定位已停止")
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ 停止模拟定位失败: ${e.javaClass.simpleName} - ${e.message}", e)
        }
    }

    fun isRunning(): Boolean = isRunning

    // UserService模式下，所有位置操作都在UserService中处理
}
