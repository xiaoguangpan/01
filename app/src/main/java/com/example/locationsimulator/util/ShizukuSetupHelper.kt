package com.example.locationsimulator.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Shizuku设置助手
 * 
 * 提供简化的Shizuku安装和配置流程：
 * 1. 自动检测设备类型和Android版本
 * 2. 提供针对性的设置指导
 * 3. 支持无线调试一键配置
 * 4. 提供自动化脚本下载
 */
object ShizukuSetupHelper {
    
    private val TAG = Constants.LogTags.SHIZUKU_MANAGER
    
    /**
     * 检查Shizuku状态
     */
    fun checkShizukuStatus(): ShizukuStatus {
        return try {
            if (!isShizukuInstalled()) {
                ShizukuStatus.NOT_INSTALLED
            } else if (!Shizuku.pingBinder()) {
                ShizukuStatus.NOT_RUNNING
            } else if (Shizuku.checkSelfPermission() != 0) {
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
     * 获取设备特定的设置指导
     */
    fun getDeviceSpecificInstructions(context: Context): List<ShizukuInstruction> {
        val instructions = mutableListOf<ShizukuInstruction>()
        val deviceBrand = Build.BRAND.lowercase()
        val androidVersion = Build.VERSION.SDK_INT
        
        // 1. 安装Shizuku
        instructions.add(
            ShizukuInstruction(
                step = 1,
                title = "安装Shizuku应用",
                description = "从GitHub或应用商店下载安装Shizuku",
                action = {
                    openShizukuDownloadPage(context)
                },
                isRequired = true
            )
        )
        
        // 2. 根据Android版本选择激活方式
        if (androidVersion >= Build.VERSION_CODES.R) { // Android 11+
            instructions.add(
                ShizukuInstruction(
                    step = 2,
                    title = "启用无线调试（推荐）",
                    description = "Android 11+支持无线调试，无需连接电脑",
                    action = {
                        openWirelessDebuggingSettings(context)
                    },
                    isRequired = false
                )
            )
            
            instructions.add(
                ShizukuInstruction(
                    step = 3,
                    title = "在Shizuku中配置无线调试",
                    description = "打开Shizuku → 通过无线调试启动",
                    action = {
                        openShizukuApp(context)
                    },
                    isRequired = true
                )
            )
        } else {
            instructions.add(
                ShizukuInstruction(
                    step = 2,
                    title = "启用USB调试",
                    description = "进入开发者选项 → 启用USB调试",
                    action = {
                        openDeveloperOptions(context)
                    },
                    isRequired = true
                )
            )
            
            instructions.add(
                ShizukuInstruction(
                    step = 3,
                    title = "连接电脑激活Shizuku",
                    description = "使用ADB命令激活Shizuku服务",
                    action = {
                        showAdbCommands(context)
                    },
                    isRequired = true
                )
            )
        }
        
        // 3. 设备特定优化
        when {
            deviceBrand.contains("xiaomi") -> {
                instructions.add(
                    ShizukuInstruction(
                        step = 4,
                        title = "小米设备特殊设置",
                        description = "关闭MIUI优化，允许后台运行",
                        action = {
                            showXiaomiOptimizations(context)
                        },
                        isRequired = false
                    )
                )
            }
            deviceBrand.contains("huawei") -> {
                instructions.add(
                    ShizukuInstruction(
                        step = 4,
                        title = "华为设备特殊设置",
                        description = "在电池优化中允许Shizuku后台运行",
                        action = {
                            openBatteryOptimization(context)
                        },
                        isRequired = false
                    )
                )
            }
            deviceBrand.contains("oppo") || deviceBrand.contains("vivo") -> {
                instructions.add(
                    ShizukuInstruction(
                        step = 4,
                        title = "OPPO/vivo设备特殊设置",
                        description = "在权限管理中允许Shizuku自启动",
                        action = {
                            openAutoStartSettings(context)
                        },
                        isRequired = false
                    )
                )
            }
        }
        
        return instructions
    }
    
    /**
     * 生成一键配置脚本
     */
    fun generateAutoSetupScript(context: Context): String {
        val deviceInfo = """
            # 设备信息
            # 品牌: ${Build.BRAND}
            # 型号: ${Build.MODEL}
            # Android版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            
        """.trimIndent()
        
        val script = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 无线调试脚本
            """
            $deviceInfo
            # Shizuku无线调试一键配置脚本
            # 使用方法：
            # 1. 在手机上启用无线调试
            # 2. 在电脑上运行此脚本
            
            echo "正在连接到设备..."
            adb connect [设备IP]:[端口]
            
            echo "启动Shizuku服务..."
            adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
            
            echo "配置完成！"
            """.trimIndent()
        } else {
            // Android 10及以下USB调试脚本
            """
            $deviceInfo
            # Shizuku USB调试一键配置脚本
            # 使用方法：
            # 1. 连接USB线到电脑
            # 2. 启用USB调试
            # 3. 运行此脚本
            
            echo "检查设备连接..."
            adb devices
            
            echo "启动Shizuku服务..."
            adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
            
            echo "配置完成！"
            """.trimIndent()
        }
        
        return script
    }
    
    /**
     * 检查是否安装了Shizuku
     */
    private fun isShizukuInstalled(): Boolean {
        return try {
            Shizuku.getVersion() > 0
        } catch (e: Exception) {
            false
        }
    }
    
    // ========== 操作方法 ==========
    
    private fun openShizukuDownloadPage(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开Shizuku下载页面", e)
        }
    }
    
    private fun openWirelessDebuggingSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开开发者选项", e)
        }
    }
    
    private fun openShizukuApp(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                context.startActivity(intent)
            } else {
                Log.e(TAG, "Shizuku应用未安装")
            }
        } catch (e: Exception) {
            Log.e(TAG, "无法打开Shizuku应用", e)
        }
    }
    
    private fun openDeveloperOptions(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开开发者选项", e)
        }
    }
    
    private fun showAdbCommands(context: Context) {
        // 这里可以显示ADB命令对话框或跳转到说明页面
        Log.d(TAG, "显示ADB命令说明")
    }
    
    private fun showXiaomiOptimizations(context: Context) {
        try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
            intent.putExtra("extra_pkgname", "moe.shizuku.privileged.api")
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开小米权限设置", e)
        }
    }
    
    private fun openBatteryOptimization(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开电池优化设置", e)
        }
    }
    
    private fun openAutoStartSettings(context: Context) {
        try {
            val intent = Intent()
            intent.component = android.content.ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开自启动设置", e)
        }
    }
}

/**
 * Shizuku状态枚举
 */
enum class ShizukuStatus(val message: String) {
    NOT_INSTALLED("未安装Shizuku"),
    NOT_RUNNING("Shizuku未运行"),
    NO_PERMISSION("无Shizuku权限"),
    READY("Shizuku就绪"),
    ERROR("检查状态出错")
}

/**
 * Shizuku设置指导
 */
data class ShizukuInstruction(
    val step: Int,
    val title: String,
    val description: String,
    val action: (() -> Unit)?,
    val isRequired: Boolean
)
