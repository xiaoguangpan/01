package com.example.locationsimulator.util

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 标准模拟定位管理器 - 不依赖Shizuku的解决方案
 * 
 * 工作原理：
 * 1. 利用Android标准的Mock Location API
 * 2. 要求用户在开发者选项中选择本应用为模拟定位应用
 * 3. 通过LocationManager的标准接口设置模拟位置
 * 4. 支持多个定位提供者同时模拟
 */
object StandardMockLocationManager {
    
    private val TAG = Constants.LogTags.MOCK_LOCATION_MANAGER
    
    private val ALL_PROVIDERS = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    
    @Volatile
    private var executor: ScheduledExecutorService? = null
    
    @Volatile
    private var isRunning = false
    
    @Volatile
    private var currentLatitude = 0.0
    
    @Volatile
    private var currentLongitude = 0.0

    @Volatile
    private var lastError: String? = null

    /**
     * 获取最后一次错误信息
     */
    fun getLastError(): String? = lastError

    /**
     * 检查是否具备模拟定位的基本条件
     */
    fun checkMockLocationPermissions(context: Context): MockLocationStatus {
        Log.d(TAG, "🔍 开始检查模拟定位权限和条件...")

        // 1. 检查是否启用了开发者选项
        val developerEnabled = isDeveloperOptionsEnabled(context)
        Log.d(TAG, "🔍 开发者选项状态: ${if (developerEnabled) "已启用" else "未启用"}")
        if (!developerEnabled) {
            return MockLocationStatus.DEVELOPER_OPTIONS_DISABLED
        }

        // 2. 检查是否选择了模拟定位应用
        val mockAppSelected = isMockLocationAppSelected(context)
        Log.d(TAG, "🔍 模拟定位应用选择状态: ${if (mockAppSelected) "已选择" else "未选择"}")
        if (!mockAppSelected) {
            return MockLocationStatus.MOCK_APP_NOT_SELECTED
        }

        // 3. 检查LocationManager是否可用
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        Log.d(TAG, "🔍 LocationManager可用性: ${if (locationManager != null) "可用" else "不可用"}")
        if (locationManager == null) {
            return MockLocationStatus.LOCATION_SERVICE_UNAVAILABLE
        }

        // 4. ACCESS_MOCK_LOCATION权限在AndroidManifest.xml中声明即可，无需运行时检查
        // 该权限是系统级权限，通过开发者选项中的"选择模拟定位应用"来授予
        Log.d(TAG, "🔍 所有权限检查通过，状态: READY")

        return MockLocationStatus.READY
    }
    
    /**
     * 开始模拟定位
     */
    fun start(context: Context, latitude: Double, longitude: Double): Boolean {
        Log.d(TAG, "🚀 开始标准模拟定位: $latitude, $longitude")

        val status = checkMockLocationPermissions(context)
        if (status != MockLocationStatus.READY) {
            Log.e(TAG, "❌ 模拟定位条件不满足: $status")
            lastError = "权限检查失败: ${status.message}"
            return false
        }

        // 停止之前的模拟
        stop(context)

        synchronized(this) {
            if (isRunning) {
                Log.w(TAG, "模拟定位已在运行中")
                return true
            }

            currentLatitude = latitude
            currentLongitude = longitude
            isRunning = true

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // 初始化测试提供者
            if (!initializeTestProviders(locationManager)) {
                isRunning = false
                lastError = "测试提供者初始化失败，可能是权限不足或系统限制"
                Log.e(TAG, "❌ $lastError")
                return false
            }

            // 启动定期更新任务
            executor = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "StandardMockLocationThread").apply {
                    isDaemon = true
                }
            }

