package com.example.locationsimulator.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 统一模拟定位管理器 - 简化两模式策略
 *
 * 采用直接有效的两模式方案：
 * 1. Primary Mode: 高级反检测模式 (AntiDetectionMockLocationManager) - 默认使用最强防检测技术
 * 2. Fallback Mode: Shizuku模式 (MockLocationManager) - 系统级权限，最高成功率
 *
 * 设计理念：
 * - 无需逐级尝试，直接使用最强可用方法
 * - 避免复杂的策略选择逻辑
 * - 提供明确的成功/失败反馈和设置指导
 */
object UnifiedMockLocationManager {
    
    private val TAG = Constants.LogTags.MOCK_LOCATION_MANAGER
    
    @Volatile
    private var currentStrategy: MockLocationStrategy = MockLocationStrategy.NONE
    
    @Volatile
    private var isRunning = false
    
    @Volatile
    private var executor: ScheduledExecutorService? = null
    
    @Volatile
    private var currentLatitude = 0.0
    
    @Volatile
    private var currentLongitude = 0.0

    @Volatile
    private var retryShizukuMode = false
    
    /**
     * 简化的两模式启动策略
     * Primary Mode: 高级反检测模式 (最强防检测)
     * Fallback Mode: Shizuku模式 (系统级权限)
     */
    fun start(context: Context, latitude: Double, longitude: Double): MockLocationResult {
        Log.d(TAG, "🚀 简化模拟定位启动: $latitude, $longitude")

        stop(context) // 先停止之前的模拟

        currentLatitude = latitude
        currentLongitude = longitude

        // 检查基础权限状态
        val standardStatus = StandardMockLocationManager.checkMockLocationPermissions(context)
        if (standardStatus != MockLocationStatus.READY) {
            Log.w(TAG, "⚠️ 基础权限不满足: ${standardStatus.message}")
            return MockLocationResult.Failure(standardStatus, getSetupInstructions(context, standardStatus))
        }

        // Primary Mode: 高级反检测模式 (默认使用最强方法)
        Log.d(TAG, "🛡️ 尝试高级反检测模式 (Primary Mode)")
        if (AntiDetectionMockLocationManager.startAntiDetection(context, latitude, longitude)) {
            currentStrategy = MockLocationStrategy.ANTI_DETECTION
            isRunning = true
            startMonitoring(context)
            Log.d(TAG, "✅ 使用高级反检测模式")
            return MockLocationResult.Success(MockLocationStrategy.ANTI_DETECTION)
        }

        // Fallback Mode: Shizuku模式 (如果可用且配置正确)
        val shizukuStatus = ShizukuStatusMonitor.getCurrentShizukuStatus()
        Log.d(TAG, "🔧 检查Shizuku模式 (Fallback Mode): ${shizukuStatus.message}")

        when (shizukuStatus) {
            ShizukuStatus.READY -> {
                Log.d(TAG, "🚀 尝试Shizuku模式")
                if (MockLocationManager.start(context, latitude, longitude)) {
                    currentStrategy = MockLocationStrategy.SHIZUKU
                    isRunning = true
                    startMonitoring(context)
                    Log.d(TAG, "✅ 使用Shizuku模式")
                    return MockLocationResult.Success(MockLocationStrategy.SHIZUKU)
                }
            }
            ShizukuStatus.NO_PERMISSION -> {
                // 请求权限并标记稍后重试
                ShizukuStatusMonitor.requestShizukuPermission()
                retryShizukuMode = true
                Log.d(TAG, "🔐 已请求Shizuku权限，稍后重试")
            }
            else -> {
                Log.w(TAG, "Shizuku不可用: ${shizukuStatus.message}")
            }
        }

        // 两种模式都失败，提供设置指导
        Log.e(TAG, "❌ 两种模拟定位模式都失败")

        val instructions = if (shizukuStatus != ShizukuStatus.NOT_INSTALLED) {
            // Shizuku已安装，提供配置指导
            getShizukuSetupInstructions(context, shizukuStatus)
        } else {
            // 提供Shizuku安装指导
            getShizukuInstallInstructions(context)
        }

        // 启动Shizuku状态监控，以便用户配置后自动重试
        startShizukuMonitoring(context)

        return MockLocationResult.Failure(standardStatus, instructions)
    }
    
