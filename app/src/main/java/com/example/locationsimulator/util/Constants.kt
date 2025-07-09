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
    
    // 颜色主题 - 按照HTML原型设计
    object Colors {
        // 背景渐变色（使用中间色作为主背景）
        val Background = Color(0xFF1e40af)

        // 半透明白色背景 rgba(255,255,255,0.1)
        val Surface = Color(0x1AFFFFFF)
        val SurfaceVariant = Color(0x33FFFFFF) // rgba(255,255,255,0.2)

        // 主要按钮颜色 #2196F3
        val Primary = Color(0xFF2196F3)
        val PrimaryHover = Color(0xFF1976D2)

        // 错误/停止按钮颜色 #F44336
        val Error = Color(0xFFF44336)
        val ErrorHover = Color(0xFFD32F2F)

        // 成功状态颜色 #4CAF50
        val Success = Color(0xFF4CAF50)

        // 收藏按钮颜色 #E91E63
        val Favorite = Color(0xFFE91E63)

        // 警告颜色 #FFC107
        val Warning = Color(0xFFFFC107)

        // 文本颜色
        val OnSurface = Color.White
        val OnSurfaceVariant = Color(0xCCFFFFFF) // rgba(255,255,255,0.8)
        val OnSurfaceDisabled = Color(0x80FFFFFF) // rgba(255,255,255,0.5)

        // 禁用状态
        val Disabled = Color(0x33FFFFFF) // rgba(255,255,255,0.2)
        val OnDisabled = Color(0x80FFFFFF) // rgba(255,255,255,0.5)
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
    
    // UI尺寸 - 按照HTML原型设计
    object Dimensions {
        const val BUTTON_HEIGHT = 56

        // 圆角半径
        const val CORNER_RADIUS_LARGE = 16 // 主要容器
        const val CORNER_RADIUS_MEDIUM = 12 // 卡片容器
        const val CORNER_RADIUS_SMALL = 8 // 按钮和输入框
        const val CORNER_RADIUS_TINY = 6 // 小按钮

        // 间距
        const val PADDING_TINY = 4
        const val PADDING_SMALL = 8
        const val PADDING_MEDIUM = 12
        const val PADDING_LARGE = 16
        const val PADDING_XLARGE = 24

        // 按钮尺寸
        const val ICON_BUTTON_SIZE = 40
        const val SMALL_BUTTON_HEIGHT = 32
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
