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
     * Secondary Mode: 标准模式 (不依赖Shizuku)
     * Fallback Mode: Shizuku模式 (系统级权限，需要增强模式开启)
     */
    fun start(context: Context, latitude: Double, longitude: Double, enableShizukuMode: Boolean = false): MockLocationResult {
        // 强制输出日志 - 确保代码更新
        android.util.Log.e("FORCE_DEBUG", "🚀🚀🚀 UnifiedMockLocationManager.start() 被调用！ [版本2024-12-14-11:15]")
        android.util.Log.e("FORCE_DEBUG", "📍 目标坐标: $latitude, $longitude")
        android.util.Log.e("FORCE_DEBUG", "🔧 Shizuku增强模式: ${if (enableShizukuMode) "已开启" else "已关闭"}")

        Log.e(TAG, "🚀🚀🚀 UnifiedMockLocationManager.start() 被调用！")
        Log.e(TAG, "📍 目标坐标: $latitude, $longitude")
        Log.e(TAG, "🔧 Shizuku增强模式: ${if (enableShizukuMode) "已开启" else "已关闭"}")

        stop(context) // 先停止之前的模拟

        currentLatitude = latitude
        currentLongitude = longitude

        // 检查基础权限状态（必须通过才能继续）
        Log.e(TAG, "🔍 开始检查基础权限状态...")
        val standardStatus = StandardMockLocationManager.checkMockLocationPermissions(context)
        Log.e(TAG, "📊 基础权限检查结果: ${standardStatus.message}")

        // 检查模拟定位应用选择状态
        Log.e(TAG, "🔍 检查模拟定位应用选择状态...")
        val isMockAppSelected = checkMockLocationAppSelected(context)
        Log.e(TAG, "📊 模拟定位应用选择状态: ${if (isMockAppSelected) "已选择" else "未选择"}")

        if (standardStatus != MockLocationStatus.READY) {
            Log.e(TAG, "❌ 基础权限检查未通过，无法启动模拟定位")
            return MockLocationResult.Failure(standardStatus, getSetupInstructions(context, standardStatus))
        }

        if (!isMockAppSelected) {
            Log.e(TAG, "❌ 当前应用未被选择为模拟定位应用")
            return MockLocationResult.Failure(
                MockLocationStatus.MOCK_APP_NOT_SELECTED,
                getSetupInstructions(context, MockLocationStatus.MOCK_APP_NOT_SELECTED)
            )
        }

        // 获取Shizuku状态（用于错误报告）
        val shizukuStatus = ShizukuStatusMonitor.getCurrentShizukuStatus(context)

        // Priority Mode: Shizuku增强模式 (增强模式开启时优先尝试)
        if (enableShizukuMode) {
            Log.e(TAG, "🔧🔧🔧 Shizuku增强模式已开启，优先尝试Shizuku模式...")
            Log.e(TAG, "🔧 Shizuku状态检查结果: ${shizukuStatus.name} - ${shizukuStatus.message}")
            Log.e(TAG, "🔧 即将进入Shizuku状态判断分支...")

            when (shizukuStatus) {
                ShizukuStatus.READY -> {
                    Log.e(TAG, "🚀🚀🚀 Shizuku状态就绪，尝试启动Shizuku增强模式")
                    try {
                        Log.e(TAG, "📞📞📞 即将调用MockLocationManager.start()")
                        val result = MockLocationManager.start(context, latitude, longitude)
                        Log.e(TAG, "📞📞📞 MockLocationManager.start()返回结果: $result")

                        if (result) {
                            currentStrategy = MockLocationStrategy.SHIZUKU
                            isRunning = true
                            startMonitoring(context)

                            // 启动位置持续监控
                            LocationPersistenceManager.getInstance().startPersistenceMonitoring(
                                context, latitude, longitude, MockLocationStrategy.SHIZUKU
                            )

                            // 启动WiFi干扰监控
                            WiFiInterferenceHandler.getInstance().startWifiInterferenceMonitoring(
                                context, { hasInterference ->
                                    if (hasInterference) {
                                        Log.w(TAG, "⚠️ 检测到WiFi干扰，增强位置监控")
                                    }
                                }
                            )

                            Log.e(TAG, "✅✅✅ 成功使用Shizuku增强模式启动模拟定位")
                            return MockLocationResult.Success(MockLocationStrategy.SHIZUKU)
                        } else {
                            Log.e(TAG, "❌❌❌ Shizuku增强模式启动失败，将继续尝试其他模式")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌❌❌ Shizuku增强模式启动异常: ${e.message}", e)
                    }
                }
                ShizukuStatus.NO_PERMISSION -> {
                    Log.w(TAG, "🔐 Shizuku已安装但需要授权，已请求权限")
                    Log.w(TAG, "💡 增强模式已开启，但Shizuku需要授权才能使用")
                    ShizukuStatusMonitor.requestShizukuPermission()
                    retryShizukuMode = true
                }
                ShizukuStatus.NOT_RUNNING -> {
                    Log.w(TAG, "⚠️ Shizuku已安装但未启动")
                    Log.w(TAG, "💡 增强模式已开启，但Shizuku服务未运行")
                    Log.w(TAG, "💡 请启动Shizuku应用并开启服务，然后重试模拟定位")
                }
                ShizukuStatus.NOT_INSTALLED -> {
                    Log.w(TAG, "⚠️ 增强模式已开启但Shizuku未安装")
                    Log.w(TAG, "💡 请安装Shizuku应用以使用增强功能，或关闭增强模式使用标准功能")
                    Log.w(TAG, "💡 将继续使用标准模式进行模拟定位")
                }
                ShizukuStatus.ERROR -> {
                    Log.w(TAG, "⚠️ Shizuku状态检测出错")
                    Log.w(TAG, "💡 增强模式已开启，但Shizuku状态异常，将使用标准模式")
                }
            }
        }

        // Primary Mode: 高级反检测模式 (默认使用最强方法)
        Log.d(TAG, "🛡️ 尝试高级反检测模式 (Primary Mode)")
        if (AntiDetectionMockLocationManager.startAntiDetection(context, latitude, longitude)) {
            currentStrategy = MockLocationStrategy.ANTI_DETECTION
            isRunning = true
            startMonitoring(context)

            // 启动位置持续监控
            LocationPersistenceManager.getInstance().startPersistenceMonitoring(
                context, latitude, longitude, MockLocationStrategy.ANTI_DETECTION
            )

            // 启动WiFi干扰监控
            WiFiInterferenceHandler.getInstance().startWifiInterferenceMonitoring(
                context, { hasInterference ->
                    if (hasInterference) {
                        Log.w(TAG, "⚠️ 检测到WiFi干扰，增强反检测处理")
                    }
                }
            )

            Log.d(TAG, "✅ 使用高级反检测模式")
            return MockLocationResult.Success(MockLocationStrategy.ANTI_DETECTION)
        }

        // Secondary Mode: 标准模式 (不依赖Shizuku)
        Log.d(TAG, "🔧 尝试标准模式 (Secondary Mode)")
        val standardError = if (StandardMockLocationManager.start(context, latitude, longitude)) {
            currentStrategy = MockLocationStrategy.STANDARD
            isRunning = true
            startMonitoring(context)

            // 启动位置持续监控
            LocationPersistenceManager.getInstance().startPersistenceMonitoring(
                context, latitude, longitude, MockLocationStrategy.STANDARD
            )

            // 启动WiFi干扰监控（钉钉模式）
            WiFiInterferenceHandler.getInstance().startWifiInterferenceMonitoring(
                context, { hasInterference ->
                    if (hasInterference) {
                        Log.w(TAG, "⚠️ 检测到WiFi干扰，建议关闭WiFi或使用增强模式")
                    }
                }, dingTalkMode = true // 标准模式通常用于钉钉
            )

            Log.d(TAG, "✅ 使用标准模式")
            return MockLocationResult.Success(MockLocationStrategy.STANDARD)
        } else {
            val error = StandardMockLocationManager.getLastError()
            Log.w(TAG, "⚠️ 标准模式失败: $error")
            error
        }

        // 所有模式都失败，提供设置指导
        Log.e(TAG, "❌ 所有模拟定位模式都失败")

        // 收集所有失败原因
        val allErrors = mutableListOf<String>()

        if (standardError != null) {
            allErrors.add("标准模式: $standardError")
        }
        allErrors.add("Shizuku模式: ${shizukuStatus.message}")

        // 创建综合错误状态
        val combinedError = if (standardStatus == MockLocationStatus.READY && standardError != null) {
            // 如果权限检查通过但实际启动失败，创建新的错误状态
            MockLocationStatus.LOCATION_SERVICE_UNAVAILABLE
        } else {
            standardStatus
        }

        // 优先提供标准模式的设置指导，但包含详细错误信息
        val instructions = getSetupInstructions(context, combinedError).toMutableList()

        // 添加详细错误信息
        if (allErrors.isNotEmpty()) {
            instructions.add(0, SetupInstruction(
                title = "详细错误信息",
                description = allErrors.joinToString("; "),
                action = null
            ))
        }

        return MockLocationResult.Failure(combinedError, instructions)
    }
    
    /**
     * 停止模拟定位
     */
    fun stop(context: Context) {
        if (!isRunning) return
        
        synchronized(this) {
            isRunning = false

            // 停止位置持续监控
            LocationPersistenceManager.getInstance().stopPersistenceMonitoring()

            // 停止WiFi干扰监控
            WiFiInterferenceHandler.getInstance().stopWifiInterferenceMonitoring()

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
                MockLocationStrategy.ANTI_DETECTION -> AntiDetectionMockLocationManager.stop(context)
                MockLocationStrategy.SHIZUKU -> MockLocationManager.stop(context)
                MockLocationStrategy.STANDARD -> StandardMockLocationManager.stop(context)
                MockLocationStrategy.ENHANCED -> StandardMockLocationManager.stop(context) // 兼容性处理
                MockLocationStrategy.NONE -> { /* 无需操作 */ }
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

        // 更新位置持续监控的目标位置
        LocationPersistenceManager.getInstance().updateTargetLocation(latitude, longitude)

        when (currentStrategy) {
            MockLocationStrategy.ANTI_DETECTION -> AntiDetectionMockLocationManager.updateLocation(latitude, longitude)
            MockLocationStrategy.STANDARD -> StandardMockLocationManager.updateLocation(latitude, longitude)
            MockLocationStrategy.ENHANCED -> EnhancedMockLocationManager.smartStart(context, latitude, longitude)
            MockLocationStrategy.SHIZUKU -> { /* Shizuku模式通过定时任务自动更新 */ }
            MockLocationStrategy.NONE -> { /* 无需操作 */ }
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
                        title = "权限配置",
                        description = "ACCESS_MOCK_LOCATION权限已在AndroidManifest.xml中声明，请确保在开发者选项中选择了本应用作为模拟定位应用",
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
                    MockLocationStrategy.STANDARD -> {
                        // 标准模式监控
                        if (!StandardMockLocationManager.isRunning()) {
                            Log.w(TAG, "标准模拟定位意外停止，尝试重启")
                            StandardMockLocationManager.start(context, currentLatitude, currentLongitude)
                        }
                    }
                    MockLocationStrategy.SHIZUKU -> { /* Shizuku模式有自己的监控机制 */ }
                    MockLocationStrategy.ENHANCED -> { /* 增强模式兼容性处理 */ }
                    MockLocationStrategy.NONE -> { /* 无需监控 */ }
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
     * 检查当前应用是否被选择为模拟定位应用
     */
    private fun checkMockLocationAppSelected(context: Context): Boolean {
        return try {
            // 方法1：检查Settings.Secure.ALLOW_MOCK_LOCATION（Android 6.0以下）
            val allowMockLocation = try {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION, 0) == 1
            } catch (e: Exception) {
                false
            }

            // 方法2：尝试创建测试提供者来验证（Android 6.0+）
            val canCreateTestProvider = try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val testProvider = "test_provider_${System.currentTimeMillis()}"

                // 尝试添加测试提供者
                locationManager.addTestProvider(
                    testProvider,
                    false, false, false, false, true, true, true, 1, 1
                )

                // 如果成功，立即移除
                locationManager.removeTestProvider(testProvider)
                true
            } catch (e: SecurityException) {
                Log.d(TAG, "🔍 无法创建测试提供者: ${e.message}")
                false
            } catch (e: Exception) {
                Log.d(TAG, "🔍 测试提供者检查异常: ${e.message}")
                false
            }

            val result = allowMockLocation || canCreateTestProvider
            Log.d(TAG, "🔍 模拟定位应用检查: allowMockLocation=$allowMockLocation, canCreateTestProvider=$canCreateTestProvider, 结果=$result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "检查模拟定位应用选择状态失败: ${e.message}", e)
            false
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
    ANTI_DETECTION("高级反检测模式"),
    STANDARD("标准模式"),
    SHIZUKU("Shizuku增强模式"),
    // 保留兼容性
    @Deprecated("使用新的策略名称")
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