    /**
     * 停止模拟定位
     */
    fun stop(context: Context) {
        if (!isRunning) return
        
        synchronized(this) {
            isRunning = false
            
            // 停止监控
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
            
            // 根据当前策略停止相应的服务
            when (currentStrategy) {
                MockLocationStrategy.ANTI_DETECTION -> {
                    AntiDetectionMockLocationManager.stop(context)
                }
                MockLocationStrategy.SHIZUKU -> {
                    MockLocationManager.stop(context)
                }
                MockLocationStrategy.NONE -> {
                    // 无需操作
                }
                // 兼容性处理 - 已弃用的策略
                MockLocationStrategy.STANDARD -> {
                    StandardMockLocationManager.stop(context)
                }
                MockLocationStrategy.ENHANCED -> {
                    StandardMockLocationManager.stop(context)
                }
            }
            
            currentStrategy = MockLocationStrategy.NONE
        }

        // 停止Shizuku监控
        ShizukuStatusMonitor.stopMonitoring()
        retryShizukuMode = false

        Log.d(TAG, "🛑 统一模拟定位已停止")
    }
    
    /**
     * 更新模拟位置
     */
    fun updateLocation(context: Context, latitude: Double, longitude: Double) {
        if (!isRunning) return
        
        currentLatitude = latitude
        currentLongitude = longitude
        
        when (currentStrategy) {
            MockLocationStrategy.ANTI_DETECTION -> {
                AntiDetectionMockLocationManager.updateLocation(latitude, longitude)
            }
            MockLocationStrategy.SHIZUKU -> {
                // Shizuku模式通过定时任务自动更新
            }
            MockLocationStrategy.NONE -> {
                // 无需操作
            }
            // 兼容性处理 - 已弃用的策略
            MockLocationStrategy.STANDARD -> {
                StandardMockLocationManager.updateLocation(latitude, longitude)
            }
            MockLocationStrategy.ENHANCED -> {
                EnhancedMockLocationManager.smartStart(context, latitude, longitude)
            }
        }
        
        Log.d(TAG, "📍 更新模拟位置: $latitude, $longitude")
    }
    
    /**
     * 获取当前状态
     */
    fun getStatus(): MockLocationInfo {
        return MockLocationInfo(
            isRunning = isRunning,
            strategy = currentStrategy,
            latitude = currentLatitude,
            longitude = currentLongitude
        )
    }
    
    /**
     * 获取设置说明
     */
    fun getSetupInstructions(context: Context, status: MockLocationStatus): List<SetupInstruction> {
        val instructions = mutableListOf<SetupInstruction>()
        
        when (status) {
            MockLocationStatus.DEVELOPER_OPTIONS_DISABLED -> {
                instructions.add(
                    SetupInstruction(
                        title = "启用开发者选项",
                        description = "进入设置 → 关于手机 → 连续点击版本号7次",
                        action = {
                            try {
                                val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e(TAG, "无法打开设备信息页面", e)
                            }
                        }
                    )
                )
            }
            
            MockLocationStatus.MOCK_APP_NOT_SELECTED -> {
                instructions.add(
                    SetupInstruction(
                        title = "选择模拟定位应用",
                        description = "进入设置 → 开发者选项 → 选择模拟定位应用 → 选择本应用",
                        action = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e(TAG, "无法打开开发者选项页面", e)
                            }
                        }
                    )
                )
            }
            
            MockLocationStatus.NO_PERMISSION -> {
                instructions.add(
                    SetupInstruction(
                        title = "权限问题",
                        description = "应用缺少必要的模拟定位权限，请重新安装应用",
                        action = null
                    )
                )
            }
            
            MockLocationStatus.LOCATION_SERVICE_UNAVAILABLE -> {
                instructions.add(
                    SetupInstruction(
                        title = "定位服务不可用",
                        description = "请确保设备的定位服务已启用",
                        action = {
                            try {
                                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e(TAG, "无法打开定位设置页面", e)
                            }
                        }
                    )
                )
            }
            
