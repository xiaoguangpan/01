package com.example.locationsimulator.repository

import android.content.Context
import com.example.locationsimulator.data.FavoriteLocation
import com.example.locationsimulator.data.FavoriteLocationDao
import com.example.locationsimulator.data.LocationDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 收藏位置仓库
 */
class FavoriteLocationRepository(context: Context) {
    
    private val favoriteLocationDao: FavoriteLocationDao = 
        LocationDatabase.getDatabase(context).favoriteLocationDao()
    
    /**
     * 获取所有收藏位置（按使用频率排序）
     */
    fun getAllFavorites(): Flow<List<FavoriteLocation>> = favoriteLocationDao.getAllFavorites()
    
    /**
     * 获取所有收藏位置（按时间排序）
     */
    fun getAllFavoritesByTime(): Flow<List<FavoriteLocation>> = favoriteLocationDao.getAllFavoritesByTime()
    
    /**
     * 搜索收藏位置
     */
    fun searchFavorites(query: String): Flow<List<FavoriteLocation>> = favoriteLocationDao.searchFavorites(query)
    
    /**
     * 添加收藏位置
     */
    suspend fun addFavorite(
        name: String,
        latitude: Double,
        longitude: Double,
        address: String
    ): Long {
        val favorite = FavoriteLocation(
            name = name,
            latitude = latitude,
            longitude = longitude,
            address = address
        )
        return favoriteLocationDao.insertFavorite(favorite)
    }
    
    /**
     * 更新收藏位置名称
     */
    suspend fun updateFavoriteName(id: Long, newName: String) {
        val favorite = favoriteLocationDao.getFavoriteById(id)
        favorite?.let {
            favoriteLocationDao.updateFavorite(it.copy(name = newName))
        }
    }
    
    /**
     * 删除收藏位置
     */
    suspend fun deleteFavorite(favorite: FavoriteLocation) {
        favoriteLocationDao.deleteFavorite(favorite)
    }
    
    /**
     * 根据ID删除收藏位置
     */
    suspend fun deleteFavoriteById(id: Long) {
        favoriteLocationDao.deleteFavoriteById(id)
    }
    
    /**
     * 使用收藏位置（增加使用次数）
     */
    suspend fun useFavorite(id: Long) {
        favoriteLocationDao.incrementUseCount(id)
    }
    
    /**
     * 获取收藏总数
     */
    suspend fun getFavoriteCount(): Int = favoriteLocationDao.getFavoriteCount()
    
    /**
     * 清空所有收藏
     */
    suspend fun clearAllFavorites() {
        favoriteLocationDao.clearAllFavorites()
    }
}
