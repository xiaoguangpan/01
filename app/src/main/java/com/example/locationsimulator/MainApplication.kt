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

        Log.d(TAG, "🚀 Application启动，开始初始化百度SDK...")
        Log.d(TAG, "📱 应用包名: $packageName")
        Log.d(TAG, "🔧 SDK版本检查开始...")

        try {
            // 第一步：设置隐私合规同意（必须在initialize之前调用）
            Log.d(TAG, "1️⃣ 设置隐私合规同意...")
            SDKInitializer.setAgreePrivacy(this, true)
            Log.d(TAG, "✅ 隐私合规同意已设置")

            // 第二步：检查API Key配置（在初始化前检查）
            Log.d(TAG, "2️⃣ 检查API Key配置...")
            if (!checkApiKeyConfiguration()) {
                Log.e(TAG, "❌ API Key配置检查失败，地图可能无法正常显示")
            }

            // 第三步：初始化百度地图SDK
            Log.d(TAG, "3️⃣ 初始化百度地图SDK...")
            val initResult = SDKInitializer.initialize(this)
            if (initResult == SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_OK) {
                Log.d(TAG, "✅ 百度地图SDK初始化成功")
            } else {
                Log.e(TAG, "❌ 百度地图SDK初始化失败，返回值: $initResult")
            }

            // 第四步：设置坐标类型为BD09LL（百度坐标）
            Log.d(TAG, "4️⃣ 设置坐标类型...")
            SDKInitializer.setCoordType(CoordType.BD09LL)
            Log.d(TAG, "✅ 坐标类型设置为BD09LL")

            // 第五步：输出SHA1配置信息
            Log.d(TAG, "5️⃣ 检查SHA1配置...")
            SHA1Util.logSHA1Info(this)

            Log.d(TAG, "🎉 百度SDK初始化流程完成")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 百度SDK初始化过程中发生异常: ${e.message}", e)
            e.printStackTrace()
        }
    }

    private fun checkApiKeyConfiguration(): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
            val apiKey = appInfo.metaData?.getString("com.baidu.lbsapi.API_KEY")

            if (apiKey.isNullOrEmpty()) {
                Log.e(TAG, "❌ 百度API Key未配置或为空")
                Log.e(TAG, "💡 请检查build.gradle.kts中的BAIDU_MAP_AK环境变量")
                Log.e(TAG, "💡 请检查AndroidManifest.xml中的meta-data配置")
                false
            } else {
                Log.d(TAG, "✅ 百度API Key已配置: ${apiKey.take(10)}...")
                Log.d(TAG, "📦 应用包名: $packageName")

                // 验证API Key格式
                if (apiKey.length < 20) {
                    Log.w(TAG, "⚠️ API Key长度异常，可能配置错误")
                }

                // 提示用户检查SHA1配置
                Log.d(TAG, "⚠️ 请确保在百度开发者平台配置了正确的SHA1安全码")
                Log.d(TAG, "📋 安全码格式应为: SHA1;包名;应用名称")
                Log.d(TAG, "🔗 百度开发者平台: https://lbsyun.baidu.com/apiconsole/key")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 检查API Key配置失败: ${e.message}")
            false
        }
    }
}
