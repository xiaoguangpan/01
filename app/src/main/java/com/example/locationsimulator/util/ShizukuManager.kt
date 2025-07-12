package com.example.locationsimulator.util

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

object ShizukuManager {

    private val TAG = Constants.LogTags.SHIZUKU_MANAGER

    @Volatile
    private var locationService: Any? = null

    @Volatile
    private var isInitialized = false

    // 反射方法缓存
    private var setTestProviderLocationMethod: Method? = null
    private var setTestProviderEnabledMethod: Method? = null
    private var addTestProviderMethod: Method? = null
    private var removeTestProviderMethod: Method? = null

    private val isShizukuAvailable: Boolean
        get() = try {
            // 使用多种方法检测Shizuku可用性，提高兼容性
            val pingResult = try { Shizuku.pingBinder() } catch (e: Exception) { false }
            val binderResult = try { Shizuku.getBinder() != null } catch (e: Exception) { false }
            val versionResult = try { Shizuku.getVersion() > 0 } catch (e: Exception) { false }
            val uidResult = try { Shizuku.getUid() > 0 } catch (e: Exception) { false }

            val available = pingResult || binderResult || versionResult || uidResult
            Log.d(TAG, "Shizuku可用性检测: ping=$pingResult, binder=$binderResult, version=$versionResult, uid=$uidResult, 结果=$available")
            available
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku可用性检测异常: ${e.message}")
            false
        }

    fun checkPermissions(context: Context): Boolean {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            Log.e(TAG, "Shizuku version is too old.")
            return false
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(Constants.RequestCodes.SHIZUKU_PERMISSION)
            return false
        }

        return true
    }

    private fun getLocationService(): Any? {
        if (locationService != null && isInitialized) {
            return locationService
        }

        if (!isShizukuAvailable) {
            Log.e(TAG, "Shizuku is not available.")
            return null
        }

        synchronized(this) {
            // 双重检查锁定
            if (locationService != null && isInitialized) {
                return locationService
            }

            try {
                val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.LOCATION_SERVICE))

                // 使用反射获取 ILocationManager.Stub.asInterface 方法
                val iLocationManagerClass = Class.forName("android.location.ILocationManager")
                val stubClass = Class.forName("android.location.ILocationManager\$Stub")
                val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)

                locationService = asInterfaceMethod.invoke(null, binder)

                // 缓存反射方法
                initializeReflectionMethods(iLocationManagerClass)
                isInitialized = true

            } catch (e: Exception) {
                Log.e(TAG, "Failed to get LocationManagerService.", e)
                locationService = null
                isInitialized = false
            }
        }

        return locationService
    }

    private fun initializeReflectionMethods(iLocationManagerClass: Class<*>) {
        try {
            setTestProviderLocationMethod = iLocationManagerClass.getMethod(
                "setTestProviderLocation",
                String::class.java,
                Location::class.java
            )
            setTestProviderEnabledMethod = iLocationManagerClass.getMethod(
                "setTestProviderEnabled",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            addTestProviderMethod = iLocationManagerClass.getMethod(
                "addTestProvider",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            removeTestProviderMethod = iLocationManagerClass.getMethod(
                "removeTestProvider",
                String::class.java
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize reflection methods.", e)
            throw e
        }
    }

    fun setMockLocation(location: Location) {
        val service = getLocationService()
        if (service == null) {
            Log.e(TAG, "❌ LocationService不可用，无法设置模拟位置")
            return
        }

        try {
            Log.d(TAG, "🔧 设置模拟位置: provider=${location.provider}, lat=${location.latitude}, lng=${location.longitude}")
            setTestProviderLocationMethod?.invoke(service, location.provider, location)
            Log.d(TAG, "✅ Shizuku模拟位置设置成功: ${location.provider}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Shizuku模拟位置设置失败: ${e.javaClass.simpleName} - ${e.message}", e)
        }
    }

    fun setProviderEnabled(provider: String, enabled: Boolean) {
        val service = getLocationService()
        if (service == null) {
            Log.e(TAG, "❌ LocationService不可用，无法设置提供者状态")
            return
        }

        try {
            Log.d(TAG, "🔧 设置Shizuku提供者状态: $provider = $enabled")
            setTestProviderEnabledMethod?.invoke(service, provider, enabled)
            Log.d(TAG, "✅ Shizuku提供者状态设置成功: $provider = $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Shizuku提供者状态设置失败: $provider - ${e.javaClass.simpleName} - ${e.message}", e)
        }
    }

    fun addTestProvider(provider: String) {
        val service = getLocationService()
        if (service == null) {
            Log.e(TAG, "❌ LocationService不可用，无法添加测试提供者")
            return
        }

        try {
            Log.d(TAG, "🔧 添加Shizuku测试提供者: $provider")
            addTestProviderMethod?.invoke(
                service,
                provider,
                false,  // requiresNetwork
                false,  // requiresSatellite
                false,  // requiresCell
                false,  // hasMonetaryCost
                true,   // supportsAltitude
                true,   // supportsSpeed
                true,   // supportsBearing
                1,      // powerRequirement
                1       // accuracy
            )
            Log.d(TAG, "✅ Shizuku测试提供者添加成功: $provider")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Shizuku测试提供者添加失败: $provider - ${e.javaClass.simpleName} - ${e.message}", e)
            throw e
        }
    }

    fun removeTestProvider(provider: String) {
        val service = getLocationService()
        if (service == null) {
            Log.e(TAG, "❌ LocationService不可用，无法移除测试提供者")
            return
        }

        try {
            Log.d(TAG, "🔧 移除Shizuku测试提供者: $provider")
            removeTestProviderMethod?.invoke(service, provider)
            Log.d(TAG, "✅ Shizuku测试提供者移除成功: $provider")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Shizuku测试提供者移除失败: $provider - ${e.javaClass.simpleName} - ${e.message}", e)
        }
    }

    fun disableRealProvider(provider: String) {
        val service = getLocationService()
        if (service == null) {
            Log.e(TAG, "❌ LocationService不可用，无法禁用真实提供者")
            return
        }

        try {
            Log.d(TAG, "🔧 尝试禁用真实位置提供者: $provider")
            // 注意：这是一个实验性功能，可能在某些Android版本上不工作
            // 我们尝试通过设置提供者为禁用状态来实现
            val setProviderEnabledMethod = service.javaClass.getMethod(
                "setProviderEnabled",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            setProviderEnabledMethod.invoke(service, provider, false)
            Log.d(TAG, "✅ 真实位置提供者禁用成功: $provider")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 无法禁用真实位置提供者 $provider: ${e.javaClass.simpleName} - ${e.message}")
            // 不抛出异常，因为这不是致命错误
        }
    }

    fun enableRealProvider(provider: String) {
        val service = getLocationService()
        if (service == null) {
            Log.e(TAG, "❌ LocationService不可用，无法启用真实提供者")
            return
        }

        try {
            Log.d(TAG, "🔧 尝试重新启用真实位置提供者: $provider")
            val setProviderEnabledMethod = service.javaClass.getMethod(
                "setProviderEnabled",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            setProviderEnabledMethod.invoke(service, provider, true)
            Log.d(TAG, "✅ 真实位置提供者重新启用成功: $provider")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 无法重新启用真实位置提供者 $provider: ${e.javaClass.simpleName} - ${e.message}")
            // 不抛出异常，因为这不是致命错误
        }
    }
}