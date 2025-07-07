package com.example.locationsimulator

import android.app.Application
// 暂时注释掉百度地图SDK初始化
// import com.baidu.mapapi.SDKInitializer

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 暂时注释掉百度地图SDK初始化
        // Initialize the Baidu Map SDK
        // SDKInitializer.setAgreePrivacy(this, true)
        // SDKInitializer.initialize(this)
    }
}
