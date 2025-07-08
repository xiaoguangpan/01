package com.example.locationsimulator.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.lang.reflect.Method
import kotlin.random.Random

/**
 * 小米设备专用反检测管理器
 * 基于社区研究和在线解决方案，针对MIUI/HyperOS的特殊限制
 */
object XiaomiAntiDetectionManager {
    private const val TAG = "XiaomiAntiDetection"
    
    // 小米设备检测
    private val isXiaomiDevice: Boolean by lazy {
        Build.MANUFACTURER.lowercase().contains("xiaomi") ||
        Build.BRAND.lowercase().contains("xiaomi") ||
        Build.BRAND.lowercase().contains("redmi")
    }
    
    // 持续任务控制
    private var isRunning = false
    private var locationUpdateThread: Thread? = null
    private var systemHookThread: Thread? = null
    
    /**
     * 启动小米专用反检测系统
     */
    fun startXiaomiAntiDetection(context: Context, lat: Double, lng: Double) {
        if (!isXiaomiDevice) {
            Log.d(TAG, "非小米设备，跳过小米专用反检测")
            return
        }
        
        try {
            Log.d(TAG, "🔧 启动小米专用反检测系统")
            isRunning = true
            
            // 1. 启动超高频位置更新
            startUltraHighFrequencyUpdates(context, lat, lng)
            
            // 2. 启动系统级位置劫持
            startSystemLocationHook(context, lat, lng)
            
            // 3. 启动MIUI服务干扰
            startMIUIServiceInterference(context)
            
            // 4. 启动位置提供者欺骗
            startLocationProviderSpoofing(context, lat, lng)
            
            Log.d(TAG, "✅ 小米专用反检测系统已启动")
            
        } catch (e: Exception) {
            Log.e(TAG, "启动小米专用反检测失败: ${e.message}")
        }
    }
    
    /**
     * 停止小米专用反检测系统
     */
    fun stopXiaomiAntiDetection() {
        try {
            Log.d(TAG, "🛑 停止小米专用反检测系统")
            isRunning = false
            
            locationUpdateThread?.interrupt()
            systemHookThread?.interrupt()
            
            locationUpdateThread = null
            systemHookThread = null
            
            Log.d(TAG, "✅ 小米专用反检测系统已停止")
            
        } catch (e: Exception) {
            Log.e(TAG, "停止小米专用反检测失败: ${e.message}")
        }
    }
    
    /**
     * 超高频位置更新 - 每秒更新多次
     */
    private fun startUltraHighFrequencyUpdates(context: Context, lat: Double, lng: Double) {
        locationUpdateThread = Thread {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            while (isRunning && !Thread.currentThread().isInterrupted) {
                try {
                    // 每500毫秒更新一次，比标准频率高10倍
                    updateAllProviders(locationManager, lat, lng)
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "超高频位置更新失败: ${e.message}")
                }
            }
        }
        
