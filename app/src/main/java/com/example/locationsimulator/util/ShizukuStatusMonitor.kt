package com.example.locationsimulator.util

import android.content.Context
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Shizuku状态监控器
 * 
 * 功能：
 * 1. 实时监控Shizuku服务状态
 * 2. 检测权限变化
 * 3. 自动触发模式切换
 * 4. 提供状态变化回调
 */
object ShizukuStatusMonitor {
    
    private val TAG = Constants.LogTags.SHIZUKU_MANAGER
    
    @Volatile
    private var isMonitoring = false
    
    @Volatile
    private var executor: ScheduledExecutorService? = null
    
    @Volatile
    private var lastShizukuStatus = ShizukuStatus.NOT_INSTALLED
    
    private var statusChangeCallback: ((ShizukuStatus) -> Unit)? = null
    
    /**
     * 开始监控Shizuku状态
     */
    fun startMonitoring(context: Context, callback: ((ShizukuStatus) -> Unit)? = null) {
        if (isMonitoring) return
        
        statusChangeCallback = callback
        isMonitoring = true
        
        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "ShizukuStatusMonitor").apply {
                isDaemon = true
            }
        }
        
        // 立即检查一次状态
        checkShizukuStatus()
        
        // 每3秒检查一次状态
        executor?.scheduleAtFixedRate({
            try {
                checkShizukuStatus()
            } catch (e: Exception) {
                Log.e(TAG, "Shizuku状态检查异常: ${e.message}", e)
            }
        }, 3, 3, TimeUnit.SECONDS)
        
        Log.d(TAG, "🔍 Shizuku状态监控已启动")
    }
    
    /**
     * 停止监控
     */
    fun stopMonitoring() {
        if (!isMonitoring) return
        
        isMonitoring = false
        statusChangeCallback = null
        
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
        
        Log.d(TAG, "🛑 Shizuku状态监控已停止")
    }
    
    /**
     * 检查Shizuku状态
     */
    private fun checkShizukuStatus() {
        val currentStatus = getCurrentShizukuStatus()
        
        if (currentStatus != lastShizukuStatus) {
            Log.d(TAG, "📊 Shizuku状态变化: ${lastShizukuStatus.message} → ${currentStatus.message}")
            lastShizukuStatus = currentStatus
            
            // 触发状态变化回调
            statusChangeCallback?.invoke(currentStatus)
        }
    }
    
    /**
     * 获取当前Shizuku状态
     */
    fun getCurrentShizukuStatus(): ShizukuStatus {
        return try {
            if (!isShizukuInstalled()) {
                ShizukuStatus.NOT_INSTALLED
            } else if (!isShizukuRunning()) {
                ShizukuStatus.NOT_RUNNING
            } else if (!hasShizukuPermission()) {
                ShizukuStatus.NO_PERMISSION
            } else {
                ShizukuStatus.READY
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查Shizuku状态失败: ${e.message}", e)
            ShizukuStatus.ERROR
        }
    }
    
    /**
     * 检查Shizuku是否已安装
     */
    private fun isShizukuInstalled(): Boolean {
        return try {
            Shizuku.getVersion() > 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查Shizuku是否正在运行
     */
    private fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查是否有Shizuku权限
     */
    private fun hasShizukuPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == Constants.RequestCodes.SHIZUKU_PERMISSION
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 请求Shizuku权限
     */
    fun requestShizukuPermission(): Boolean {
        return try {
            if (getCurrentShizukuStatus() == ShizukuStatus.NO_PERMISSION) {
                Shizuku.requestPermission(Constants.RequestCodes.SHIZUKU_PERMISSION)
                Log.d(TAG, "📝 已请求Shizuku权限")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求Shizuku权限失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 获取Shizuku设置指导
     */
    fun getShizukuSetupInstructions(context: Context): List<ShizukuInstruction> {
        return ShizukuSetupHelper.getDeviceSpecificInstructions(context)
    }
    
    /**
     * 生成Shizuku配置脚本
     */
    fun generateSetupScript(context: Context): String {
        return ShizukuSetupHelper.generateAutoSetupScript(context)
    }
    
    /**
     * 检查是否可以尝试Shizuku模式
     */
    fun canTryShizukuMode(): Boolean {
        val status = getCurrentShizukuStatus()
        return status == ShizukuStatus.READY || status == ShizukuStatus.NO_PERMISSION
    }
    
    /**
     * 获取Shizuku状态描述
     */
    fun getStatusDescription(): String {
        return when (val status = getCurrentShizukuStatus()) {
            ShizukuStatus.NOT_INSTALLED -> "❌ 未安装Shizuku应用"
            ShizukuStatus.NOT_RUNNING -> "⏸️ Shizuku服务未运行"
            ShizukuStatus.NO_PERMISSION -> "🔐 需要授予Shizuku权限"
            ShizukuStatus.READY -> "✅ Shizuku就绪"
            ShizukuStatus.ERROR -> "⚠️ 状态检查出错"
        }
    }
    
    /**
     * 获取详细状态信息
     */
    fun getDetailedStatus(): ShizukuDetailedStatus {
        return try {
            val installed = isShizukuInstalled()
            val running = if (installed) isShizukuRunning() else false
            val hasPermission = if (running) hasShizukuPermission() else false
            val version = if (installed) {
                try {
                    Shizuku.getVersion()
                } catch (e: Exception) {
                    -1
                }
            } else -1
            
            ShizukuDetailedStatus(
                installed = installed,
                running = running,
                hasPermission = hasPermission,
                version = version,
                status = getCurrentShizukuStatus()
            )
        } catch (e: Exception) {
            ShizukuDetailedStatus(
                installed = false,
                running = false,
                hasPermission = false,
                version = -1,
                status = ShizukuStatus.ERROR
            )
        }
    }
}

/**
 * Shizuku详细状态信息
 */
data class ShizukuDetailedStatus(
    val installed: Boolean,
    val running: Boolean,
    val hasPermission: Boolean,
    val version: Int,
    val status: ShizukuStatus
) {
    fun getStatusText(): String {
        return buildString {
            appendLine("📱 安装状态: ${if (installed) "已安装" else "未安装"}")
            if (installed) {
                appendLine("🔢 版本号: $version")
                appendLine("🏃 运行状态: ${if (running) "运行中" else "未运行"}")
                if (running) {
                    appendLine("🔐 权限状态: ${if (hasPermission) "已授权" else "未授权"}")
                }
            }
            appendLine("📊 总体状态: ${status.message}")
        }
    }
}
