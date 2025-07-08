package com.example.locationsimulator.util

import android.content.Context
import android.content.pm.PackageManager
import android.location.ILocationManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object ShizukuManager {

    private const val TAG = "ShizukuManager"

    private var locationService: ILocationManager? = null

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

    private fun getLocationService(): ILocationManager? {
        if (locationService != null) {
            return locationService
        }

        if (!isShizukuAvailable) {
            Log.e(TAG, "Shizuku is not available.")
            return null
        }

        try {
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.LOCATION_SERVICE))
            locationService = ILocationManager.Stub.asInterface(binder)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get LocationManagerService.", e)
        }

        return locationService
    }

    fun setMockLocation(location: Location) {
        val service = getLocationService() ?: return

        try {
            service.setTestProviderLocation(location.provider, location)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set mock location.", e)
        }
    }

    fun setProviderEnabled(provider: String, enabled: Boolean) {
        val service = getLocationService() ?: return

        try {
            service.setTestProviderEnabled(provider, enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set provider enabled state.", e)
        }
    }

    fun addTestProvider(provider: String) {
        val service = getLocationService() ?: return

        try {
            service.addTestProvider(
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
            service.removeTestProvider(provider)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove test provider.", e)
        }
    }
}