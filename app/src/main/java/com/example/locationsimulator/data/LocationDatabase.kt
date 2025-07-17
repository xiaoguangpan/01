package com.example.locationsimulator.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 位置数据库
 */
@Database(
    entities = [FavoriteLocation::class],
    version = 1,
    exportSchema = false
)
abstract class LocationDatabase : RoomDatabase() {
    
    abstract fun favoriteLocationDao(): FavoriteLocationDao
    
    companion object {
        @Volatile
        private var INSTANCE: LocationDatabase? = null
        
        fun getDatabase(context: Context): LocationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocationDatabase::class.java,
                    "location_database"
                )
                .fallbackToDestructiveMigration() // 简化版本升级处理
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
