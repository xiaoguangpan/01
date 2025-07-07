package com.example.locationsimulator.data

import com.baidu.mapapi.model.LatLng

/**
 * 地点建议项数据类
 */
data class SuggestionItem(
    val name: String,           // 地点名称
    val location: LatLng?,      // 坐标（可能为空）
    val uid: String?,           // POI的UID
    val city: String?,          // 城市
    val district: String?       // 区域
)
