package com.example.locationsimulator.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.security.MessageDigest

object SHA1Util {
    private const val TAG = "SHA1Util"
    
    /**
     * 获取应用的SHA1指纹
     */
    fun getAppSHA1(context: Context): String? {
        try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            
            val signatures = packageInfo.signatures
            if (signatures.isNotEmpty()) {
                val signature = signatures[0]
                val md = MessageDigest.getInstance("SHA1")
                md.update(signature.toByteArray())
                
                val digest = md.digest()
                val sha1 = digest.joinToString(":") { 
                    String.format("%02X", it) 
                }
                
                Log.d(TAG, "应用SHA1: $sha1")
                return sha1
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取SHA1失败: ${e.message}")
        }
        return null
    }
    
    /**
     * 生成百度地图安全码格式
     * 格式: SHA1;包名;应用名称
     */
    fun generateBaiduSecurityCode(context: Context, appName: String = "Location Simulator"): String? {
        val sha1 = getAppSHA1(context)
        val packageName = context.packageName
        
        return if (sha1 != null) {
            "$sha1;$packageName;$appName"
        } else {
            null
        }
    }
    
    /**
     * 输出SHA1配置信息到日志
     */
    fun logSHA1Info(context: Context) {
        val sha1 = getAppSHA1(context)
        val packageName = context.packageName
        val securityCode = generateBaiduSecurityCode(context)
        
        Log.d(TAG, "=== 百度地图SHA1配置信息 ===")
        Log.d(TAG, "包名: $packageName")
        Log.d(TAG, "SHA1: $sha1")
        Log.d(TAG, "安全码: $securityCode")
        Log.d(TAG, "配置说明: 请在百度开发者平台的应用配置中填入上述安全码")
        Log.d(TAG, "========================")
    }
}