            executor?.scheduleAtFixedRate({
                if (!isRunning) return@scheduleAtFixedRate

                try {
                    updateMockLocation(locationManager, currentLatitude, currentLongitude)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 更新模拟位置失败: ${e.message}", e)
                    lastError = "位置更新失败: ${e.message}"
                }
            }, 0, Constants.Timing.LOCATION_UPDATE_INTERVAL, TimeUnit.MILLISECONDS)
        }

        Log.d(TAG, "✅ 标准模拟定位已启动")
        lastError = null
        return true
    }
    
    /**
     * 停止模拟定位
     */
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
        
        // 清理测试提供者
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            cleanupTestProviders(locationManager)
            Log.d(TAG, "🛑 标准模拟定位已停止")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 停止模拟定位失败: ${e.message}", e)
        }
    }
    
    /**
     * 更新模拟位置坐标
     */
    fun updateLocation(latitude: Double, longitude: Double) {
        synchronized(this) {
            currentLatitude = latitude
            currentLongitude = longitude
        }
        Log.d(TAG, "📍 更新模拟位置: $latitude, $longitude")
    }
    
    /**
     * 检查是否正在运行
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * 获取当前模拟的坐标
     */
    fun getCurrentLocation(): Pair<Double, Double> = Pair(currentLatitude, currentLongitude)
    
    // ========== 私有方法 ==========
    
    private fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (e: Exception) {
            Log.w(TAG, "无法检查开发者选项状态: ${e.message}")
            false
        }
    }
    
    private fun isMockLocationAppSelected(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 方法1：检查Settings.Secure中的模拟定位应用设置
                val mockLocationApp = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ALLOW_MOCK_LOCATION
                )

                // 方法2：使用AppOpsManager检查权限
                val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val result = appOpsManager.checkOp(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    android.os.Process.myUid(),
                    context.packageName
                )

                // 方法3：检查系统设置中的选择应用
                val selectedApp = try {
                    Settings.Secure.getString(context.contentResolver, "mock_location_app")
                } catch (e: Exception) {
                    null
                }

                // 如果任一方法检测到应用被选择，则返回true
                val isSelected = result == AppOpsManager.MODE_ALLOWED ||
                                context.packageName == selectedApp ||
                                mockLocationApp == "1"

                Log.d(TAG, "模拟定位应用检测: AppOps=$result, Selected=$selectedApp, Package=${context.packageName}, Result=$isSelected")

                isSelected
            } else {
                // Android 6.0以下版本不需要选择模拟定位应用
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法检查模拟定位应用设置: ${e.message}")
            false
        }
    }
    
    private fun initializeTestProviders(locationManager: LocationManager): Boolean {
        Log.d(TAG, "🔧 开始初始化测试提供者...")
        var successCount = 0
        var totalProviders = ALL_PROVIDERS.size

        ALL_PROVIDERS.forEach { provider ->
            try {
                Log.d(TAG, "🔧 处理提供者: $provider")

                // 先移除可能存在的测试提供者
                try {
                    locationManager.removeTestProvider(provider)
                    Log.d(TAG, "🗑️ 移除旧的测试提供者: $provider")
                } catch (e: Exception) {
                    Log.d(TAG, "🗑️ 移除测试提供者失败（可能不存在）: $provider")
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

                Log.d(TAG, "✅ 初始化测试提供者成功: $provider")
                successCount++

            } catch (e: SecurityException) {
                Log.e(TAG, "❌ 测试提供者权限不足 $provider: ${e.message}")
                lastError = "权限不足：无法创建测试提供者 $provider"
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "❌ 测试提供者参数错误 $provider: ${e.message}")
                lastError = "参数错误：测试提供者 $provider 配置无效"
            } catch (e: Exception) {
                Log.e(TAG, "❌ 初始化测试提供者失败 $provider: ${e.message}", e)
                lastError = "初始化失败：测试提供者 $provider - ${e.message}"
            }
        }

        Log.d(TAG, "📊 测试提供者初始化结果: $successCount/$totalProviders 成功")

        // 只要有一个提供者成功就认为初始化成功
        val result = successCount > 0
        if (!result) {
            lastError = "所有测试提供者初始化失败，可能是权限不足或系统限制"
        }

        return result
    }
    
    private fun updateMockLocation(locationManager: LocationManager, lat: Double, lng: Double) {
        ALL_PROVIDERS.forEach { provider ->
            try {
                val location = createMockLocation(provider, lat, lng)
                locationManager.setTestProviderLocation(provider, location)
            } catch (e: Exception) {
                Log.w(TAG, "设置模拟位置失败 $provider: ${e.message}")
            }
        }
    }
    
    private fun cleanupTestProviders(locationManager: LocationManager) {
        ALL_PROVIDERS.forEach { provider ->
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                Log.w(TAG, "清理测试提供者失败 $provider: ${e.message}")
            }
        }
    }
    
    private fun createMockLocation(provider: String, lat: Double, lng: Double): Location {
        return Location(provider).apply {
            latitude = lat
            longitude = lng
            accuracy = 1.0f
            altitude = 50.0
            bearing = 0.0f
            speed = 0.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            
            // Android 17+ 需要设置这些字段
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
        }
    }
}

/**
 * 模拟定位状态枚举
 */
enum class MockLocationStatus(val message: String) {
    READY("准备就绪"),
    NO_PERMISSION("缺少ACCESS_MOCK_LOCATION权限"),
    DEVELOPER_OPTIONS_DISABLED("开发者选项未启用"),
    MOCK_APP_NOT_SELECTED("未选择模拟定位应用"),
    LOCATION_SERVICE_UNAVAILABLE("定位服务不可用")
}
