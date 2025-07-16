package com.example.locationsimulator.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.*

/**
 * 应用差异化处理器 - 针对不同应用的特殊处理策略
 * 核心功能：
 * 1. 检测当前运行的目标应用
 * 2. 提供应用特定的模拟定位策略
 * 3. 实现应用特定的反检测机制
 * 4. 处理应用特定的技术限制
 */
object AppSpecificHandler {
    
    private const val TAG = "AppSpecificHandler"
    
    // 支持的应用包名
    object SupportedApps {
        const val DINGTALK = "com.alibaba.android.rimet"
        const val GAODE_MAP = "com.autonavi.minimap"
        const val BAIDU_MAP = "com.baidu.BaiduMap"
        const val TENCENT_MAP = "com.tencent.map"
        const val WECHAT = "com.tencent.mm"
        const val QQ = "com.tencent.mobileqq"
        const val ALIPAY = "com.eg.android.AlipayGphone"
    }
    
    // 应用特定配置
    data class AppConfig(
        val packageName: String,
        val displayName: String,
        val recommendedStrategy: MockLocationStrategy,
        val requiresHighFrequency: Boolean,
        val requiresWifiEnabled: Boolean,
        val hasStrongAntiDetection: Boolean,
        val specialHandling: String?
    )
    
    private val appConfigs = mapOf(
        SupportedApps.DINGTALK to AppConfig(
            packageName = SupportedApps.DINGTALK,
            displayName = "钉钉",
            recommendedStrategy = MockLocationStrategy.STANDARD,
            requiresHighFrequency = true,
            requiresWifiEnabled = true,
            hasStrongAntiDetection = true,
            specialHandling = "飞行模式重置 + WiFi兼容处理"
        ),
        SupportedApps.GAODE_MAP to AppConfig(
            packageName = SupportedApps.GAODE_MAP,
            displayName = "高德地图",
            recommendedStrategy = MockLocationStrategy.SHIZUKU,
            requiresHighFrequency = true,
            requiresWifiEnabled = false,
            hasStrongAntiDetection = true,
            specialHandling = "广告延迟处理 + 位置刷新拦截"
        ),
        SupportedApps.BAIDU_MAP to AppConfig(
            packageName = SupportedApps.BAIDU_MAP,
            displayName = "百度地图",
            recommendedStrategy = MockLocationStrategy.ANTI_DETECTION,
            requiresHighFrequency = false,
            requiresWifiEnabled = false,
            hasStrongAntiDetection = true,
            specialHandling = "多提供商轮换 + 信号强度模拟"
        ),
        SupportedApps.TENCENT_MAP to AppConfig(
            packageName = SupportedApps.TENCENT_MAP,
            displayName = "腾讯地图",
            recommendedStrategy = MockLocationStrategy.STANDARD,
            requiresHighFrequency = false,
            requiresWifiEnabled = false,
            hasStrongAntiDetection = false,
            specialHandling = null
        ),
        SupportedApps.WECHAT to AppConfig(
            packageName = SupportedApps.WECHAT,
            displayName = "微信",
            recommendedStrategy = MockLocationStrategy.STANDARD,
            requiresHighFrequency = false,
            requiresWifiEnabled = false,
            hasStrongAntiDetection = false,
            specialHandling = "位置共享兼容处理"
        ),
        SupportedApps.ALIPAY to AppConfig(
            packageName = SupportedApps.ALIPAY,
            displayName = "支付宝",
            recommendedStrategy = MockLocationStrategy.STANDARD,
            requiresHighFrequency = false,
            requiresWifiEnabled = false,
            hasStrongAntiDetection = false,
            specialHandling = null
        )
    )
    
    /**
     * 检测已安装的目标应用
     */
    fun getInstalledTargetApps(context: Context): List<AppConfig> {
        val packageManager = context.packageManager
        val installedApps = mutableListOf<AppConfig>()
        
        appConfigs.values.forEach { config ->
            try {
                packageManager.getPackageInfo(config.packageName, 0)
                installedApps.add(config)
                Log.d(TAG, "✅ 检测到已安装应用: ${config.displayName}")
            } catch (e: PackageManager.NameNotFoundException) {
                Log.d(TAG, "❌ 应用未安装: ${config.displayName}")
            }
        }
        
        return installedApps
    }
    
    /**
     * 获取应用配置
     */
    fun getAppConfig(packageName: String): AppConfig? {
        return appConfigs[packageName]
    }
    
    /**
     * 获取推荐的模拟定位策略
     */
    fun getRecommendedStrategy(packageName: String?): MockLocationStrategy {
        return packageName?.let { appConfigs[it]?.recommendedStrategy } 
            ?: MockLocationStrategy.STANDARD
    }
    
    /**
     * 检查应用是否需要高频更新
     */
    fun requiresHighFrequency(packageName: String?): Boolean {
        return packageName?.let { appConfigs[it]?.requiresHighFrequency } ?: false
    }
    
    /**
     * 检查应用是否需要WiFi开启
     */
    fun requiresWifiEnabled(packageName: String?): Boolean {
        return packageName?.let { appConfigs[it]?.requiresWifiEnabled } ?: false
    }
    
    /**
     * 检查应用是否有强反检测机制
     */
    fun hasStrongAntiDetection(packageName: String?): Boolean {
        return packageName?.let { appConfigs[it]?.hasStrongAntiDetection } ?: false
    }
    
    /**
     * 获取应用特殊处理说明
     */
    fun getSpecialHandling(packageName: String?): String? {
        return packageName?.let { appConfigs[it]?.specialHandling }
    }
    
