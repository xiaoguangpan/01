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

    private const val TAG = "ShizukuManager"

    private var locationService: Any? = null
    private var locationServiceClass: Class<*>? = null
    private var setTestProviderLocationMethod: Method? = null
    private var setTestProviderEnabledMethod: Method? = null
    private var addTestProviderMethod: Method? = null
    private var removeTestProviderMethod: Method? = null

    private val isShizukuAvailable: Boolean
        get() = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }

    fun checkPermissions(context: Context): Boolean {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            Log.e(TAG, "Shizuku version is too old.")
            return false
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(0)
            return false
        }

        return true
    }

    private fun getLocationService(): Any? {
        if (locationService != null) {
            return locationService
        }

        if (!isShizukuAvailable) {
            Log.e(TAG, "Shizuku is not available.")
            return null
        }

        try {
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.LOCATION_SERVICE))

            // 使用反射获取 ILocationManager.Stub.asInterface 方法
            val iLocationManagerClass = Class.forName("android.location.ILocationManager")
            val stubClass = Class.forName("android.location.ILocationManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)

            locationService = asInterfaceMethod.invoke(null, binder)
            locationServiceClass = iLocationManagerClass

            // 缓存反射方法
            setTestProviderLocationMethod = locationServiceClass?.getMethod(
                "setTestProviderLocation",
                String::class.java,
                Location::class.java
            )
            setTestProviderEnabledMethod = locationServiceClass?.getMethod(
                "setTestProviderEnabled",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            addTestProviderMethod = locationServiceClass?.getMethod(
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
            removeTestProviderMethod = locationServiceClass?.getMethod(
                "removeTestProvider",
                String::class.java
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get LocationManagerService.", e)
        }

        return locationService
    }

    fun setMockLocation(location: Location) {
        val service = getLocationService() ?: return

        try {
            setTestProviderLocationMethod?.invoke(service, location.provider, location)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set mock location.", e)
        }
    }

    fun setProviderEnabled(provider: String, enabled: Boolean) {
        val service = getLocationService() ?: return

        try {
            setTestProviderEnabledMethod?.invoke(service, provider, enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set provider enabled state.", e)
        }
    }

    fun addTestProvider(provider: String) {
        val service = getLocationService() ?: return

        try {
            addTestProviderMethod?.invoke(
                service,
                provider,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                1,
                1
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add test provider.", e)
        }
    }

    fun removeTestProvider(provider: String) {
        val service = getLocationService() ?: return

        try {
            removeTestProviderMethod?.invoke(service, provider)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove test provider.", e)
        }
    }
}