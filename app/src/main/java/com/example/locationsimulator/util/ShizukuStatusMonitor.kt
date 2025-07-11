package com.example.locationsimulator.util

import android.content.Context
import android.content.Intent
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

    @Volatile
    private var monitoringContext: Context? = null

    /**
     * 开始监控Shizuku状态
     */
    fun startMonitoring(context: Context, callback: ((ShizukuStatus) -> Unit)? = null) {
        if (isMonitoring) return

        monitoringContext = context
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
        val currentStatus = getCurrentShizukuStatus(monitoringContext)

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
    fun getCurrentShizukuStatus(context: Context? = null): ShizukuStatus {
        return try {
            Log.d(TAG, "🔍 ========== 开始完整Shizuku状态检测 ==========")

            // 第1步：检查安装状态
            val installed = isShizukuInstalled(context)
            if (!installed) {
                Log.d(TAG, "🔍 ❌ 最终结果: NOT_INSTALLED - Shizuku应用未安装")
                Log.d(TAG, "🔍 ========== Shizuku状态检测完成 ==========")
                return ShizukuStatus.NOT_INSTALLED
            }

            // 第2步：检查运行状态
            val running = isShizukuRunning()
            if (!running) {
                Log.d(TAG, "🔍 ❌ 最终结果: NOT_RUNNING - Shizuku已安装但未运行")
                Log.d(TAG, "🔍 ========== Shizuku状态检测完成 ==========")
                return ShizukuStatus.NOT_RUNNING
            }

            // 第3步：检查权限状态
            val hasPermission = hasShizukuPermission()
            if (!hasPermission) {
                Log.d(TAG, "🔍 ❌ 最终结果: NO_PERMISSION - Shizuku运行但未授权")
                Log.d(TAG, "🔍 ========== Shizuku状态检测完成 ==========")
                return ShizukuStatus.NO_PERMISSION
            }

            Log.d(TAG, "🔍 ✅ 最终结果: READY - Shizuku完全就绪")
            Log.d(TAG, "🔍 ========== Shizuku状态检测完成 ==========")
            ShizukuStatus.READY
        } catch (e: Exception) {
            Log.e(TAG, "❌ 检查Shizuku状态失败: ${e.message}", e)
            Log.d(TAG, "🔍 ❌ 最终结果: ERROR - 检测过程异常")
            Log.d(TAG, "🔍 ========== Shizuku状态检测完成 ==========")
            ShizukuStatus.ERROR
        }
    }
    
    /**
     * 检查Shizuku是否已安装
     */
    private fun isShizukuInstalled(context: Context? = null): Boolean {
        Log.d(TAG, "🔍 ===== 第1步：检测Shizuku安装状态 =====")

        // 方法1：通过PackageManager检查包是否已安装（最可靠的方法）
        if (context != null) {
            // 检查QUERY_ALL_PACKAGES权限状态
            val hasQueryAllPackagesPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    context.checkSelfPermission(android.Manifest.permission.QUERY_ALL_PACKAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } catch (e: Exception) {
                    Log.w(TAG, "🔍 检查QUERY_ALL_PACKAGES权限失败: ${e.message}")
                    false
                }
            } else {
                true // Android 11以下不需要此权限
            }

            Log.d(TAG, "🔍 QUERY_ALL_PACKAGES权限状态: ${if (hasQueryAllPackagesPermission) "已授予" else "未授予"}")

            val shizukuPackages = listOf(
                "moe.shizuku.privileged.api",  // Shizuku主包名
                "rikka.shizuku.privileged.api", // 备选包名
                "moe.shizuku.manager"  // Shizuku管理器包名
            )

            var packageManagerDetected = false
            for (packageName in shizukuPackages) {
                try {
                    val packageManager = context.packageManager
                    val packageInfo = packageManager.getPackageInfo(packageName, 0)
                    Log.d(TAG, "🔍 ✅ PackageManager检测成功: 找到Shizuku包 $packageName, 版本: ${packageInfo.versionName}")
                    packageManagerDetected = true
                    break
                } catch (packageException: Exception) {
                    when (packageException) {
                        is android.content.pm.PackageManager.NameNotFoundException -> {
                            Log.d(TAG, "🔍 PackageManager检测: 包 $packageName 未安装")
                        }
                        is SecurityException -> {
                            Log.w(TAG, "🔍 PackageManager检测: 包 $packageName 权限不足 - ${packageException.message}")
                        }
                        else -> {
                            Log.w(TAG, "🔍 PackageManager检测: 包 $packageName 检测失败 - ${packageException.message}")
                        }
                    }
                }
            }

            if (packageManagerDetected) {
                Log.d(TAG, "🔍 ===== 安装检测结果: 已安装 (PackageManager) =====")
                return true
            }

            if (!hasQueryAllPackagesPermission) {
                Log.d(TAG, "🔍 PackageManager检测失败可能是由于缺少QUERY_ALL_PACKAGES权限，尝试备选方案")

                // 备选方案：通过Intent查询检测Shizuku
                Log.d(TAG, "🔍 尝试备选检测方案: Intent查询")
                if (tryIntentBasedDetection(context)) {
                    Log.d(TAG, "🔍 ✅ Intent检测成功: Shizuku已安装")
                    Log.d(TAG, "🔍 ===== 安装检测结果: 已安装 (Intent) =====")
                    return true
                }
            } else {
                Log.d(TAG, "🔍 PackageManager检测: 所有Shizuku包都未找到")
            }
        } else {
            Log.w(TAG, "🔍 无Context可用，跳过PackageManager检测")
        }

        // 最终结论：未安装
        Log.d(TAG, "🔍 ❌ 所有检测方法都未找到Shizuku应用")
        Log.d(TAG, "🔍 ===== 安装检测结果: 未安装 =====")
        return false
    }

    /**
     * 备选检测方案：通过Intent查询检测Shizuku
     */
    private fun tryIntentBasedDetection(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager

            // 尝试查询Shizuku的主Activity
            val shizukuIntents = listOf(
                Intent().setClassName("moe.shizuku.privileged.api", "moe.shizuku.manager.MainActivity"),
                Intent().setClassName("rikka.shizuku.privileged.api", "rikka.shizuku.manager.MainActivity"),
                Intent().setClassName("moe.shizuku.manager", "moe.shizuku.manager.MainActivity")
            )

            for (intent in shizukuIntents) {
                try {
                    val resolveInfo = packageManager.resolveActivity(intent, 0)
                    if (resolveInfo != null) {
                        Log.d(TAG, "🔍 Intent检测: 找到Shizuku Activity - ${intent.component}")
                        return true
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "🔍 Intent检测失败: ${intent.component} - ${e.message}")
                }
            }

            Log.d(TAG, "🔍 Intent检测: 未找到Shizuku Activity")
            false
        } catch (e: Exception) {
            Log.w(TAG, "🔍 Intent检测异常: ${e.message}")
            false
        }
    }

    /**
     * 检查Shizuku是否正在运行
     */
    private fun isShizukuRunning(): Boolean {
        return try {
            Log.d(TAG, "🔍 ===== 第2步：检测Shizuku运行状态 =====")

            // 方法1：使用pingBinder检测
            val pingResult = Shizuku.pingBinder()
            Log.d(TAG, "🔍 Shizuku.pingBinder()结果: $pingResult")

            // 方法2：检查Binder是否可用
            val binderAvailable = try {
                Shizuku.getBinder() != null
            } catch (e: Exception) {
                Log.d(TAG, "🔍 Shizuku.getBinder()失败: ${e.message}")
                false
            }
            Log.d(TAG, "🔍 Shizuku Binder可用性: $binderAvailable")

            // 方法3：检查版本信息
            val versionCheck = try {
                val version = Shizuku.getVersion()
                Log.d(TAG, "🔍 Shizuku版本检测: $version")
                version > 0
            } catch (e: Exception) {
                Log.d(TAG, "🔍 Shizuku版本检测失败: ${e.message}")
                false
            }

            val isRunning = pingResult && binderAvailable
            if (isRunning) {
                Log.d(TAG, "🔍 ✅ Shizuku运行状态: 正在运行")
            } else {
                Log.d(TAG, "🔍 ❌ Shizuku运行状态: 未运行")
            }
            Log.d(TAG, "🔍 检测详情: ping=$pingResult, binder=$binderAvailable, version=$versionCheck")
            Log.d(TAG, "🔍 ===== 运行状态检测结果: ${if (isRunning) "运行中" else "未运行"} =====")

            isRunning
        } catch (e: Exception) {
            Log.e(TAG, "🔍 ❌ Shizuku运行状态检测异常: ${e.message}", e)
            Log.d(TAG, "🔍 ===== 运行状态检测结果: 检测异常 =====")
            false
        }
    }
    
    /**
     * 检查是否有Shizuku权限
     */
    private fun hasShizukuPermission(): Boolean {
        return try {
            Log.d(TAG, "🔍 ===== 第3步：检测Shizuku权限状态 =====")

            val permission = Shizuku.checkSelfPermission()
            val hasPermission = permission == android.content.pm.PackageManager.PERMISSION_GRANTED

            Log.d(TAG, "🔍 Shizuku权限检测: 权限码=$permission")
            Log.d(TAG, "🔍 权限检测详情: PERMISSION_GRANTED=${android.content.pm.PackageManager.PERMISSION_GRANTED}")

            if (hasPermission) {
                Log.d(TAG, "🔍 ✅ Shizuku权限状态: 已授权")
            } else {
                Log.d(TAG, "🔍 ❌ Shizuku权限状态: 未授权")
            }
            Log.d(TAG, "🔍 ===== 权限检测结果: ${if (hasPermission) "已授权" else "未授权"} =====")

            hasPermission
        } catch (e: Exception) {
            Log.e(TAG, "🔍 ❌ Shizuku权限检测失败: ${e.message}", e)
            Log.d(TAG, "🔍 ===== 权限检测结果: 检测异常 =====")
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
    fun getStatusDescription(context: Context? = null): String {
        return when (val status = getCurrentShizukuStatus(context)) {
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
    fun getDetailedStatus(context: Context? = null): ShizukuDetailedStatus {
        return try {
            val installed = isShizukuInstalled(context)
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
                status = getCurrentShizukuStatus(context)
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