            MockLocationStatus.READY -> {
                // 已准备就绪，无需额外说明
            }
        }
        
        return instructions
    }
    
    /**
     * 启动监控任务
     */
    private fun startMonitoring(context: Context) {
        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "UnifiedMockLocationMonitor").apply {
                isDaemon = true
            }
        }
        
        executor?.scheduleAtFixedRate({
            if (!isRunning) return@scheduleAtFixedRate
            
            try {
                // 监控模拟定位状态，必要时重新设置
                when (currentStrategy) {
                    MockLocationStrategy.ANTI_DETECTION -> {
                        if (!AntiDetectionMockLocationManager.isRunning()) {
                            Log.w(TAG, "反检测模拟定位意外停止，尝试重启")
                            AntiDetectionMockLocationManager.startAntiDetection(context, currentLatitude, currentLongitude)
                        }
                    }
                    MockLocationStrategy.SHIZUKU -> {
                        // Shizuku模式有自己的监控机制
                    }
                    MockLocationStrategy.NONE -> {
                        // 无需监控
                    }
                    // 兼容性处理 - 已弃用的策略
                    MockLocationStrategy.STANDARD -> {
                        Log.w(TAG, "使用已弃用的标准模式，建议升级到反检测模式")
                    }
                    MockLocationStrategy.ENHANCED -> {
                        Log.w(TAG, "使用已弃用的增强模式，建议升级到反检测模式")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "监控任务异常: ${e.message}", e)
            }
        }, 5, 5, TimeUnit.SECONDS) // 每5秒检查一次
    }

    /**
     * 启动Shizuku状态监控
     */
    private fun startShizukuMonitoring(context: Context) {
        ShizukuStatusMonitor.startMonitoring(context) { status ->
            Log.d(TAG, "📊 Shizuku状态变化: ${status.message}")

            // 如果Shizuku变为可用状态，且之前标记需要重试
            if (status == ShizukuStatus.READY && retryShizukuMode && !isRunning) {
                Log.d(TAG, "🔄 Shizuku已就绪，自动重试模拟定位")

                // 自动重试启动模拟定位
                val result = start(context, currentLatitude, currentLongitude)
                if (result is MockLocationResult.Success) {
                    retryShizukuMode = false
                    Log.d(TAG, "✅ Shizuku模式自动重试成功")
                }
            }
        }
    }

    /**
     * 获取Shizuku设置指导
     */
    private fun getShizukuSetupInstructions(context: Context, status: ShizukuStatus): List<SetupInstruction> {
        val instructions = mutableListOf<SetupInstruction>()

        when (status) {
            ShizukuStatus.NOT_RUNNING -> {
                instructions.add(
                    SetupInstruction(
                        title = "启动Shizuku服务",
                        description = "请按照以下步骤激活Shizuku服务",
                        action = {
                            // 显示Shizuku设置指导
                            showShizukuSetupGuide(context)
                        }
                    )
                )
            }
            ShizukuStatus.NO_PERMISSION -> {
                instructions.add(
                    SetupInstruction(
                        title = "授予Shizuku权限",
                        description = "在Shizuku应用中授予本应用权限",
                        action = {
                            // 打开Shizuku应用
                            openShizukuApp(context)
                        }
                    )
                )
            }
            else -> {
                // 其他状态使用标准指导
                return getSetupInstructions(context, MockLocationStatus.READY)
            }
        }

        return instructions
    }

    /**
     * 获取Shizuku安装指导
     */
    private fun getShizukuInstallInstructions(context: Context): List<SetupInstruction> {
        return listOf(
            SetupInstruction(
                title = "安装Shizuku应用",
                description = "为了获得最佳的模拟定位效果，建议安装Shizuku应用",
                action = {
                    showShizukuSetupGuide(context)
                }
            ),
            SetupInstruction(
                title = "当前使用反检测模式",
                description = "应用已尝试使用高级反检测技术，但可能在某些应用中被检测到",
                action = null
            )
        )
    }

    /**
     * 显示Shizuku设置指导
     */
    private fun showShizukuSetupGuide(context: Context) {
        try {
            val instructions = ShizukuStatusMonitor.getShizukuSetupInstructions(context)
            Log.d(TAG, "📋 Shizuku设置指导:")
            instructions.forEach { instruction ->
                Log.d(TAG, "  ${instruction.step}. ${instruction.title}: ${instruction.description}")
            }

            // 执行第一个可执行的操作
            instructions.firstOrNull { it.action != null }?.action?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "显示Shizuku设置指导失败: ${e.message}", e)
        }
    }

    /**
     * 打开Shizuku应用
     */
    private fun openShizukuApp(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                context.startActivity(intent)
                Log.d(TAG, "📱 已打开Shizuku应用")
            } else {
                Log.e(TAG, "❌ Shizuku应用未安装")
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开Shizuku应用失败: ${e.message}", e)
        }
    }

    /**
     * 获取Shizuku状态信息
     */
    fun getShizukuStatus(): ShizukuDetailedStatus {
        return ShizukuStatusMonitor.getDetailedStatus()
    }
}

/**
 * 模拟定位策略枚举
 */
enum class MockLocationStrategy(val displayName: String) {
    NONE("未启用"),
    ANTI_DETECTION("高级反检测模式 (Primary)"),
    SHIZUKU("Shizuku模式 (Fallback)"),
    // 保留兼容性，但不在新策略中使用
    @Deprecated("使用简化的两模式策略")
    STANDARD("标准模式"),
    @Deprecated("使用简化的两模式策略")
    ENHANCED("增强兼容模式")
}

/**
 * 模拟定位结果
 */
sealed class MockLocationResult {
    data class Success(val strategy: MockLocationStrategy) : MockLocationResult()
    data class Failure(val status: MockLocationStatus, val instructions: List<SetupInstruction>) : MockLocationResult()
}

/**
 * 模拟定位信息
 */
data class MockLocationInfo(
    val isRunning: Boolean,
    val strategy: MockLocationStrategy,
    val latitude: Double,
    val longitude: Double
)

/**
 * 设置说明
 */
data class SetupInstruction(
    val title: String,
    val description: String,
    val action: (() -> Unit)?
)
