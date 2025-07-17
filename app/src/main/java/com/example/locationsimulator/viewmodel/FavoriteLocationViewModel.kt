package com.example.locationsimulator.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import com.example.locationsimulator.data.FavoriteLocation
import com.example.locationsimulator.repository.FavoriteLocationRepository

/**
 * 收藏位置ViewModel
 */
class FavoriteLocationViewModel(private val repository: FavoriteLocationRepository) : ViewModel() {
    
    private val _sortMode = MutableLiveData<SortMode>(SortMode.FREQUENCY)
    
    val allFavorites: LiveData<List<FavoriteLocation>> = _sortMode.switchMap { sortMode ->
        when (sortMode) {
            SortMode.FREQUENCY -> repository.getAllFavorites().asLiveData()
            SortMode.TIME -> repository.getAllFavoritesByTime().asLiveData()
        }
    }
    
    fun sortByFrequency() {
        _sortMode.value = SortMode.FREQUENCY
    }
    
    fun sortByTime() {
        _sortMode.value = SortMode.TIME
    }
    
    enum class SortMode {
        FREQUENCY, TIME
    }
}

/**
 * ViewModel工厂
 */
class FavoriteLocationViewModelFactory(
    private val repository: FavoriteLocationRepository
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FavoriteLocationViewModel::class.java)) {
            return FavoriteLocationViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
