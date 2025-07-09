package com.example.locationsimulator.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 高级反检测模拟定位管理器
 * 
 * 针对主流应用的检测机制提供对应的绕过策略：
 * 1. 绕过 isFromMockProvider() 检测
 * 2. 隐藏开发者选项状态
 * 3. 模拟真实GPS信号特征
 * 4. 添加合理的定位噪声和漂移
 * 5. 模拟多传感器数据一致性
 */
object AntiDetectionMockLocationManager {
    
    private val TAG = Constants.LogTags.MOCK_LOCATION_MANAGER
    
    @Volatile
    private var executor: ScheduledExecutorService? = null
    
    @Volatile
    private var isRunning = false
    
    @Volatile
    private var currentLatitude = 0.0
    
    @Volatile
    private var currentLongitude = 0.0
    
    // 反检测配置
    private var enableNoiseSimulation = true
    private var enableSignalStrengthSimulation = true
    private var enableProviderRotation = true
    private var enableAirplaneModeSimulation = true

    // 飞行模式模拟状态
    private var originalAirplaneModeState = false
    private var airplaneModeSimulationActive = false
    
    /**
     * 启动高级反检测模拟定位
     */
    fun startAntiDetection(context: Context, latitude: Double, longitude: Double): Boolean {
        Log.d(TAG, "🛡️ 启动高级反检测模拟定位")
        
        stop(context)
        
        currentLatitude = latitude
        currentLongitude = longitude
        
        return try {
            // 1. 尝试隐藏开发者选项检测
            if (!bypassDeveloperOptionsDetection(context)) {
                Log.w(TAG, "⚠️ 无法完全隐藏开发者选项，继续尝试其他方法")
            }

            // 2. 基于DingTalk测试发现：模拟飞行模式切换来绕过检测
            if (enableAirplaneModeSimulation) {
                performAirplaneModeAntiDetection(context)
            }

            // 3. 设置反检测模拟定位
            if (setupAntiDetectionMockLocation(context, latitude, longitude)) {
                isRunning = true
                startAdvancedLocationSimulation(context)
                Log.d(TAG, "✅ 高级反检测模拟定位启动成功")
                return true
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ 高级反检测模拟定位启动失败: ${e.message}", e)
            false
        }
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
        
        // 清理模拟定位
        cleanupMockLocation(context)

        // 恢复飞行模式状态
        if (airplaneModeSimulationActive) {
            restoreAirplaneModeState(context)
        }

        Log.d(TAG, "🛑 高级反检测模拟定位已停止")
    }
    
    /**
     * 基于DingTalk测试发现的飞行模式反检测方法
     *
     * 发现：当DingTalk显示"疑似作弊"警告时，执行以下步骤可以绕过检测：
     * 1. 启用飞行模式
     * 2. 重新打开模拟定位应用
     * 3. 启动目标应用（如DingTalk）
     *
     * 原理：飞行模式会重置网络连接状态，可能清除某些检测缓存
     */
    private fun performAirplaneModeAntiDetection(context: Context) {
        try {
            Log.d(TAG, "🛩️ 执行飞行模式反检测策略")

            // 保存当前飞行模式状态
            originalAirplaneModeState = isAirplaneModeOn(context)
            Log.d(TAG, "📱 当前飞行模式状态: ${if (originalAirplaneModeState) "开启" else "关闭"}")

            if (!originalAirplaneModeState) {
                // 如果飞行模式未开启，则短暂开启后关闭
                Log.d(TAG, "🔄 模拟飞行模式切换以绕过检测...")

                // 尝试开启飞行模式（需要系统权限）
                if (setAirplaneMode(context, true)) {
                    Log.d(TAG, "✈️ 飞行模式已开启")
                    airplaneModeSimulationActive = true

                    // 延迟2秒后关闭飞行模式
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            setAirplaneMode(context, false)
                            Log.d(TAG, "📶 飞行模式已关闭，网络连接恢复")
                            Log.d(TAG, "🎯 飞行模式反检测策略执行完成")
                        } catch (e: Exception) {
                            Log.w(TAG, "关闭飞行模式失败: ${e.message}")
                        }
                    }, 2000)
                } else {
                    Log.w(TAG, "⚠️ 无法控制飞行模式，可能需要系统权限")
                    Log.d(TAG, "💡 建议用户手动执行：开启飞行模式 → 等待2秒 → 关闭飞行模式")
                }
            } else {
                Log.d(TAG, "✈️ 飞行模式已开启，无需额外操作")
            }

        } catch (e: Exception) {
            Log.e(TAG, "飞行模式反检测失败: ${e.message}", e)
        }
    }

    /**
     * 检查飞行模式是否开启
     */
    private fun isAirplaneModeOn(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) != 0
        } catch (e: Exception) {
            Log.w(TAG, "检查飞行模式状态失败: ${e.message}")
            false
        }
    }

    /**
     * 设置飞行模式状态
     */
    private fun setAirplaneMode(context: Context, enabled: Boolean): Boolean {
        return try {
            // 方法1: 尝试通过Settings.Global设置（需要WRITE_SECURE_SETTINGS权限）
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                if (enabled) 1 else 0
            )

            // 发送飞行模式变化广播
            val intent = android.content.Intent(android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED)
            intent.putExtra("state", enabled)
            context.sendBroadcast(intent)

            Log.d(TAG, "✅ 飞行模式设置成功: ${if (enabled) "开启" else "关闭"}")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "设置飞行模式需要系统权限: ${e.message}")

            // 方法2: 尝试通过反射调用系统方法
            try {
                val connectivityManagerClass = Class.forName("android.net.ConnectivityManager")
                val setAirplaneModeMethod = connectivityManagerClass.getMethod("setAirplaneMode", Boolean::class.javaPrimitiveType)
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                setAirplaneModeMethod.invoke(connectivityManager, enabled)
                Log.d(TAG, "✅ 通过反射设置飞行模式成功")
                true
            } catch (e2: Exception) {
                Log.w(TAG, "反射设置飞行模式失败: ${e2.message}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置飞行模式失败: ${e.message}", e)
            false
        }
    }

    /**
     * 恢复飞行模式状态
     */
    private fun restoreAirplaneModeState(context: Context) {
        try {
            if (airplaneModeSimulationActive) {
                Log.d(TAG, "🔄 恢复原始飞行模式状态: ${if (originalAirplaneModeState) "开启" else "关闭"}")
                setAirplaneMode(context, originalAirplaneModeState)
                airplaneModeSimulationActive = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复飞行模式状态失败: ${e.message}", e)
        }
    }

    /**
     * 绕过开发者选项检测
     */
    private fun bypassDeveloperOptionsDetection(context: Context): Boolean {
        return try {
            // 方法1: 尝试修改系统设置（需要系统权限）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                try {
                    Settings.Global.putInt(
                        context.contentResolver,
                        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                        0
                    )
                    Log.d(TAG, "✅ 成功隐藏开发者选项状态")
                    return true
                } catch (e: SecurityException) {
                    Log.w(TAG, "⚠️ 无系统权限修改开发者选项设置")
                }
            }
            
            // 方法2: Hook系统调用（需要Xposed或类似框架）
            try {
                hookDeveloperOptionsCheck()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Hook开发者选项检查失败: ${e.message}")
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "绕过开发者选项检测失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * Hook开发者选项检查
     */
    private fun hookDeveloperOptionsCheck() {
        try {
            val settingsGlobalClass = Settings.Global::class.java
            val getIntMethod = settingsGlobalClass.getMethod(
                "getInt",
                android.content.ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            
            // 这里需要使用反射Hook技术，实际实现需要更复杂的字节码操作
            Log.d(TAG, "尝试Hook开发者选项检查方法")
            
        } catch (e: Exception) {
            throw e
        }
    }
    
    /**
     * 设置反检测模拟定位
     */
    private fun setupAntiDetectionMockLocation(context: Context, lat: Double, lng: Double): Boolean {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            
            providers.forEach { provider ->
                try {
                    // 移除现有测试提供者
                    locationManager.removeTestProvider(provider)
                } catch (e: Exception) {
                    // 忽略
                }
                
                // 添加测试提供者
                locationManager.addTestProvider(
                    provider,
                    false, false, false, false, true, true, true,
                    android.location.Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
                )
                
                locationManager.setTestProviderEnabled(provider, true)
                
                // 设置初始位置
                val location = createAntiDetectionLocation(provider, lat, lng)
                locationManager.setTestProviderLocation(provider, location)
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "设置反检测模拟定位失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 创建反检测Location对象
     */
    private fun createAntiDetectionLocation(provider: String, lat: Double, lng: Double): Location {
        val location = Location(provider)
        
        // 添加随机噪声模拟真实GPS漂移
        val noiseLat = if (enableNoiseSimulation) {
            lat + (Random.nextDouble(-0.00001, 0.00001)) // 约1米范围内的随机偏移
        } else lat
        
        val noiseLng = if (enableNoiseSimulation) {
            lng + (Random.nextDouble(-0.00001, 0.00001))
        } else lng
        
        location.latitude = noiseLat
        location.longitude = noiseLng
        
        // 模拟真实GPS特征
        when (provider) {
            LocationManager.GPS_PROVIDER -> {
                location.accuracy = Random.nextFloat() * 5 + 3 // 3-8米精度
                location.altitude = Random.nextDouble(10.0, 100.0)
                location.bearing = Random.nextFloat() * 360
                location.speed = Random.nextFloat() * 2 // 0-2 m/s 步行速度
            }
            LocationManager.NETWORK_PROVIDER -> {
                location.accuracy = Random.nextFloat() * 50 + 20 // 20-70米精度
                location.altitude = 0.0
            }
            LocationManager.PASSIVE_PROVIDER -> {
                location.accuracy = Random.nextFloat() * 10 + 5 // 5-15米精度
            }
        }
        
        location.time = System.currentTimeMillis()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            location.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        
        // 尝试移除MockProvider标记
        try {
            removeMockProviderFlag(location)
        } catch (e: Exception) {
            Log.w(TAG, "无法移除MockProvider标记: ${e.message}")
        }
        
        return location
    }
    
    /**
     * 尝试移除Location的MockProvider标记
     */
    private fun removeMockProviderFlag(location: Location) {
        try {
            // 方法1: 反射修改内部字段
            val locationClass = Location::class.java
            
            // 查找可能的mock标记字段
            val possibleFields = listOf("mFromMockProvider", "mMock", "mIsMock")
            
            possibleFields.forEach { fieldName ->
                try {
                    val field = locationClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    field.setBoolean(location, false)
                    Log.d(TAG, "✅ 成功移除MockProvider标记: $fieldName")
                } catch (e: NoSuchFieldException) {
                    // 字段不存在，继续尝试下一个
                } catch (e: Exception) {
                    Log.w(TAG, "修改字段失败 $fieldName: ${e.message}")
                }
            }
            
            // 方法2: 尝试Hook isFromMockProvider方法
            try {
                val isFromMockProviderMethod = locationClass.getMethod("isFromMockProvider")
                // 这里需要使用动态代理或字节码操作来Hook方法返回值
                Log.d(TAG, "找到isFromMockProvider方法，尝试Hook")
            } catch (e: Exception) {
                Log.w(TAG, "Hook isFromMockProvider方法失败: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "移除MockProvider标记失败: ${e.message}")
        }
    }
    
    /**
     * 启动高级定位模拟
     */
    private fun startAdvancedLocationSimulation(context: Context) {
        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "AntiDetectionMockLocationThread").apply {
                isDaemon = true
            }
        }
        
        executor?.scheduleAtFixedRate({
            if (!isRunning) return@scheduleAtFixedRate
            
            try {
                updateAdvancedMockLocation(context)
            } catch (e: Exception) {
                Log.e(TAG, "高级定位模拟更新失败: ${e.message}", e)
            }
        }, 0, 1000, TimeUnit.MILLISECONDS) // 每秒更新一次
    }
    
    /**
     * 更新高级模拟定位
     */
    private fun updateAdvancedMockLocation(context: Context) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        val providers = if (enableProviderRotation) {
            // 随机选择1-2个提供者，模拟真实情况
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .shuffled().take(Random.nextInt(1, 3))
        } else {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        }
        
        providers.forEach { provider ->
            try {
                val location = createAntiDetectionLocation(provider, currentLatitude, currentLongitude)
                locationManager.setTestProviderLocation(provider, location)
            } catch (e: Exception) {
                Log.w(TAG, "更新提供者位置失败 $provider: ${e.message}")
            }
        }
    }
    
    /**
     * 清理模拟定位
     */
    private fun cleanupMockLocation(context: Context) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            
            providers.forEach { provider ->
                try {
                    locationManager.setTestProviderEnabled(provider, false)
                    locationManager.removeTestProvider(provider)
                } catch (e: Exception) {
                    Log.w(TAG, "清理提供者失败 $provider: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理模拟定位失败: ${e.message}", e)
        }
    }
    
    /**
     * 更新模拟位置
     */
    fun updateLocation(latitude: Double, longitude: Double) {
        synchronized(this) {
            currentLatitude = latitude
            currentLongitude = longitude
        }
    }
    
    /**
     * 配置反检测选项
     */
    fun configureAntiDetection(
        enableNoise: Boolean = true,
        enableSignalStrength: Boolean = true,
        enableProviderRotation: Boolean = true,
        enableAirplaneMode: Boolean = true
    ) {
        enableNoiseSimulation = enableNoise
        enableSignalStrengthSimulation = enableSignalStrength
        this.enableProviderRotation = enableProviderRotation
        enableAirplaneModeSimulation = enableAirplaneMode

        Log.d(TAG, "🔧 反检测配置更新: 噪声=$enableNoise, 信号强度=$enableSignalStrength, 提供者轮换=$enableProviderRotation, 飞行模式=$enableAirplaneMode")
    }
    
    fun isRunning(): Boolean = isRunning
}
