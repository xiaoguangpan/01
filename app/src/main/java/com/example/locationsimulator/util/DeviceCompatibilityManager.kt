package com.example.locationsimulator.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * 设备兼容性管理器
 * 处理不同品牌设备的特殊要求和限制
 */
object DeviceCompatibilityManager {
    private const val TAG = "DeviceCompatibility"
    
    // 设备品牌枚举
    enum class DeviceBrand {
        HUAWEI, XIAOMI, OPPO, VIVO, SAMSUNG, ONEPLUS, REALME, UNKNOWN
    }
    
    /**
     * 获取当前设备品牌
     */
    fun getCurrentDeviceBrand(): DeviceBrand {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        
        return when {
            manufacturer.contains("huawei") || brand.contains("huawei") || 
            manufacturer.contains("honor") || brand.contains("honor") -> DeviceBrand.HUAWEI
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
            manufacturer.contains("redmi") || brand.contains("redmi") -> DeviceBrand.XIAOMI
            manufacturer.contains("oppo") || brand.contains("oppo") -> DeviceBrand.OPPO
            manufacturer.contains("vivo") || brand.contains("vivo") -> DeviceBrand.VIVO
            manufacturer.contains("samsung") || brand.contains("samsung") -> DeviceBrand.SAMSUNG
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> DeviceBrand.ONEPLUS
            manufacturer.contains("realme") || brand.contains("realme") -> DeviceBrand.REALME
            else -> DeviceBrand.UNKNOWN
        }
    }
    
    /**
     * 检查设备是否需要特殊处理
     */
    fun requiresSpecialHandling(): Boolean {
        val brand = getCurrentDeviceBrand()
        return brand in listOf(DeviceBrand.HUAWEI, DeviceBrand.XIAOMI, DeviceBrand.OPPO, DeviceBrand.VIVO)
    }
    
    /**
     * 获取品牌特定的模拟定位设置指导
     */
    fun getBrandSpecificInstructions(context: Context): String {
        val brand = getCurrentDeviceBrand()
        return when (brand) {
            DeviceBrand.HUAWEI -> """
                华为设备特殊设置：
                1. 进入设置 → 系统和更新 → 开发人员选项
                2. 开启"允许模拟位置"
                3. 选择"定红"作为模拟位置应用
                4. 关闭"位置服务"中的"提升位置精度"
                注意：HarmonyOS可能完全禁用模拟定位功能
            """.trimIndent()
            
            DeviceBrand.XIAOMI -> """
                小米设备特殊设置：
                1. 进入设置 → 更多设置 → 开发者选项
                2. 开启"允许模拟位置"
                3. 在安全中心 → 授权管理 → 应用权限管理中允许位置权限
                4. 关闭MIUI优化（如果需要）
            """.trimIndent()
            
            DeviceBrand.OPPO -> """
                OPPO设备特殊设置：
                1. 进入设置 → 其他设置 → 开发者选项
                2. 开启"模拟位置信息应用"
                3. 在权限管理中允许位置权限
                4. 关闭ColorOS的位置优化功能
            """.trimIndent()
            
            DeviceBrand.VIVO -> """
                vivo设备特殊设置：
                1. 进入设置 → 系统管理 → 开发者选项
                2. 开启"允许模拟位置"
                3. 在i管家 → 软件管理 → 软件权限管理中允许位置权限
                4. 关闭OriginOS的智能位置服务
            """.trimIndent()
            
            DeviceBrand.SAMSUNG -> """
                三星设备设置：
                1. 进入设置 → 开发者选项
                2. 开启"允许模拟位置"
                3. Knox安全可能会检测模拟定位，建议关闭Knox功能
            """.trimIndent()
            
            else -> """
                通用设置步骤：
                1. 进入设置 → 开发者选项
                2. 开启"允许模拟位置"或"选择模拟位置信息应用"
                3. 选择"定红"作为模拟位置应用
            """.trimIndent()
        }
    }
    
    /**
     * 尝试打开品牌特定的设置页面
     */
    fun openBrandSpecificSettings(context: Context): Boolean {
        val brand = getCurrentDeviceBrand()
        return try {
            val intent = when (brand) {
                DeviceBrand.HUAWEI -> {
                    // 华为开发者选项
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.android.settings",
                            "com.android.settings.DevelopmentSettings"
                        )
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
                DeviceBrand.XIAOMI -> {
                    // 小米开发者选项
                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
                else -> {
                    // 通用开发者选项
                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
            }
            
            context.startActivity(intent)
            Log.d(TAG, "成功打开${brand}设备的开发者选项")
            true
        } catch (e: Exception) {
            Log.e(TAG, "无法打开${brand}设备的特定设置: ${e.message}")
            false
        }
    }
    
    /**
     * 检查设备是否有已知的模拟定位限制
     */
    fun hasKnownLimitations(): Pair<Boolean, String> {
        val brand = getCurrentDeviceBrand()
        return when (brand) {
            DeviceBrand.HUAWEI -> {
                val isHarmonyOS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                                 Build.DISPLAY.contains("Harmony", ignoreCase = true)
                if (isHarmonyOS) {
                    true to "HarmonyOS系统可能完全禁用第三方模拟定位功能"
                } else {
                    true to "华为EMUI系统对模拟定位有严格限制，可能需要关闭位置优化功能"
                }
            }
            DeviceBrand.XIAOMI -> {
                true to "小米MIUI系统需要在安全中心中额外授权位置权限"
            }
            DeviceBrand.SAMSUNG -> {
                true to "三星Knox安全框架可能检测并阻止模拟定位"
            }
            else -> false to ""
        }
    }
    
    /**
     * 获取设备信息用于调试
     */
    fun getDeviceInfo(): String {
        return """
            设备品牌: ${getCurrentDeviceBrand()}
            制造商: ${Build.MANUFACTURER}
            品牌: ${Build.BRAND}
            型号: ${Build.MODEL}
            Android版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            显示版本: ${Build.DISPLAY}
        """.trimIndent()
    }
}
