package com.example.locationsimulator.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * 简化的模拟定位管理器
 * 
 * 功能：
 * 1. 仅保留标准模拟定位功能
 * 2. 移除所有Shizuku相关代码
 * 3. 简化错误处理和用户指导
 * 4. 专注于核心功能，提升稳定性
 */
object SimplifiedMockLocationManager {
    
    private const val TAG = "SimplifiedMockLocationManager"
    
    @Volatile
    private var isRunning = false
    
    private var currentLatitude = 0.0
    private var currentLongitude = 0.0
    
    /**
     * 启动模拟定位
     */
    fun start(context: Context, latitude: Double, longitude: Double, enhanced: Boolean = false): MockLocationResult {
        Log.d(TAG, "🚀 启动简化模拟定位")
        Log.d(TAG, "📍 目标坐标: $latitude, $longitude")
        
        // 检查模拟定位应用是否已选择
        if (!checkMockLocationAppSelected(context)) {
            return MockLocationResult.Failure("未选择模拟定位应用，请在开发者选项中选择本应用")
        }
        
        // 停止之前的模拟定位
        if (isRunning) {
            stop(context)
        }
        
        // 尝试标准模拟定位
        return if (StandardMockLocationManager.start(context, latitude, longitude, enhanced)) {
            isRunning = true
            currentLatitude = latitude
            currentLongitude = longitude
            
            Log.d(TAG, "✅ 标准模拟定位启动成功")
            MockLocationResult.Success(MockLocationStrategy.STANDARD)
        } else {
            Log.e(TAG, "❌ 标准模拟定位启动失败")
            MockLocationResult.Failure("模拟定位启动失败，请检查权限设置")
        }
    }
    
    /**
     * 停止模拟定位
     */
    fun stop(context: Context) {
        Log.d(TAG, "🛑 停止模拟定位")
        
        if (!isRunning) {
            Log.d(TAG, "模拟定位未运行，无需停止")
            return
        }
        
        isRunning = false
        
        // 停止标准模拟定位
        StandardMockLocationManager.stop(context)
        
        Log.d(TAG, "✅ 模拟定位已停止")
    }
    
    /**
     * 更新位置
     */
    fun updateLocation(context: Context, latitude: Double, longitude: Double) {
        if (!isRunning) return
        
        currentLatitude = latitude
        currentLongitude = longitude
        
        // 更新标准模拟定位
        StandardMockLocationManager.updateLocation(latitude, longitude)
        
        Log.d(TAG, "📍 更新模拟位置: $latitude, $longitude")
    }
    
    /**
     * 检查模拟定位应用是否已选择
     */
    private fun checkMockLocationAppSelected(context: Context): Boolean {
        return try {
            // Android 6.0+的检测方式
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // 尝试使用AppOpsManager检查模拟定位权限
                val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val mode = appOpsManager.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                    android.os.Process.myUid(),
                    context.packageName
                )
                val result = mode == android.app.AppOpsManager.MODE_ALLOWED
                Log.d(TAG, "🔍 AppOps检查结果: $result (mode: $mode)")
                return result
            } else {
                // Android 6.0以下的检测方式
                val mockLocationApp = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ALLOW_MOCK_LOCATION
                )
                val result = mockLocationApp == "1"
                Log.d(TAG, "🔍 Settings检查结果: $result (value: $mockLocationApp)")
                return result
            }
        } catch (e: Exception) {
            Log.w(TAG, "检查模拟定位应用状态失败: ${e.message}")
            // 如果检查失败，尝试直接启动看是否有权限
            return true
        }
    }
    
    /**
     * 打开开发者选项设置
     */
    fun openDeveloperOptions(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开开发者选项失败: ${e.message}")
            // 备选方案：打开应用设置
            openAppSettings(context)
        }
    }
    
    /**
     * 打开应用设置
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开应用设置失败: ${e.message}")
        }
    }
    
    /**
     * 获取运行状态
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * 获取当前坐标
     */
    fun getCurrentLocation(): Pair<Double, Double>? {
        return if (isRunning) {
            Pair(currentLatitude, currentLongitude)
        } else {
            null
        }
    }
    
    /**
     * 获取使用建议
     */
    fun getUsageTips(): List<String> {
        return listOf(
            "💡 使用建议：",
            "",
            "📱 钉钉打卡：",
            "• 开启飞行模式3秒 → 关闭飞行模式 → 立即打开钉钉打卡",
            "• 动作要快，钉钉有延迟检测机制",
            "",
            "🗺️ 高德地图：",
            "• 关闭WiFi → 开启飞行模式3秒 → 关闭飞行模式 → 重启高德地图",
            "• 保持WiFi关闭状态使用",
            "",
            "📱 百度地图：",
            "• 强制停止百度地图 → 清除缓存 → 重启应用",
            "• 百度地图反检测较强，成功率相对较低",
            "",
            "⚠️ 技术限制：",
            "• 模拟定位成功率约30-60%",
            "• 需要配合飞行模式等手动操作",
            "• WiFi定位可能干扰模拟效果，建议关闭WiFi"
        )
    }
}
