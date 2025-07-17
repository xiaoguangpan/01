package com.example.locationsimulator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 收藏位置数据实体
 */
@Entity(tableName = "favorite_locations")
data class FavoriteLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 收藏名称（用户自定义） */
    val name: String,
    
    /** 纬度 */
    val latitude: Double,
    
    /** 经度 */
    val longitude: Double,
    
    /** 地址描述 */
    val address: String,
    
    /** 收藏时间（时间戳） */
    val createdTime: Long = System.currentTimeMillis(),
    
    /** 使用次数统计 */
    val useCount: Int = 0,
    
    /** 最后使用时间 */
    val lastUsedTime: Long = 0
)
