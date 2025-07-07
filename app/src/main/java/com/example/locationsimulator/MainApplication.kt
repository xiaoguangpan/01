package com.example.locationsimulator

import android.app.Application
import android.util.Log
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.example.locationsimulator.util.SHA1Util

class MainApplication : Application() {

    companion object {
        private const val TAG = "MainApplication"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "🚀 Application启动，初始化百度SDK...")

        try {
            // 设置隐私合规同意
            SDKInitializer.setAgreePrivacy(this, true)
            Log.d(TAG, "✅ 隐私合规同意已设置")

            // 初始化百度地图SDK
            SDKInitializer.initialize(this)
            Log.d(TAG, "✅ 百度地图SDK初始化完成")

            // 设置坐标类型为BD09LL（百度坐标）
            SDKInitializer.setCoordType(CoordType.BD09LL)
            Log.d(TAG, "✅ 坐标类型设置为BD09LL")

            // 检查API Key配置
            checkApiKeyConfiguration()

            // 输出SHA1配置信息
            SHA1Util.logSHA1Info(this)

        } catch (e: Exception) {
            Log.e(TAG, "❌ 百度SDK初始化失败: ${e.message}", e)
        }
    }

    private fun checkApiKeyConfiguration() {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
            val apiKey = appInfo.metaData?.getString("com.baidu.lbsapi.API_KEY")

            if (apiKey.isNullOrEmpty()) {
                Log.e(TAG, "❌ 百度API Key未配置或为空")
            } else {
                Log.d(TAG, "✅ 百度API Key已配置: ${apiKey.take(10)}...")

                // 检查包名
                Log.d(TAG, "📦 应用包名: $packageName")

                // 提示用户检查SHA1配置
                Log.d(TAG, "⚠️ 请确保在百度开发者平台配置了正确的SHA1安全码")
                Log.d(TAG, "📋 安全码格式应为: SHA1;包名;应用名称")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 检查API Key配置失败: ${e.message}")
        }
    }
}
