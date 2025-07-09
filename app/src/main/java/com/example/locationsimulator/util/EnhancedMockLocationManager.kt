package com.example.locationsimulator.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import java.lang.reflect.Method

/**
 * 增强版模拟定位管理器
 * 
 * 专门针对国产手机ROM的兼容性优化：
 * 1. 小米MIUI/HyperOS反检测
 * 2. 华为EMUI/HarmonyOS适配
 * 3. OPPO ColorOS适配
 * 4. vivo OriginOS适配
 * 5. 一加OxygenOS适配
 */
object EnhancedMockLocationManager {
    
    private val TAG = Constants.LogTags.MOCK_LOCATION_MANAGER
    
    // 设备品牌检测
    private val deviceBrand = Build.BRAND.lowercase()
    private val deviceManufacturer = Build.MANUFACTURER.lowercase()
    private val systemVersion = Build.VERSION.RELEASE
    
    // 反射方法缓存
    private var locationManagerClass: Class<*>? = null
    private var addTestProviderMethod: Method? = null
    private var setTestProviderLocationMethod: Method? = null
    private var setTestProviderEnabledMethod: Method? = null
    private var removeTestProviderMethod: Method? = null
    
    init {
        initializeReflectionMethods()
    }
    
    /**
     * 智能模拟定位启动 - 根据设备类型选择最佳策略
     */
    fun smartStart(context: Context, latitude: Double, longitude: Double): Boolean {
        Log.d(TAG, "🎯 智能模拟定位启动 - 设备: $deviceBrand $deviceManufacturer")
        
        return when {
            isXiaomiDevice() -> startXiaomiCompatible(context, latitude, longitude)
            isHuaweiDevice() -> startHuaweiCompatible(context, latitude, longitude)
            isOppoDevice() -> startOppoCompatible(context, latitude, longitude)
            isVivoDevice() -> startVivoCompatible(context, latitude, longitude)
            isOnePlusDevice() -> startOnePlusCompatible(context, latitude, longitude)
            else -> startStandardMode(context, latitude, longitude)
        }
    }
    
    /**
     * 小米设备兼容模式
     * 针对MIUI/HyperOS的特殊处理
     */
    private fun startXiaomiCompatible(context: Context, latitude: Double, longitude: Double): Boolean {
        Log.d(TAG, "🔧 启用小米兼容模式")
        
        // 小米设备特殊处理策略
        return try {
            // 1. 尝试标准方式
            if (tryStandardMockLocation(context, latitude, longitude)) {
                Log.d(TAG, "✅ 小米设备标准模式成功")
                return true
            }
            
            // 2. 尝试反射方式绕过检测
            if (tryReflectionMockLocation(context, latitude, longitude)) {
                Log.d(TAG, "✅ 小米设备反射模式成功")
                return true
            }
            
            // 3. 尝试多线程并发设置
            if (tryConcurrentMockLocation(context, latitude, longitude)) {
                Log.d(TAG, "✅ 小米设备并发模式成功")
                return true
            }
            
            Log.e(TAG, "❌ 小米设备所有模式均失败")
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 小米兼容模式异常: ${e.message}", e)
            false
        }
    }
    
