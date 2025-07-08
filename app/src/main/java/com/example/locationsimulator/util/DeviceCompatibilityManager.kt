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
        HUAWEI, XIAOMI, XIAOMI_HYPEROS, OPPO, VIVO, SAMSUNG, ONEPLUS, REALME, UNKNOWN
    }

    // 系统版本信息
    data class SystemInfo(
        val brand: DeviceBrand,
        val systemName: String,
        val systemVersion: String,
        val isHyperOS: Boolean,
        val hyperOSVersion: String?
    )
    
    /**
     * 获取详细的系统信息
     */
    fun getSystemInfo(): SystemInfo {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val display = Build.DISPLAY
        val version = Build.VERSION.RELEASE

        // 检测HyperOS
        val isHyperOS = display.contains("HyperOS", ignoreCase = true) ||
                       getSystemProperty("ro.miui.ui.version.name")?.contains("HyperOS") == true

        val hyperOSVersion = if (isHyperOS) {
            extractHyperOSVersion(display)
        } else null

        val deviceBrand = when {
            manufacturer.contains("huawei") || brand.contains("huawei") ||
            manufacturer.contains("honor") || brand.contains("honor") -> DeviceBrand.HUAWEI

            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
            manufacturer.contains("redmi") || brand.contains("redmi") -> {
                if (isHyperOS) DeviceBrand.XIAOMI_HYPEROS else DeviceBrand.XIAOMI
            }

            manufacturer.contains("oppo") || brand.contains("oppo") -> DeviceBrand.OPPO
            manufacturer.contains("vivo") || brand.contains("vivo") -> DeviceBrand.VIVO
            manufacturer.contains("samsung") || brand.contains("samsung") -> DeviceBrand.SAMSUNG
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> DeviceBrand.ONEPLUS
            manufacturer.contains("realme") || brand.contains("realme") -> DeviceBrand.REALME
            else -> DeviceBrand.UNKNOWN
        }

        val systemName = when {
            isHyperOS -> "HyperOS"
            manufacturer.contains("xiaomi") -> "MIUI"
            manufacturer.contains("huawei") -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "HarmonyOS" else "EMUI"
            manufacturer.contains("oppo") -> "ColorOS"
            manufacturer.contains("vivo") -> "OriginOS"
            manufacturer.contains("samsung") -> "One UI"
            else -> "Android"
        }

        return SystemInfo(deviceBrand, systemName, version, isHyperOS, hyperOSVersion)
    }

    /**
     * 获取当前设备品牌（兼容性方法）
     */
    fun getCurrentDeviceBrand(): DeviceBrand {
        return getSystemInfo().brand
    }

    /**
     * 提取HyperOS版本号
     */
    private fun extractHyperOSVersion(display: String): String? {
        val regex = Regex("HyperOS\\s+(\\d+\\.\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        return regex.find(display)?.groupValues?.get(1)
    }

    /**
     * 获取系统属性
     */
    private fun getSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            process.inputStream.bufferedReader().readLine()?.trim()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 检查设备是否需要特殊处理
     */
    fun requiresSpecialHandling(): Boolean {
        val brand = getCurrentDeviceBrand()
        return brand in listOf(DeviceBrand.HUAWEI, DeviceBrand.XIAOMI, DeviceBrand.XIAOMI_HYPEROS, DeviceBrand.OPPO, DeviceBrand.VIVO)
    }
    
    /**
     * 获取品牌特定的模拟定位设置指导
     */
    fun getBrandSpecificInstructions(context: Context): String {
        val systemInfo = getSystemInfo()
        return when (systemInfo.brand) {
            DeviceBrand.XIAOMI_HYPEROS -> """
                小米HyperOS ${systemInfo.hyperOSVersion ?: "2.0+"} 特殊设置：

                ⚠️ HyperOS对模拟定位有严格限制，需要完整设置：

                1. 开启开发者选项：
                   - 设置 → 我的设备 → 全部参数 → 连续点击"MIUI版本"7次

                2. 模拟定位设置：
                   - 设置 → 更多设置 → 开发者选项
                   - 开启"USB调试"
                   - 开启"允许模拟位置"
                   - 在"选择模拟位置信息应用"中选择"定红"

                3. 权限管理（关键步骤）：
                   - 设置 → 应用设置 → 应用管理 → 定红 → 权限管理
                   - 位置信息 → 设置为"始终允许"
                   - 在"权限管理"中关闭"位置信息保护"

                4. HyperOS特殊设置：
                   - 设置 → 隐私保护 → 特殊权限 → 设备管理器
                   - 允许"定红"作为设备管理器
                   - 设置 → 隐私保护 → 位置服务 → 关闭"位置服务增强"

                5. 安全中心设置：
                   - 安全中心 → 授权管理 → 应用权限管理 → 定红
                   - 位置信息 → 允许
                   - 自启动管理 → 允许"定红"自启动

                6. 重启设备后重新测试

                注意：HyperOS 2.0+对模拟定位检测更严格，可能需要多次尝试
            """.trimIndent()

            DeviceBrand.XIAOMI -> """
                小米MIUI设备特殊设置：
                1. 进入设置 → 更多设置 → 开发者选项
                2. 开启"允许模拟位置"
                3. 在安全中心 → 授权管理 → 应用权限管理中允许位置权限
                4. 关闭MIUI优化（如果需要）
            """.trimIndent()

            DeviceBrand.HUAWEI -> """
                华为设备特殊设置：
                1. 进入设置 → 系统和更新 → 开发人员选项
                2. 开启"允许模拟位置"
                3. 选择"定红"作为模拟位置应用
                4. 关闭"位置服务"中的"提升位置精度"
                注意：HarmonyOS可能完全禁用模拟定位功能
            """.trimIndent()
            DeviceBrand.HUAWEI -> """
                华为设备特殊设置：
                1. 进入设置 → 系统和更新 → 开发人员选项
                2. 开启"允许模拟位置"
                3. 选择"定红"作为模拟位置应用
                4. 关闭"位置服务"中的"提升位置精度"
                注意：HarmonyOS可能完全禁用模拟定位功能
            """.trimIndent()

            DeviceBrand.XIAOMI -> """
                小米MIUI设备特殊设置：
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
        val systemInfo = getSystemInfo()
        return when (systemInfo.brand) {
            DeviceBrand.XIAOMI_HYPEROS -> {
                true to "小米HyperOS ${systemInfo.hyperOSVersion ?: "2.0+"} 对模拟定位有极严格的限制，需要完整的权限配置和特殊设置才能正常工作"
            }
            DeviceBrand.HUAWEI -> {
                val isHarmonyOS = systemInfo.systemName == "HarmonyOS"
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
        val systemInfo = getSystemInfo()
        return """
            设备品牌: ${systemInfo.brand}
            系统名称: ${systemInfo.systemName}
            系统版本: ${systemInfo.systemVersion}
            ${if (systemInfo.isHyperOS) "HyperOS版本: ${systemInfo.hyperOSVersion}" else ""}
            制造商: ${Build.MANUFACTURER}
            品牌: ${Build.BRAND}
            型号: ${Build.MODEL}
            Android版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            显示版本: ${Build.DISPLAY}
            特殊处理: ${if (requiresSpecialHandling()) "需要" else "不需要"}
        """.trimIndent()
    }
}
