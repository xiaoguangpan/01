package com.example.locationsimulator.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 位置持续监控管理器 - 解决模拟位置回退问题
 * 核心功能：
 * 1. 持续监控位置提供商状态
 * 2. 检测位置回退并自动重新设置
 * 3. 针对不同应用的差异化处理策略
 * 4. WiFi干扰处理和兼容性优化
 */
class LocationPersistenceManager private constructor() {
    
    companion object {
        private const val TAG = "LocationPersistenceManager"
        
        @Volatile
        private var INSTANCE: LocationPersistenceManager? = null
        
        fun getInstance(): LocationPersistenceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocationPersistenceManager().also { INSTANCE = it }
            }
        }
        
        // 监控间隔配置
        private const val FAST_MONITOR_INTERVAL = 1000L // 1秒 - 高频监控
        private const val NORMAL_MONITOR_INTERVAL = 3000L // 3秒 - 正常监控
        private const val SLOW_MONITOR_INTERVAL = 5000L // 5秒 - 低频监控
        
        // 应用特定配置
        private const val DINGTALK_PACKAGE = "com.alibaba.android.rimet"
        private const val GAODE_PACKAGE = "com.autonavi.minimap"
        private const val BAIDU_PACKAGE = "com.baidu.BaiduMap"
    }
    
    private var isMonitoring = false
    private var currentLatitude = 0.0
    private var currentLongitude = 0.0
    private var targetStrategy = MockLocationStrategy.NONE
    
    private var monitoringExecutor: ScheduledExecutorService? = null
    private var persistenceScope: CoroutineScope? = null
    private var locationManager: LocationManager? = null
    
    // 应用差异化处理
    private var currentAppPackage: String? = null
    private var isHighFrequencyMode = false
    
    // 位置验证和回退检测
    private var lastSetLocation: Location? = null
    private var locationResetCount = 0
    private var lastResetTime = 0L
    
    /**
     * 启动位置持续监控
     */
    fun startPersistenceMonitoring(
        context: Context,
        latitude: Double,
        longitude: Double,
        strategy: MockLocationStrategy,
        targetApp: String? = null
    ) {
        Log.d(TAG, "🚀 启动位置持续监控")
        Log.d(TAG, "📍 目标位置: $latitude, $longitude")
        Log.d(TAG, "🎯 策略: $strategy")
        Log.d(TAG, "📱 目标应用: ${targetApp ?: "通用"}")
        
        stopPersistenceMonitoring()
        
        isMonitoring = true
        currentLatitude = latitude
        currentLongitude = longitude
        targetStrategy = strategy
        currentAppPackage = targetApp
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // 根据目标应用调整监控策略
        configureAppSpecificSettings(targetApp)
        
        // 启动监控任务
        startMonitoringTasks(context)
        
        Log.d(TAG, "✅ 位置持续监控已启动")
    }
    
    /**
     * 停止位置持续监控
     */
    fun stopPersistenceMonitoring() {
        if (!isMonitoring) return
        
        Log.d(TAG, "🛑 停止位置持续监控")
        
        isMonitoring = false
        
        // 停止监控任务
        monitoringExecutor?.let { executor ->
            try {
                executor.shutdown()
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
        monitoringExecutor = null
        
        // 停止协程作用域
        persistenceScope?.cancel()
        persistenceScope = null
        
        // 重置状态
        currentAppPackage = null
        isHighFrequencyMode = false
        lastSetLocation = null
        locationResetCount = 0
        
        Log.d(TAG, "✅ 位置持续监控已停止")
    }
    
    /**
     * 更新目标位置
     */
    fun updateTargetLocation(latitude: Double, longitude: Double) {
        if (!isMonitoring) return
        
        currentLatitude = latitude
        currentLongitude = longitude
        lastSetLocation = null // 重置验证位置
        
        Log.d(TAG, "📍 更新目标位置: $latitude, $longitude")
    }
    
    /**
     * 配置应用特定设置
     */
    private fun configureAppSpecificSettings(targetApp: String?) {
        when (targetApp) {
            DINGTALK_PACKAGE -> {
                isHighFrequencyMode = true
                Log.d(TAG, "🎯 钉钉模式: 启用高频监控")
            }
            GAODE_PACKAGE -> {
                isHighFrequencyMode = true
                Log.d(TAG, "🎯 高德地图模式: 启用高频监控 + 广告延迟处理")
            }
            BAIDU_PACKAGE -> {
                isHighFrequencyMode = false
                Log.d(TAG, "🎯 百度地图模式: 标准监控 + 多提供商轮换")
            }
            else -> {
                isHighFrequencyMode = false
                Log.d(TAG, "🎯 通用模式: 标准监控")
            }
        }
    }
    
    /**
     * 启动监控任务
     */
    private fun startMonitoringTasks(context: Context) {
        // 创建监控线程池
        monitoringExecutor = Executors.newScheduledThreadPool(2) { r ->
            Thread(r, "LocationPersistenceMonitor").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY + 1 // 稍高优先级
            }
        }
        
        // 创建协程作用域
        persistenceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        
        // 启动主监控任务
        startMainMonitoringTask(context)
        
        // 启动应用特定任务
        startAppSpecificTasks(context)
    }
    
    /**
     * 主监控任务 - 检测位置回退并重新设置
     */
    private fun startMainMonitoringTask(context: Context) {
        val interval = if (isHighFrequencyMode) FAST_MONITOR_INTERVAL else NORMAL_MONITOR_INTERVAL
        
        monitoringExecutor?.scheduleAtFixedRate({
            if (!isMonitoring) return@scheduleAtFixedRate
            
            try {
                // 检查并重新设置位置
                checkAndResetLocation(context)
                
                // 监控提供商状态
                monitorProviderStatus()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 主监控任务异常: ${e.message}", e)
            }
        }, 0, interval, TimeUnit.MILLISECONDS)
        
        Log.d(TAG, "🔍 主监控任务已启动，间隔: ${interval}ms")
    }
    
    /**
     * 应用特定任务
     */
    private fun startAppSpecificTasks(context: Context) {
        when (currentAppPackage) {
            DINGTALK_PACKAGE -> startDingTalkSpecificTask(context)
            GAODE_PACKAGE -> startGaodeSpecificTask(context)
            BAIDU_PACKAGE -> startBaiduSpecificTask(context)
        }
    }
    
    /**
     * 钉钉特定任务 - 超高频更新
     */
    private fun startDingTalkSpecificTask(context: Context) {
        persistenceScope?.launch {
            Log.d(TAG, "🎯 启动钉钉超高频更新任务")
            
            while (isMonitoring && currentAppPackage == DINGTALK_PACKAGE) {
                try {
                    // 每250ms更新一次位置，持续2分钟
                    repeat(480) { // 2分钟 = 120秒 * 4次/秒
                        if (!isMonitoring) break
                        
                        updateLocationWithNoise(context)
                        delay(250)
                        
                        if (it % 40 == 0) { // 每10秒输出一次日志
                            Log.d(TAG, "⚡ 钉钉超频更新: 第${it + 1}次")
                        }
                    }
                    
                    Log.d(TAG, "✅ 钉钉超频更新完成，切换到正常监控")
                    
                    // 超频更新完成后，等待30秒再进行下一轮
                    delay(30000)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 钉钉特定任务异常: ${e.message}", e)
                    delay(5000) // 异常后等待5秒
                }
            }
        }
    }
    
    /**
     * 高德地图特定任务 - 广告延迟处理
     */
    private fun startGaodeSpecificTask(context: Context) {
        persistenceScope?.launch {
            Log.d(TAG, "🎯 启动高德地图广告延迟处理任务")
            
            while (isMonitoring && currentAppPackage == GAODE_PACKAGE) {
                try {
                    // 等待3秒（广告时间）
                    delay(3000)
                    
                    // 强制重新设置位置
                    forceResetLocation(context)
                    
                    // 持续高频更新10秒
                    repeat(40) { // 10秒 * 4次/秒
                        if (!isMonitoring) break
                        updateLocationWithNoise(context)
                        delay(250)
                    }
                    
                    // 等待10秒再进行下一轮
                    delay(10000)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 高德特定任务异常: ${e.message}", e)
                    delay(5000)
                }
            }
        }
    }
    
    /**
     * 百度地图特定任务 - 多提供商轮换
     */
    private fun startBaiduSpecificTask(context: Context) {
        persistenceScope?.launch {
            Log.d(TAG, "🎯 启动百度地图多提供商轮换任务")

            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            while (isMonitoring && currentAppPackage == BAIDU_PACKAGE) {
                try {
                    // 轮换提供商
                    providers.shuffled().take(2).forEach { provider ->
                        if (!isMonitoring) return@forEach

                        updateSpecificProvider(context, provider)
                        delay(1000)
                    }

                    delay(5000) // 等待5秒再进行下一轮

                } catch (e: Exception) {
                    Log.e(TAG, "❌ 百度特定任务异常: ${e.message}", e)
                    delay(5000)
                }
            }
        }
    }

    /**
     * 检查并重新设置位置
     */
    private fun checkAndResetLocation(context: Context) {
        try {
            val locationManager = this.locationManager ?: return

            // 检查提供商状态
            val gpsEnabled = try {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (e: Exception) {
                Log.w(TAG, "检查GPS提供商状态异常: ${e.message}")
                false
            }

            val networkEnabled = try {
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            } catch (e: Exception) {
                Log.w(TAG, "检查网络提供商状态异常: ${e.message}")
                false
            }

            if (!gpsEnabled && !networkEnabled) {
                Log.w(TAG, "⚠️ 所有位置提供商都已禁用")
                return
            }

            // 检查是否需要重新设置位置（线程安全）
            val currentTime = System.currentTimeMillis()
            synchronized(this) {
                if (currentTime - lastResetTime > 2000) { // 至少间隔2秒
                    resetLocationProviders(context)
                    lastResetTime = currentTime
                    locationResetCount++

                    if (locationResetCount % 10 == 0) {
                        Log.d(TAG, "🔄 位置重置次数: $locationResetCount")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 检查位置状态异常: ${e.message}", e)
        }
    }

    /**
     * 监控提供商状态
     */
    private fun monitorProviderStatus() {
        try {
            val locationManager = this.locationManager ?: return

            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )

            providers.forEach { provider ->
                try {
                    val isEnabled = locationManager.isProviderEnabled(provider)
                    val lastKnownLocation = locationManager.getLastKnownLocation(provider)

                    // 检查位置是否正确
                    lastKnownLocation?.let { location ->
                        val distance = calculateDistance(
                            location.latitude, location.longitude,
                            currentLatitude, currentLongitude
                        )

                        if (distance > 100) { // 距离超过100米认为位置不正确
                            Log.w(TAG, "⚠️ 检测到位置偏差: $provider, 距离: ${distance}m")
                            // 触发位置重置
                            try {
                                forceResetLocation(context)
                            } catch (e: Exception) {
                                Log.e(TAG, "强制重置位置失败: ${e.message}")
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "监控提供商 $provider 异常: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 监控提供商状态异常: ${e.message}", e)
        }
    }

    /**
     * 重置位置提供商
     */
    private fun resetLocationProviders(context: Context) {
        try {
            when (targetStrategy) {
                MockLocationStrategy.SHIZUKU -> resetShizukuLocation(context)
                MockLocationStrategy.STANDARD -> resetStandardLocation(context)
                MockLocationStrategy.ANTI_DETECTION -> resetAntiDetectionLocation(context)
                else -> resetStandardLocation(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重置位置提供商异常: ${e.message}", e)
        }
    }

    /**
     * 强制重新设置位置
     */
    private fun forceResetLocation(context: Context) {
        Log.d(TAG, "🔄 强制重新设置位置")
        resetLocationProviders(context)
    }

    /**
     * 带噪声更新位置
     */
    private fun updateLocationWithNoise(context: Context) {
        try {
            val locationManager = this.locationManager ?: return

            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )

            providers.forEach { provider ->
                try {
                    val location = createLocationWithNoise(provider)
                    locationManager.setTestProviderLocation(provider, location)
                } catch (e: Exception) {
                    // 忽略单个提供商的错误
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "更新带噪声位置异常: ${e.message}")
        }
    }

    /**
     * 更新特定提供商
     */
    private fun updateSpecificProvider(context: Context, provider: String) {
        try {
            val locationManager = this.locationManager ?: return
            val location = createLocationWithNoise(provider)
            locationManager.setTestProviderLocation(provider, location)

            Log.d(TAG, "📍 更新提供商 $provider: ${location.latitude}, ${location.longitude}")

        } catch (e: Exception) {
            Log.w(TAG, "更新提供商 $provider 异常: ${e.message}")
        }
    }

    /**
     * 创建带噪声的位置对象
     */
    private fun createLocationWithNoise(provider: String): Location {
        return Location(provider).apply {
            // 添加极小的随机偏移，模拟真实GPS噪声
            latitude = currentLatitude + (Random.nextDouble() - 0.5) * 0.0000001
            longitude = currentLongitude + (Random.nextDouble() - 0.5) * 0.0000001
            accuracy = 1.0f + Random.nextFloat() * 2.0f
            altitude = 50.0 + Random.nextDouble() * 10.0
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

            // 添加速度和方向信息，增加真实性
            if (Random.nextBoolean()) {
                speed = Random.nextFloat() * 2.0f // 0-2 m/s
                bearing = Random.nextFloat() * 360.0f
            }
        }
    }

    /**
     * 重置Shizuku位置
     */
    private fun resetShizukuLocation(context: Context) {
        try {
            // 调用Shizuku增强模式重新设置位置
            val shizukuStatus = try {
                ShizukuStatusMonitor.getCurrentShizukuStatus(context)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 无法获取Shizuku状态: ${e.message}")
                null
            }

            if (shizukuStatus == ShizukuStatus.READY) {
                MockLocationManager.start(context, currentLatitude, currentLongitude)
                Log.d(TAG, "🔄 Shizuku位置已重置")
            } else {
                Log.w(TAG, "⚠️ Shizuku不可用，回退到标准模式")
                resetStandardLocation(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重置Shizuku位置异常: ${e.message}", e)
            resetStandardLocation(context) // 回退到标准模式
        }
    }

    /**
     * 重置标准位置
     */
    private fun resetStandardLocation(context: Context) {
        try {
            val result = StandardMockLocationManager.start(context, currentLatitude, currentLongitude)
            if (result) {
                Log.d(TAG, "🔄 标准位置已重置")
            } else {
                Log.w(TAG, "⚠️ 标准位置重置失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重置标准位置异常: ${e.message}", e)
        }
    }

    /**
     * 重置反检测位置
     */
    private fun resetAntiDetectionLocation(context: Context) {
        try {
            val result = AntiDetectionMockLocationManager.startAntiDetection(context, currentLatitude, currentLongitude)
            if (result) {
                Log.d(TAG, "🔄 反检测位置已重置")
            } else {
                Log.w(TAG, "⚠️ 反检测位置重置失败，回退到标准模式")
                resetStandardLocation(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重置反检测位置异常: ${e.message}", e)
            resetStandardLocation(context) // 回退到标准模式
        }
    }

    /**
     * 计算两点间距离（米）
     */
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0 // 地球半径（米）

        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * 获取监控状态
     */
    fun isMonitoring(): Boolean = isMonitoring

    /**
     * 获取重置次数
     */
    fun getResetCount(): Int = locationResetCount

    /**
     * 获取当前目标应用
     */
    fun getCurrentTargetApp(): String? = currentAppPackage
}