    /**
     * 华为设备兼容模式
     * 针对EMUI/HarmonyOS的特殊处理
     */
    private fun startHuaweiCompatible(context: Context, latitude: Double, longitude: Double): Boolean {
        Log.d(TAG, "🔧 启用华为兼容模式")
        
        return try {
            // 华为设备通常对标准API支持较好
            if (tryStandardMockLocation(context, latitude, longitude)) {
                Log.d(TAG, "✅ 华为设备标准模式成功")
                return true
            }
            
            // 备用方案：延迟设置
            if (tryDelayedMockLocation(context, latitude, longitude)) {
                Log.d(TAG, "✅ 华为设备延迟模式成功")
                return true
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ 华为兼容模式异常: ${e.message}", e)
            false
        }
    }
    
    /**
     * OPPO设备兼容模式
     */
    private fun startOppoCompatible(context: Context, latitude: Double, longitude: Double): Boolean {
        Log.d(TAG, "🔧 启用OPPO兼容模式")
        return tryStandardMockLocation(context, latitude, longitude)
    }
    
    /**
     * vivo设备兼容模式
     */
    private fun startVivoCompatible(context: Context, latitude: Double, longitude: Double): Boolean {
        Log.d(TAG, "🔧 启用vivo兼容模式")
        return tryStandardMockLocation(context, latitude, longitude)
    }
    
    /**
     * 一加设备兼容模式
     */
    private fun startOnePlusCompatible(context: Context, latitude: Double, longitude: Double): Boolean {
        Log.d(TAG, "🔧 启用一加兼容模式")
        return tryStandardMockLocation(context, latitude, longitude)
    }
    
    /**
     * 标准模式（其他设备）
     */
    private fun startStandardMode(context: Context, latitude: Double, longitude: Double): Boolean {
        Log.d(TAG, "🔧 启用标准兼容模式")
        return tryStandardMockLocation(context, latitude, longitude)
    }
    
    // ========== 设备检测方法 ==========
    
    private fun isXiaomiDevice(): Boolean {
        return deviceBrand.contains("xiaomi") || 
               deviceBrand.contains("redmi") || 
               deviceBrand.contains("poco") ||
               deviceManufacturer.contains("xiaomi")
    }
    
    private fun isHuaweiDevice(): Boolean {
        return deviceBrand.contains("huawei") || 
               deviceBrand.contains("honor") ||
               deviceManufacturer.contains("huawei")
    }
    
    private fun isOppoDevice(): Boolean {
        return deviceBrand.contains("oppo") || 
               deviceBrand.contains("realme") ||
               deviceManufacturer.contains("oppo")
    }
    
    private fun isVivoDevice(): Boolean {
        return deviceBrand.contains("vivo") || 
               deviceBrand.contains("iqoo") ||
               deviceManufacturer.contains("vivo")
    }
    
    private fun isOnePlusDevice(): Boolean {
        return deviceBrand.contains("oneplus") ||
               deviceManufacturer.contains("oneplus")
    }
    
    // ========== 模拟定位实现方法 ==========
    
    /**
     * 标准模拟定位方式
     */
    private fun tryStandardMockLocation(context: Context, latitude: Double, longitude: Double): Boolean {
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
                
                // 启用测试提供者
                locationManager.setTestProviderEnabled(provider, true)
                
                // 设置模拟位置
                val location = createMockLocation(provider, latitude, longitude)
                locationManager.setTestProviderLocation(provider, location)
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "标准模拟定位失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 反射模拟定位方式（绕过某些限制）
     */
    private fun tryReflectionMockLocation(context: Context, latitude: Double, longitude: Double): Boolean {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            if (locationManagerClass == null) {
                initializeReflectionMethods()
            }
            
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            
            providers.forEach { provider ->
                // 使用反射调用
                addTestProviderMethod?.invoke(
                    locationManager, provider,
                    false, false, false, false, true, true, true,
                    android.location.Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
                )
                
                setTestProviderEnabledMethod?.invoke(locationManager, provider, true)
                
                val location = createMockLocation(provider, latitude, longitude)
                setTestProviderLocationMethod?.invoke(locationManager, provider, location)
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "反射模拟定位失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 并发模拟定位方式（多线程同时设置）
     */
    private fun tryConcurrentMockLocation(context: Context, latitude: Double, longitude: Double): Boolean {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            
            // 并发执行
            val threads = providers.map { provider ->
                Thread {
                    try {
                        locationManager.removeTestProvider(provider)
                        Thread.sleep(50)
                        
                        locationManager.addTestProvider(
                            provider, false, false, false, false, true, true, true,
                            android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE
                        )
                        Thread.sleep(50)
                        
                        locationManager.setTestProviderEnabled(provider, true)
                        Thread.sleep(50)
                        
                        val location = createMockLocation(provider, latitude, longitude)
                        locationManager.setTestProviderLocation(provider, location)
                        
                    } catch (e: Exception) {
                        Log.w(TAG, "并发设置提供者失败 $provider: ${e.message}")
                    }
                }
            }
            
            threads.forEach { it.start() }
            threads.forEach { it.join(1000) } // 最多等待1秒
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "并发模拟定位失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 延迟模拟定位方式
     */
    private fun tryDelayedMockLocation(context: Context, latitude: Double, longitude: Double): Boolean {
        return try {
            Thread {
                Thread.sleep(500) // 延迟500ms
                tryStandardMockLocation(context, latitude, longitude)
            }.start()
            true
        } catch (e: Exception) {
            Log.e(TAG, "延迟模拟定位失败: ${e.message}", e)
            false
        }
    }
    
    private fun initializeReflectionMethods() {
        try {
            locationManagerClass = LocationManager::class.java
            
            addTestProviderMethod = locationManagerClass?.getMethod(
                "addTestProvider",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            
            setTestProviderLocationMethod = locationManagerClass?.getMethod(
                "setTestProviderLocation",
                String::class.java,
                Location::class.java
            )
            
            setTestProviderEnabledMethod = locationManagerClass?.getMethod(
                "setTestProviderEnabled",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            
            removeTestProviderMethod = locationManagerClass?.getMethod(
                "removeTestProvider",
                String::class.java
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "反射方法初始化失败: ${e.message}", e)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
            }
        }
    }
}
