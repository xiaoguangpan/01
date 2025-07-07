package com.example.locationsimulator

import android.app.Application
import com.baidu.mapapi.SDKInitializer

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize the Baidu Map SDK
        SDKInitializer.setAgreePrivacy(this, true)
        SDKInitializer.initialize(this)
    }
}
