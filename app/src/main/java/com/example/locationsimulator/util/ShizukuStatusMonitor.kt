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
     * 强制刷新状态（忽略缓存）
     */
    fun forceRefreshStatus(): ShizukuStatus {
        Log.d(TAG, "🔄 强制刷新Shizuku状态（忽略缓存）")
        val currentStatus = getCurrentShizukuStatus(monitoringContext)

        // 强制更新缓存状态
        if (currentStatus != lastShizukuStatus) {
            Log.d(TAG, "📊 强制刷新发现状态变化: ${lastShizukuStatus.message} → ${currentStatus.message}")
            lastShizukuStatus = currentStatus
            statusChangeCallback?.invoke(currentStatus)
        } else {
            Log.d(TAG, "📊 强制刷新状态无变化: ${currentStatus.message}")
        }

        return currentStatus
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
        return try {
            Log.d(TAG, "🔍 ===== 第1步：检测Shizuku安装状态 =====")

            // 首先尝试最直接的API检测方法
            Log.d(TAG, "🔍 优先尝试: Shizuku API直接检测")
            try {
                val apiDetected = tryShizukuApiDetection()
                if (apiDetected) {
                    Log.d(TAG, "🔍 ✅ Shizuku API检测成功: Shizuku已安装")
                    Log.d(TAG, "🔍 ===== 安装检测结果: 已安装 (API优先) =====")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "🔍 API优先检测异常: ${e.javaClass.simpleName} - ${e.message}")
            }

        // 方法1：通过PackageManager检查包是否已安装
        if (context != null) {
            Log.d(TAG, "🔍 尝试方法1: PackageManager检测")

            val shizukuPackages = listOf(
                "moe.shizuku.privileged.api",  // Shizuku主包名
                "rikka.shizuku.privileged.api", // 备选包名
                "moe.shizuku.manager"  // Shizuku管理器包名
            )

            // 尝试不同的PackageManager查询方式
            var packageManagerDetected = false

            // 方式1：标准getPackageInfo查询
            for (packageName in shizukuPackages) {
                try {
                    val packageManager = context.packageManager
                    val packageInfo = packageManager.getPackageInfo(packageName, 0)
                    Log.d(TAG, "🔍 ✅ PackageManager标准检测成功: 找到Shizuku包 $packageName, 版本: ${packageInfo.versionName}")
                    packageManagerDetected = true
                    break
                } catch (packageException: Exception) {
                    Log.d(TAG, "🔍 PackageManager标准检测: 包 $packageName - ${packageException.javaClass.simpleName}")
                }
            }

            // 方式2：getInstalledPackages查询（如果标准方式失败）
            if (!packageManagerDetected) {
                Log.d(TAG, "🔍 标准检测失败，尝试getInstalledPackages方式")
                try {
                    val installedPackages = context.packageManager.getInstalledPackages(0)
                    for (packageInfo in installedPackages) {
                        if (shizukuPackages.contains(packageInfo.packageName)) {
                            Log.d(TAG, "🔍 ✅ PackageManager列表检测成功: 找到Shizuku包 ${packageInfo.packageName}")
                            packageManagerDetected = true
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "🔍 PackageManager列表检测失败: ${e.message}")
                }
            }

            if (packageManagerDetected) {
                Log.d(TAG, "🔍 ===== 安装检测结果: 已安装 (PackageManager) =====")
                return true
            }

            // 方法2：Intent查询检测
            Log.d(TAG, "🔍 尝试方法2: Intent查询检测")
            if (tryIntentBasedDetection(context)) {
                Log.d(TAG, "🔍 ✅ Intent检测成功: Shizuku已安装")
                Log.d(TAG, "🔍 ===== 安装检测结果: 已安装 (Intent) =====")
                return true
            }
        } else {
            Log.w(TAG, "🔍 无Context可用，跳过PackageManager和Intent检测")
        }

        // 方法3：再次尝试API检测（更详细的日志）
        Log.d(TAG, "🔍 尝试方法3: Shizuku API详细检测")
        if (tryShizukuApiDetection()) {
            Log.d(TAG, "🔍 ✅ Shizuku API检测成功: Shizuku已安装")
            Log.d(TAG, "🔍 ===== 安装检测结果: 已安装 (API) =====")
            return true
        }

            // 最终结论：未安装
            Log.d(TAG, "🔍 ❌ 所有检测方法都未找到Shizuku应用")
            Log.d(TAG, "🔍 详细信息: 请检查Shizuku是否正确安装，包名是否为moe.shizuku.privileged.api")
            Log.d(TAG, "🔍 ===== 安装检测结果: 未安装 =====")
            false
        } catch (e: Exception) {
            Log.e(TAG, "🔍 ❌ 安装检测过程发生异常: ${e.javaClass.simpleName} - ${e.message}", e)
            Log.d(TAG, "🔍 异常堆栈: ${e.stackTraceToString()}")
            Log.d(TAG, "🔍 ===== 安装检测结果: 异常导致检测失败 =====")
            false
        }
    }

    /**
     * 备选检测方案：通过Intent查询检测Shizuku
     */
    private fun tryIntentBasedDetection(context: Context): Boolean {
        Log.d(TAG, "🔍 开始Intent检测...")

        return try {
            val packageManager = context.packageManager

            // 方法1：尝试查询Shizuku的主Activity
            val shizukuIntents = listOf(
                Intent().setClassName("moe.shizuku.privileged.api", "moe.shizuku.manager.MainActivity"),
                Intent().setClassName("rikka.shizuku.privileged.api", "rikka.shizuku.manager.MainActivity"),
                Intent().setClassName("moe.shizuku.manager", "moe.shizuku.manager.MainActivity"),
                // 添加更多可能的Activity
                Intent().setClassName("moe.shizuku.privileged.api", "moe.shizuku.manager.ShizukuActivity"),
                Intent().setClassName("moe.shizuku.privileged.api", "moe.shizuku.manager.app.MainActivity")
            )

            for (intent in shizukuIntents) {
                try {
                    val resolveInfo = packageManager.resolveActivity(intent, 0)
                    if (resolveInfo != null) {
                        Log.d(TAG, "🔍 ✅ Intent检测成功: 找到Shizuku Activity - ${intent.component}")
                        return true
                    } else {
                        Log.d(TAG, "🔍 Intent检测: Activity不存在 - ${intent.component}")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "🔍 Intent检测异常: ${intent.component} - ${e.javaClass.simpleName}")
                }
            }

            // 方法2：尝试查询启动Intent
            Log.d(TAG, "🔍 尝试查询Shizuku启动Intent...")
            val shizukuPackages = listOf("moe.shizuku.privileged.api", "rikka.shizuku.privileged.api", "moe.shizuku.manager")

            for (packageName in shizukuPackages) {
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        Log.d(TAG, "🔍 ✅ Intent检测成功: 找到Shizuku启动Intent - $packageName")
                        return true
                    } else {
                        Log.d(TAG, "🔍 Intent检测: 无启动Intent - $packageName")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "🔍 Intent检测异常: $packageName - ${e.javaClass.simpleName}")
                }
            }

            Log.d(TAG, "🔍 Intent检测: 所有方法都未找到Shizuku")
            false
        } catch (e: Exception) {
            Log.w(TAG, "🔍 Intent检测整体异常: ${e.javaClass.simpleName} - ${e.message}")
            false
        }
    }

    /**
     * 备选检测方案：通过Shizuku API直接检测
     */
    private fun tryShizukuApiDetection(): Boolean {
        return try {
            Log.d(TAG, "🔍 开始Shizuku API检测...")

            // 方法1：尝试获取版本号
            try {
                val version = Shizuku.getVersion()
                Log.d(TAG, "🔍 ✅ Shizuku.getVersion()成功: 版本 $version")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "🔍 Shizuku.getVersion()失败: ${e.javaClass.simpleName} - ${e.message}")
            }

        // 方法2：尝试检查Binder可用性
        try {
            val binderAvailable = Shizuku.getBinder() != null
            Log.d(TAG, "🔍 Shizuku.getBinder()结果: ${if (binderAvailable) "可用" else "不可用"}")
            if (binderAvailable) {
                Log.d(TAG, "🔍 ✅ Shizuku Binder可用，说明已安装")
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "🔍 Shizuku.getBinder()失败: ${e.javaClass.simpleName} - ${e.message}")
        }

        // 方法3：尝试ping Binder
        try {
            val pingResult = Shizuku.pingBinder()
            Log.d(TAG, "🔍 Shizuku.pingBinder()结果: $pingResult")
            if (pingResult) {
                Log.d(TAG, "🔍 ✅ Shizuku ping成功，说明已安装")
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "🔍 Shizuku.pingBinder()失败: ${e.javaClass.simpleName} - ${e.message}")
        }

        // 方法4：尝试获取UID
        try {
            val uid = Shizuku.getUid()
            Log.d(TAG, "🔍 Shizuku.getUid()结果: $uid")
            if (uid > 0) {
                Log.d(TAG, "🔍 ✅ Shizuku UID有效($uid)，说明已安装")
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "🔍 Shizuku.getUid()失败: ${e.javaClass.simpleName} - ${e.message}")
        }

            // 分析异常类型，判断是否已安装
            Log.d(TAG, "🔍 所有API方法都失败，分析可能原因:")
            Log.d(TAG, "🔍 - 如果是RuntimeException且包含'not running'，说明已安装但未运行")
            Log.d(TAG, "🔍 - 如果是UnsatisfiedLinkError或NoClassDefFoundError，说明未安装")
            Log.d(TAG, "🔍 - 其他异常可能是权限或状态问题")

            false
        } catch (e: Exception) {
            Log.e(TAG, "🔍 ❌ Shizuku API检测过程发生严重异常: ${e.javaClass.simpleName} - ${e.message}", e)
            Log.d(TAG, "🔍 异常堆栈: ${e.stackTraceToString()}")
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
            val pingResult = try {
                Shizuku.pingBinder()
            } catch (e: Exception) {
                Log.d(TAG, "🔍 Shizuku.pingBinder()异常: ${e.message}")
                false
            }
            Log.d(TAG, "🔍 Shizuku.pingBinder()结果: $pingResult")

            // 方法2：检查Binder是否可用
            val binderAvailable = try {
                val binder = Shizuku.getBinder()
                val available = binder != null
                Log.d(TAG, "🔍 Shizuku.getBinder()结果: ${if (available) "可用" else "null"}")
                available
            } catch (e: Exception) {
                Log.d(TAG, "🔍 Shizuku.getBinder()异常: ${e.message}")
                false
            }

            // 方法3：检查版本信息
            val versionCheck = try {
                val version = Shizuku.getVersion()
                Log.d(TAG, "🔍 Shizuku.getVersion()结果: $version")
                version > 0
            } catch (e: Exception) {
                Log.d(TAG, "🔍 Shizuku.getVersion()异常: ${e.message}")
                false
            }

            // 方法4：检查服务状态（新增）
            val serviceCheck = try {
                val uid = Shizuku.getUid()
                Log.d(TAG, "🔍 Shizuku.getUid()结果: $uid")
                uid > 0
            } catch (e: Exception) {
                Log.d(TAG, "🔍 Shizuku.getUid()异常: ${e.message}")
                false
            }

            // 修改判断逻辑：任何一个方法成功都认为Shizuku在运行
            // 这样可以处理不同启动方式（ADB、无线调试等）的兼容性问题
            val isRunning = pingResult || binderAvailable || versionCheck || serviceCheck

            // 详细记录每个检测方法的结果
            Log.d(TAG, "🔍 ===== 详细检测结果分析 =====")
            Log.d(TAG, "🔍 方法1 - pingBinder(): $pingResult")
            Log.d(TAG, "🔍 方法2 - getBinder(): $binderAvailable")
            Log.d(TAG, "🔍 方法3 - getVersion(): $versionCheck")
            Log.d(TAG, "🔍 方法4 - getUid(): $serviceCheck")
            Log.d(TAG, "🔍 综合判断逻辑: $pingResult || $binderAvailable || $versionCheck || $serviceCheck = $isRunning")

            if (isRunning) {
                Log.d(TAG, "🔍 ✅ Shizuku运行状态: 正在运行")
                val successMethods = mutableListOf<String>()
                if (pingResult) successMethods.add("pingBinder")
                if (binderAvailable) successMethods.add("getBinder")
                if (versionCheck) successMethods.add("getVersion")
                if (serviceCheck) successMethods.add("getUid")
                Log.d(TAG, "🔍 成功的检测方法: ${successMethods.joinToString(", ")}")
            } else {
                Log.d(TAG, "🔍 ❌ Shizuku运行状态: 未运行")
                Log.d(TAG, "🔍 所有检测方法都失败，可能原因:")
                Log.d(TAG, "🔍   - Shizuku服务未启动")
                Log.d(TAG, "🔍   - ADB连接问题")
                Log.d(TAG, "🔍   - 权限不足")
                Log.d(TAG, "🔍   - Shizuku版本不兼容")
            }
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
