package com.example.locationsimulator.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * WiFi干扰处理器 - 解决WiFi定位服务干扰模拟定位的问题
 * 核心功能：
 * 1. 检测WiFi状态变化
 * 2. 处理WiFi定位服务干扰
 * 3. 实现WiFi兼容性处理
 * 4. 提供钉钉等应用的WiFi需求兼容
 */
class WiFiInterferenceHandler private constructor() {
    
    companion object {
        private const val TAG = "WiFiInterferenceHandler"
        
        @Volatile
        private var INSTANCE: WiFiInterferenceHandler? = null
        
        fun getInstance(): WiFiInterferenceHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WiFiInterferenceHandler().also { INSTANCE = it }
            }
        }
        
        // WiFi状态检查间隔
        private const val WIFI_CHECK_INTERVAL = 2000L // 2秒
        
        // 干扰处理延迟
        private const val INTERFERENCE_HANDLE_DELAY = 1000L // 1秒
    }
    
    private var isMonitoring = false
    private var wifiManager: WifiManager? = null
    private var connectivityManager: ConnectivityManager? = null
    
    private var monitoringExecutor: ScheduledExecutorService? = null
    private var handlerScope: CoroutineScope? = null
    
    // WiFi状态跟踪
    private var lastWifiEnabled = false
    private var lastWifiConnected = false
    private var interferenceCallback: ((Boolean) -> Unit)? = null
    
    // 应用特定处理
    private var isDingTalkMode = false
    private var requiresWifiEnabled = false
    
    /**
     * 启动WiFi干扰监控
     */
    fun startWifiInterferenceMonitoring(
        context: Context,
        callback: ((Boolean) -> Unit)? = null,
        dingTalkMode: Boolean = false
    ) {
        Log.d(TAG, "🚀 启动WiFi干扰监控")
        Log.d(TAG, "📱 钉钉模式: $dingTalkMode")
        
        stopWifiInterferenceMonitoring()
        
        isMonitoring = true
        interferenceCallback = callback
        isDingTalkMode = dingTalkMode
        requiresWifiEnabled = dingTalkMode // 钉钉需要WiFi开启
        
        // 初始化系统服务
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        
        // 获取初始状态
        updateWifiStatus()
        
        // 启动监控任务
        startMonitoringTasks()
        
        Log.d(TAG, "✅ WiFi干扰监控已启动")
    }
    
    /**
     * 停止WiFi干扰监控
     */
    fun stopWifiInterferenceMonitoring() {
        if (!isMonitoring) return
        
        Log.d(TAG, "🛑 停止WiFi干扰监控")
        
        isMonitoring = false
        interferenceCallback = null
        
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
        handlerScope?.cancel()
        handlerScope = null
        
        Log.d(TAG, "✅ WiFi干扰监控已停止")
    }
    
    /**
     * 启动监控任务
     */
    private fun startMonitoringTasks() {
        // 创建监控线程池
        monitoringExecutor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "WiFiInterferenceMonitor").apply {
                isDaemon = true
            }
        }
        
        // 创建协程作用域
        handlerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        
        // 启动WiFi状态监控
        monitoringExecutor?.scheduleAtFixedRate({
            if (!isMonitoring) return@scheduleAtFixedRate
            
            try {
                checkWifiStatusChange()
            } catch (e: Exception) {
                Log.e(TAG, "❌ WiFi状态检查异常: ${e.message}", e)
            }
        }, 0, WIFI_CHECK_INTERVAL, TimeUnit.MILLISECONDS)
        
        Log.d(TAG, "🔍 WiFi监控任务已启动")
    }
    
    /**
     * 检查WiFi状态变化
     */
    private fun checkWifiStatusChange() {
        val currentWifiEnabled = isWifiEnabled()
        val currentWifiConnected = isWifiConnected()
        
        // 检测WiFi开启状态变化
        if (currentWifiEnabled != lastWifiEnabled) {
            Log.d(TAG, "📶 WiFi状态变化: ${if (currentWifiEnabled) "开启" else "关闭"}")
            
            handleWifiStateChange(currentWifiEnabled, currentWifiConnected)
            lastWifiEnabled = currentWifiEnabled
        }
        
        // 检测WiFi连接状态变化
        if (currentWifiConnected != lastWifiConnected) {
            Log.d(TAG, "🌐 WiFi连接状态变化: ${if (currentWifiConnected) "已连接" else "未连接"}")
            
            handleWifiConnectionChange(currentWifiConnected)
            lastWifiConnected = currentWifiConnected
        }
    }
    
    /**
     * 处理WiFi状态变化
     */
    private fun handleWifiStateChange(enabled: Boolean, connected: Boolean) {
        handlerScope?.launch {
            try {
                if (enabled) {
                    Log.w(TAG, "⚠️ WiFi已开启，可能干扰模拟定位")
                    
                    if (isDingTalkMode) {
                        Log.d(TAG, "🎯 钉钉模式: WiFi开启是必需的，执行兼容性处理")
                        handleDingTalkWifiCompatibility()
                    } else {
                        Log.d(TAG, "🔧 执行WiFi干扰处理")
                        handleWifiInterference()
                    }
                    
                    // 通知干扰检测
                    interferenceCallback?.invoke(true)
                    
                } else {
                    Log.d(TAG, "✅ WiFi已关闭，干扰解除")
                    interferenceCallback?.invoke(false)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 处理WiFi状态变化异常: ${e.message}", e)
            }
        }
    }
    
    /**
     * 处理WiFi连接变化
     */
    private fun handleWifiConnectionChange(connected: Boolean) {
        handlerScope?.launch {
            try {
                if (connected) {
                    Log.w(TAG, "⚠️ WiFi已连接，网络定位可能干扰模拟定位")
                    
                    // 延迟处理，等待网络定位稳定
                    delay(INTERFERENCE_HANDLE_DELAY)
                    
                    if (isDingTalkMode) {
                        handleDingTalkNetworkCompatibility()
                    } else {
                        handleNetworkLocationInterference()
                    }
                    
                } else {
                    Log.d(TAG, "✅ WiFi连接断开，网络定位干扰减少")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 处理WiFi连接变化异常: ${e.message}", e)
            }
        }
    }
    
    /**
     * 钉钉WiFi兼容性处理
     */
    private fun handleDingTalkWifiCompatibility() {
        Log.d(TAG, "🎯 执行钉钉WiFi兼容性处理")
        
        // 钉钉需要WiFi开启但不一定需要连接
        // 实施策略：保持WiFi开启，但通过其他方式减少网络定位干扰
        
        // 策略1: 增加位置更新频率
        Log.d(TAG, "📍 策略1: 增加位置更新频率以对抗网络定位")
        
        // 策略2: 使用飞行模式重置技巧（如果可能）
        Log.d(TAG, "✈️ 策略2: 考虑飞行模式重置技巧")
        
        // 通知需要增强处理
        interferenceCallback?.invoke(true)
    }
    
    /**
     * 钉钉网络兼容性处理
     */
    private fun handleDingTalkNetworkCompatibility() {
        Log.d(TAG, "🎯 执行钉钉网络兼容性处理")
        
        // 钉钉在WiFi连接时的特殊处理
        // 需要更频繁的位置重置来对抗网络定位
        
        Log.d(TAG, "🔄 增强位置重置频率")
    }
    
    /**
     * 处理WiFi干扰
     */
    private fun handleWifiInterference() {
        Log.d(TAG, "🔧 处理WiFi干扰")
        
        // 对于非钉钉应用，建议关闭WiFi以获得最佳效果
        Log.w(TAG, "💡 建议: 关闭WiFi以获得最佳模拟定位效果")
    }
    
    /**
     * 处理网络定位干扰
     */
    private fun handleNetworkLocationInterference() {
        Log.d(TAG, "🔧 处理网络定位干扰")
        
        // 网络定位会使用WiFi和基站信息，可能覆盖模拟位置
        Log.w(TAG, "⚠️ 网络定位可能覆盖模拟位置，建议断开WiFi连接")
    }
    
    /**
     * 更新WiFi状态
     */
    private fun updateWifiStatus() {
        lastWifiEnabled = isWifiEnabled()
        lastWifiConnected = isWifiConnected()
        
        Log.d(TAG, "📊 当前WiFi状态 - 开启: $lastWifiEnabled, 连接: $lastWifiConnected")
    }
    
    /**
     * 检查WiFi是否开启
     */
    private fun isWifiEnabled(): Boolean {
        return try {
            wifiManager?.isWifiEnabled ?: false
        } catch (e: Exception) {
            Log.w(TAG, "检查WiFi开启状态异常: ${e.message}")
            false
        }
    }
    
    /**
     * 检查WiFi是否连接
     */
    private fun isWifiConnected(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager?.let { cm ->
                    val network = cm.activeNetwork
                    val capabilities = cm.getNetworkCapabilities(network)
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
                } ?: false
            } else {
                @Suppress("DEPRECATION")
                connectivityManager?.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
            }
        } catch (e: Exception) {
            Log.w(TAG, "检查WiFi连接状态异常: ${e.message}")
            false
        }
    }
    
    /**
     * 获取WiFi状态信息
     */
    fun getWifiStatusInfo(): String {
        val enabled = isWifiEnabled()
        val connected = isWifiConnected()
        return "WiFi开启: $enabled, 连接: $connected"
    }
    
    /**
     * 是否正在监控
     */
    fun isMonitoring(): Boolean = isMonitoring
    
    /**
     * 是否检测到干扰
     */
    fun hasInterference(): Boolean = lastWifiEnabled || lastWifiConnected
}