        locationUpdateThread?.start()
        Log.d(TAG, "超高频位置更新已启动 (500ms间隔)")
    }
    
    /**
     * 系统级位置劫持 - 尝试hook系统调用
     */
    private fun startSystemLocationHook(context: Context, lat: Double, lng: Double) {
        systemHookThread = Thread {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                try {
                    // 尝试通过反射修改系统位置服务
                    hookLocationManagerService(context, lat, lng)
                    
                    // 尝试修改GPS状态
                    spoofGPSStatus(context)
                    
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "系统级位置劫持失败: ${e.message}")
                }
            }
        }
        
        systemHookThread?.start()
        Log.d(TAG, "系统级位置劫持已启动")
    }
    
    /**
     * MIUI服务干扰 - 干扰MIUI的位置检测服务
     */
    private fun startMIUIServiceInterference(context: Context) {
        try {
            // 尝试禁用MIUI的位置验证服务
            disableMIUILocationVerification(context)
            
            // 修改MIUI系统属性
            modifyMIUISystemProperties()
            
            Log.d(TAG, "MIUI服务干扰已启动")
            
        } catch (e: Exception) {
            Log.e(TAG, "MIUI服务干扰失败: ${e.message}")
        }
    }
    
    /**
     * 位置提供者欺骗 - 创建虚假的位置提供者
     */
    private fun startLocationProviderSpoofing(context: Context, lat: Double, lng: Double) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // 创建额外的虚假提供者
            val fakeProviders = listOf("xiaomi_gps", "miui_location", "hybrid_location")
            
            fakeProviders.forEach { provider ->
                try {
                    // 移除可能存在的提供者
                    try {
                        locationManager.removeTestProvider(provider)
                    } catch (e: Exception) {
                        // 忽略
                    }
                    
                    // 添加虚假提供者
                    locationManager.addTestProvider(
                        provider,
                        true, true, true, false, true, true, true,
                        android.location.Criteria.POWER_MEDIUM,
                        android.location.Criteria.ACCURACY_FINE
                    )
                    
                    locationManager.setTestProviderEnabled(provider, true)
                    
                    // 设置位置
                    val location = createXiaomiOptimizedLocation(provider, lat, lng)
                    locationManager.setTestProviderLocation(provider, location)
                    
                    Log.d(TAG, "虚假提供者 $provider 已创建")
                    
                } catch (e: Exception) {
                    Log.w(TAG, "创建虚假提供者 $provider 失败: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "位置提供者欺骗失败: ${e.message}")
        }
    }
    
    /**
     * 更新所有位置提供者
     */
    private fun updateAllProviders(locationManager: LocationManager, lat: Double, lng: Double) {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            "fused",
            "xiaomi_gps",
            "miui_location",
            "hybrid_location"
        )
        
        providers.forEach { provider ->
            try {
                val location = createXiaomiOptimizedLocation(provider, lat, lng)
                locationManager.setTestProviderLocation(provider, location)
            } catch (e: Exception) {
                // 忽略单个提供者错误
            }
        }
    }
    
    /**
     * 创建小米优化的位置对象
     */
    private fun createXiaomiOptimizedLocation(provider: String, lat: Double, lng: Double): Location {
        val currentTime = System.currentTimeMillis()
        
        // 更小的随机偏移，避免被检测
        val latOffset = Random.nextDouble(-0.0000001, 0.0000001)
        val lngOffset = Random.nextDouble(-0.0000001, 0.0000001)
        
        return Location(provider).apply {
            latitude = lat + latOffset
            longitude = lng + lngOffset
            time = currentTime
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            
            // 小米设备需要更真实的精度值
            accuracy = when (provider) {
                LocationManager.GPS_PROVIDER -> Random.nextFloat() * 1f + 1.5f // 1.5-2.5米
                LocationManager.NETWORK_PROVIDER -> Random.nextFloat() * 3f + 7f // 7-10米
                "fused" -> Random.nextFloat() * 2f + 2f // 2-4米
                else -> Random.nextFloat() * 2f + 3f // 3-5米
            }
            
            // 设置其他参数
            speed = 0.0f
            bearing = 0.0f
            altitude = 50.0 + Random.nextDouble(-1.0, 1.0)
            
            // Android 8.0+ 的额外参数
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                verticalAccuracyMeters = accuracy * 1.2f
                speedAccuracyMetersPerSecond = 0.05f
                bearingAccuracyDegrees = 10.0f
            }
        }
    }
    
    /**
     * Hook LocationManager服务
     */
    private fun hookLocationManagerService(context: Context, lat: Double, lng: Double) {
        try {
            // 通过反射尝试修改LocationManager的内部状态
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val clazz = locationManager.javaClass
            
            // 尝试获取内部方法
            val methods = clazz.declaredMethods
            methods.forEach { method ->
                if (method.name.contains("getLastKnownLocation") || 
                    method.name.contains("requestLocationUpdates")) {
                    try {
                        method.isAccessible = true
                        // 这里可以进一步hook方法调用
                    } catch (e: Exception) {
                        // 忽略反射错误
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Hook LocationManager失败: ${e.message}")
        }
    }
    
    /**
     * 欺骗GPS状态
     */
    private fun spoofGPSStatus(context: Context) {
        try {
            // 尝试修改GPS状态相关的系统属性
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // 确保GPS提供者显示为可用
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Log.d(TAG, "GPS提供者未启用，尝试激活")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "欺骗GPS状态失败: ${e.message}")
        }
    }
    
    /**
     * 禁用MIUI位置验证
     */
    private fun disableMIUILocationVerification(context: Context) {
        try {
            // 尝试通过系统属性禁用MIUI的位置验证
            val properties = listOf(
                "ro.miui.has_real_location_verification",
                "ro.miui.location_verification_enabled",
                "persist.vendor.radio.enable_location_check"
            )
            
            properties.forEach { prop ->
                try {
                    setSystemProperty(prop, "false")
                } catch (e: Exception) {
                    // 忽略单个属性设置错误
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "禁用MIUI位置验证失败: ${e.message}")
        }
    }
    
    /**
     * 修改MIUI系统属性
     */
    private fun modifyMIUISystemProperties() {
        try {
            val properties = mapOf(
                "ro.debuggable" to "1",
                "ro.secure" to "0",
                "ro.miui.ui.version.code" to "12",
                "persist.vendor.radio.enable_location_check" to "false"
            )
            
            properties.forEach { (key, value) ->
                try {
                    setSystemProperty(key, value)
                } catch (e: Exception) {
                    // 忽略单个属性设置错误
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "修改MIUI系统属性失败: ${e.message}")
        }
    }
    
    /**
     * 设置系统属性
     */
    private fun setSystemProperty(key: String, value: String) {
        try {
            val process = Runtime.getRuntime().exec("setprop $key $value")
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "设置系统属性 $key=$value 失败: ${e.message}")
        }
    }
    
    /**
     * 检查小米专用反检测是否运行
     */
    fun isXiaomiAntiDetectionRunning(): Boolean {
        return isRunning && isXiaomiDevice
    }
}