    /**
     * 获取应用显示名称
     */
    fun getAppDisplayName(packageName: String?): String {
        return packageName?.let { appConfigs[it]?.displayName } ?: "未知应用"
    }
    
    /**
     * 检查应用是否被支持
     */
    fun isAppSupported(packageName: String?): Boolean {
        return packageName != null && appConfigs.containsKey(packageName)
    }
    
    /**
     * 获取所有支持的应用列表
     */
    fun getAllSupportedApps(): List<AppConfig> {
        return appConfigs.values.toList()
    }
    
    /**
     * 为特定应用生成优化建议
     */
    fun generateOptimizationTips(packageName: String?): List<String> {
        val config = packageName?.let { appConfigs[it] } ?: return emptyList()
        val tips = mutableListOf<String>()
        
        when (config.packageName) {
            SupportedApps.DINGTALK -> {
                tips.add("💡 钉钉打卡建议:")
                tips.add("• 保持WiFi开启（钉钉要求）")
                tips.add("• 使用飞行模式重置技巧")
                tips.add("• 建议在打卡前1-2分钟启动模拟")
                tips.add("• 打卡后可立即停止模拟")
            }
            SupportedApps.GAODE_MAP -> {
                tips.add("💡 高德地图优化建议:")
                tips.add("• 关闭WiFi以减少干扰")
                tips.add("• 等待3秒广告时间后再检查位置")
                tips.add("• 使用增强模式获得最佳效果")
                tips.add("• 避免频繁切换位置")
            }
            SupportedApps.BAIDU_MAP -> {
                tips.add("💡 百度地图优化建议:")
                tips.add("• 关闭WiFi和蓝牙")
                tips.add("• 使用反检测模式")
                tips.add("• 允许应用获取位置权限")
                tips.add("• 避免在应用启动时立即检查位置")
            }
            else -> {
                tips.add("💡 通用优化建议:")
                tips.add("• 确保应用有位置权限")
                tips.add("• 关闭WiFi以减少干扰")
                tips.add("• 使用标准模式即可")
            }
        }
        
        return tips
    }
    
    /**
     * 执行应用特定的预处理
     */
    suspend fun executePreProcessing(context: Context, packageName: String?): Boolean {
        val config = packageName?.let { appConfigs[it] } ?: return true
        
        Log.d(TAG, "🔧 执行${config.displayName}特定预处理")
        
        return try {
            when (config.packageName) {
                SupportedApps.DINGTALK -> executeDingTalkPreProcessing(context)
                SupportedApps.GAODE_MAP -> executeGaodePreProcessing(context)
                SupportedApps.BAIDU_MAP -> executeBaiduPreProcessing(context)
                else -> true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ${config.displayName}预处理失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 钉钉特定预处理
     */
    private suspend fun executeDingTalkPreProcessing(context: Context): Boolean {
        Log.d(TAG, "🎯 执行钉钉特定预处理")
        
        // 检查WiFi状态
        val wifiHandler = WiFiInterferenceHandler.getInstance()
        if (!wifiHandler.hasInterference()) {
            Log.w(TAG, "⚠️ 钉钉需要WiFi开启，请手动开启WiFi")
        }
        
        // 可以在这里添加更多钉钉特定的预处理逻辑
        return true
    }
    
    /**
     * 高德地图特定预处理
     */
    private suspend fun executeGaodePreProcessing(context: Context): Boolean {
        Log.d(TAG, "🎯 执行高德地图特定预处理")
        
        // 建议关闭WiFi
        val wifiHandler = WiFiInterferenceHandler.getInstance()
        if (wifiHandler.hasInterference()) {
            Log.w(TAG, "💡 建议关闭WiFi以获得更好的模拟效果")
        }
        
        return true
    }
    
    /**
     * 百度地图特定预处理
     */
    private suspend fun executeBaiduPreProcessing(context: Context): Boolean {
        Log.d(TAG, "🎯 执行百度地图特定预处理")
        
        // 百度地图特定的预处理逻辑
        return true
    }
    
    /**
     * 执行应用特定的后处理
     */
    suspend fun executePostProcessing(context: Context, packageName: String?): Boolean {
        val config = packageName?.let { appConfigs[it] } ?: return true
        
        Log.d(TAG, "🔧 执行${config.displayName}特定后处理")
        
        return try {
            when (config.packageName) {
                SupportedApps.DINGTALK -> executeDingTalkPostProcessing(context)
                SupportedApps.GAODE_MAP -> executeGaodePostProcessing(context)
                SupportedApps.BAIDU_MAP -> executeBaiduPostProcessing(context)
                else -> true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ${config.displayName}后处理失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 钉钉特定后处理
     */
    private suspend fun executeDingTalkPostProcessing(context: Context): Boolean {
        Log.d(TAG, "🎯 执行钉钉特定后处理")
        // 钉钉打卡后的清理工作
        return true
    }
    
    /**
     * 高德地图特定后处理
     */
    private suspend fun executeGaodePostProcessing(context: Context): Boolean {
        Log.d(TAG, "🎯 执行高德地图特定后处理")
        // 高德地图使用后的清理工作
        return true
    }
    
    /**
     * 百度地图特定后处理
     */
    private suspend fun executeBaiduPostProcessing(context: Context): Boolean {
        Log.d(TAG, "🎯 执行百度地图特定后处理")
        // 百度地图使用后的清理工作
        return true
    }
}
