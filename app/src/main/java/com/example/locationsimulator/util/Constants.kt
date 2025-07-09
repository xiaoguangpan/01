package com.example.locationsimulator.util

import androidx.compose.ui.graphics.Color

/**
 * 应用常量定义
 */
object Constants {
    
    // 应用信息
    const val APP_NAME = "Location Simulator"
    const val APP_NAME_DEBUG = "Location Simulator Debug"
    const val PACKAGE_NAME = "com.example.locationsimulator"
    
    // 颜色主题
    object Colors {
        val Background = Color(0xFF1F2937)
        val Surface = Color(0xFF374151)
        val Primary = Color(0xFF3B82F6)
        val Secondary = Color(0xFF6B7280)
        val Success = Color(0xFF10B981)
        val Warning = Color(0xFFFB8C00)
        val Error = Color(0xFFE53935)
        val OnSurface = Color.White
        val OnSurfaceVariant = Color(0xFFD1D5DB)
    }
    
    // 默认坐标（北京天安门）
    object DefaultLocation {
        const val LATITUDE = 39.915
        const val LONGITUDE = 116.404
        const val ZOOM_LEVEL = 15f
    }
    
    // 时间常量
    object Timing {
        const val SDK_INIT_DELAY = 2000L // SDK初始化延迟
        const val LOCATION_UPDATE_INTERVAL = 1000L // 位置更新间隔（毫秒）
        const val DEBUG_PANEL_TAP_COUNT = 5 // 调试面板激活点击次数
    }
    
    // 权限请求码
    object RequestCodes {
        const val LOCATION_PERMISSION = 1001
        const val SHIZUKU_PERMISSION = 0
    }
    
    // 百度地图相关
    object BaiduMap {
        const val COORD_TYPE = "bd09ll"
        const val DEVELOPER_CONSOLE_URL = "https://lbsyun.baidu.com/apiconsole/key"
        const val DOCUMENTATION_URL = "https://lbsyun.baidu.com/faq/api?title=androidsdk/guide/create-project/ak"
    }
    
    // UI尺寸
    object Dimensions {
        const val BUTTON_HEIGHT = 56
        const val CORNER_RADIUS = 16
        const val PADDING_SMALL = 8
        const val PADDING_MEDIUM = 16
        const val PADDING_LARGE = 24
    }
    
    // 日志标签
    object LogTags {
        const val MAIN_ACTIVITY = "MainActivity"
        const val MAIN_APPLICATION = "MainApplication"
        const val MAIN_VIEW_MODEL = "MainViewModel"
        const val MOCK_LOCATION_MANAGER = "MockLocationManager"
        const val SHIZUKU_MANAGER = "ShizukuManager"
        const val SHA1_UTIL = "SHA1Util"
        const val COORDINATE_CONVERTER = "CoordinateConverter"
    }
}
