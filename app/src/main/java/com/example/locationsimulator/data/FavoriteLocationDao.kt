package com.example.locationsimulator.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 收藏位置数据访问对象
 */
@Dao
interface FavoriteLocationDao {
    
    /**
     * 获取所有收藏位置，按使用频率排序
     */
    @Query("SELECT * FROM favorite_locations ORDER BY useCount DESC, lastUsedTime DESC")
    fun getAllFavorites(): Flow<List<FavoriteLocation>>
    
    /**
     * 按时间排序获取收藏位置
     */
    @Query("SELECT * FROM favorite_locations ORDER BY createdTime DESC")
    fun getAllFavoritesByTime(): Flow<List<FavoriteLocation>>
    
    /**
     * 根据ID获取收藏位置
     */
    @Query("SELECT * FROM favorite_locations WHERE id = :id")
    suspend fun getFavoriteById(id: Long): FavoriteLocation?
    
    /**
     * 搜索收藏位置
     */
    @Query("SELECT * FROM favorite_locations WHERE name LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%' ORDER BY useCount DESC")
    fun searchFavorites(query: String): Flow<List<FavoriteLocation>>
    
    /**
     * 插入新的收藏位置
     */
    @Insert
    suspend fun insertFavorite(favorite: FavoriteLocation): Long
    
    /**
     * 更新收藏位置
     */
    @Update
    suspend fun updateFavorite(favorite: FavoriteLocation)
    
    /**
     * 删除收藏位置
     */
    @Delete
    suspend fun deleteFavorite(favorite: FavoriteLocation)
    
    /**
     * 根据ID删除收藏位置
     */
    @Query("DELETE FROM favorite_locations WHERE id = :id")
    suspend fun deleteFavoriteById(id: Long)
    
    /**
     * 增加使用次数
     */
    @Query("UPDATE favorite_locations SET useCount = useCount + 1, lastUsedTime = :currentTime WHERE id = :id")
    suspend fun incrementUseCount(id: Long, currentTime: Long = System.currentTimeMillis())
    
    /**
     * 获取收藏总数
     */
    @Query("SELECT COUNT(*) FROM favorite_locations")
    suspend fun getFavoriteCount(): Int
    
    /**
     * 清空所有收藏
     */
    @Query("DELETE FROM favorite_locations")
    suspend fun clearAllFavorites()
}
