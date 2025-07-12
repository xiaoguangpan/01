package com.example.locationsimulator.util

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.example.locationsimulator.aidl.ILocationMockService
import com.example.locationsimulator.service.LocationMockService
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Shizuku UserService管理器
 * 使用UserService方式进行位置模拟，比直接Binder调用更可靠
 */
object ShizukuUserServiceManager {
    private const val TAG = "ShizukuUserServiceManager"
    
    private var locationMockService: ILocationMockService? = null
    private var isServiceBound = false
    private var bindLatch: CountDownLatch? = null
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.e(TAG, "🔗🔗🔗 UserService连接成功！")
            locationMockService = ILocationMockService.Stub.asInterface(service)
            isServiceBound = true
            bindLatch?.countDown()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.e(TAG, "🔗❌ UserService连接断开")
            locationMockService = null
            isServiceBound = false
        }
    }
    
    /**
     * 绑定UserService
     */
    fun bindUserService(context: Context): Boolean {
        Log.e(TAG, "🔗 开始绑定UserService...")
        
        if (isServiceBound) {
            Log.e(TAG, "✅ UserService已经绑定")
            return true
        }
        
        try {
            val args = UserServiceArgs(ComponentName(context, LocationMockService::class.java))
                .tag("LocationMockService")
                .version(1)
            
            bindLatch = CountDownLatch(1)
            
            Shizuku.bindUserService(args, serviceConnection)
            Log.e(TAG, "🔗 UserService绑定请求已发送，等待连接...")
            
            // 等待连接完成（最多10秒）
            val connected = bindLatch!!.await(10, TimeUnit.SECONDS)
            
            if (connected && isServiceBound) {
                Log.e(TAG, "✅✅✅ UserService绑定成功！")
                return true
            } else {
                Log.e(TAG, "❌❌❌ UserService绑定超时或失败")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ UserService绑定异常: ${e.javaClass.simpleName} - ${e.message}", e)
            return false
        }
    }
    
    /**
     * 解绑UserService
     */
    fun unbindUserService() {
        Log.e(TAG, "🔗 解绑UserService...")
        
        try {
            if (isServiceBound) {
                Shizuku.unbindUserService(serviceConnection, true)
                locationMockService = null
                isServiceBound = false
                Log.e(TAG, "✅ UserService解绑成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ UserService解绑失败: ${e.message}", e)
        }
    }
    
    /**
     * 开始位置模拟
     */
    fun startMockLocation(latitude: Double, longitude: Double): Boolean {
        Log.e(TAG, "🚀 UserService开始位置模拟: $latitude, $longitude")
        
        if (!isServiceBound || locationMockService == null) {
            Log.e(TAG, "❌ UserService未绑定")
            return false
        }
        
        return try {
            val result = locationMockService!!.startMockLocation(latitude, longitude)
            Log.e(TAG, if (result) "✅ UserService位置模拟启动成功" else "❌ UserService位置模拟启动失败")
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ UserService位置模拟调用异常: ${e.javaClass.simpleName} - ${e.message}", e)
            false
        }
    }
    
    /**
     * 停止位置模拟
     */
    fun stopMockLocation(): Boolean {
        Log.e(TAG, "🛑 UserService停止位置模拟")
        
        if (!isServiceBound || locationMockService == null) {
            Log.e(TAG, "❌ UserService未绑定")
            return false
        }
        
        return try {
            val result = locationMockService!!.stopMockLocation()
            Log.e(TAG, if (result) "✅ UserService位置模拟停止成功" else "❌ UserService位置模拟停止失败")
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ UserService位置模拟停止异常: ${e.javaClass.simpleName} - ${e.message}", e)
            false
        }
    }
    
    /**
     * 检查是否正在运行
     */
    fun isRunning(): Boolean {
        if (!isServiceBound || locationMockService == null) {
            return false
        }
        
        return try {
            locationMockService!!.isRunning()
        } catch (e: Exception) {
            Log.e(TAG, "❌ UserService状态检查异常: ${e.message}", e)
            false
        }
    }
    
    /**
     * 检查UserService是否已绑定
     */
    fun isServiceBound(): Boolean {
        return isServiceBound && locationMockService != null
    }
}
