package com.example.locationsimulator.service

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.example.locationsimulator.aidl.ILocationMockService

/**
 * Shizuku UserService for location mocking
 * 运行在具有系统权限的独立进程中
 */
class LocationMockService : ILocationMockService.Stub {
    
    companion object {
        private const val TAG = "LocationMockService"
        private val ALL_PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
    }
    
    private var locationManager: LocationManager? = null
    private var isRunning = false

    // UserService必须有无参构造函数
    init {
        Log.d(TAG, "✅ LocationMockService初始化完成")
    }
    
    override fun startMockLocation(latitude: Double, longitude: Double): Boolean {
        Log.e(TAG, "🚀🚀🚀 UserService.startMockLocation() 被调用！")
        Log.e(TAG, "📍 目标坐标: lat=$latitude, lng=$longitude")
        
        try {
            // 如果没有Context，尝试通过反射获取系统Context
            if (locationManager == null) {
                Log.e(TAG, "🔧 尝试获取系统LocationManager...")
                try {
                    // 在UserService中，我们需要通过反射获取系统服务
                    val activityThreadClass = Class.forName("android.app.ActivityThread")
                    val currentApplicationMethod = activityThreadClass.getMethod("currentApplication")
                    val application = currentApplicationMethod.invoke(null) as? Context

                    if (application != null) {
                        locationManager = application.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                        Log.e(TAG, "✅ 通过反射成功获取系统LocationManager")
                    } else {
                        Log.e(TAG, "❌ 无法通过反射获取Application Context")
                        return false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 反射获取LocationManager失败: ${e.message}", e)
                    return false
                }

                if (locationManager == null) {
                    Log.e(TAG, "❌ 无法获取LocationManager")
                    return false
                }
            }
            
            // 添加和启用测试提供者
            ALL_PROVIDERS.forEach { provider ->
                Log.e(TAG, "🔧 处理提供者: $provider")
                
                try {
                    // 移除可能存在的旧测试提供者
                    try {
                        locationManager!!.removeTestProvider(provider)
                        Log.e(TAG, "🔧 移除旧测试提供者: $provider")
                    } catch (e: Exception) {
                        Log.d(TAG, "🔧 移除旧测试提供者失败（可能不存在）: $provider")
                    }
                    
                    // 添加测试提供者
                    locationManager!!.addTestProvider(
                        provider,
                        false, // requiresNetwork
                        false, // requiresSatellite  
                        false, // requiresCell
                        false, // hasMonetaryCost
                        true,  // supportsAltitude
                        true,  // supportsSpeed
                        true,  // supportsBearing
                        1,     // powerRequirement
                        1      // accuracy
                    )
                    Log.e(TAG, "✅ 添加测试提供者成功: $provider")
                    
                    // 启用测试提供者
                    locationManager!!.setTestProviderEnabled(provider, true)
                    Log.e(TAG, "✅ 启用测试提供者成功: $provider")
                    
                    // 设置模拟位置
                    val location = createLocation(provider, latitude, longitude)
                    locationManager!!.setTestProviderLocation(provider, location)
                    Log.e(TAG, "✅ 设置模拟位置成功: $provider -> $latitude, $longitude")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 提供者 $provider 处理失败: ${e.javaClass.simpleName} - ${e.message}", e)
                    return false
                }
            }
            
            isRunning = true
            Log.e(TAG, "🎯🎯🎯 UserService位置模拟启动成功！")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ UserService位置模拟启动失败: ${e.javaClass.simpleName} - ${e.message}", e)
            return false
        }
    }
    
    override fun stopMockLocation(): Boolean {
        Log.e(TAG, "🛑🛑🛑 UserService.stopMockLocation() 被调用！")
        
        try {
            if (locationManager == null) {
                Log.e(TAG, "❌ LocationManager为空，无法停止")
                return false
            }
            
            ALL_PROVIDERS.forEach { provider ->
                try {
                    // 禁用测试提供者
                    locationManager!!.setTestProviderEnabled(provider, false)
                    Log.e(TAG, "✅ 禁用测试提供者: $provider")
                    
                    // 移除测试提供者
                    locationManager!!.removeTestProvider(provider)
                    Log.e(TAG, "✅ 移除测试提供者: $provider")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ 清理提供者失败 $provider: ${e.message}")
                }
            }
            
            isRunning = false
            Log.e(TAG, "🛑🛑🛑 UserService位置模拟停止成功！")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ UserService位置模拟停止失败: ${e.javaClass.simpleName} - ${e.message}", e)
            return false
        }
    }
    
    override fun isRunning(): Boolean {
        return isRunning
    }
    
    private fun createLocation(provider: String, lat: Double, lng: Double): Location {
        return Location(provider).apply {
            latitude = lat
            longitude = lng
            accuracy = 1.0f
            altitude = 50.0
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
    }
    
    // Shizuku UserService销毁方法（事务码16777115）
    override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
        if (code == 16777115) { // 销毁事务码
            Log.e(TAG, "🔧 UserService销毁请求")
            try {
                stopMockLocation()
            } catch (e: Exception) {
                Log.e(TAG, "销毁时停止模拟失败: ${e.message}")
            }
            System.exit(0)
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }
}
