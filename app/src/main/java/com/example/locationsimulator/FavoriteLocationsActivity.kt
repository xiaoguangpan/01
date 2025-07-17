package com.example.locationsimulator

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.locationsimulator.adapter.FavoriteLocationAdapter
import com.example.locationsimulator.data.FavoriteLocation
import com.example.locationsimulator.repository.FavoriteLocationRepository
import com.example.locationsimulator.viewmodel.FavoriteLocationViewModel
import com.example.locationsimulator.viewmodel.FavoriteLocationViewModelFactory
import kotlinx.coroutines.launch

/**
 * 收藏位置列表界面
 */
class FavoriteLocationsActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FavoriteLocationAdapter
    private lateinit var repository: FavoriteLocationRepository
    
    private val viewModel: FavoriteLocationViewModel by viewModels {
        FavoriteLocationViewModelFactory(repository)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorite_locations)
        
        // 设置标题栏
        supportActionBar?.apply {
            title = "收藏位置"
            setDisplayHomeAsUpEnabled(true)
        }
        
        // 初始化仓库
        repository = FavoriteLocationRepository(this)
        
        // 初始化RecyclerView
        setupRecyclerView()
        
        // 观察数据变化
        observeData()
    }
    
    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewFavorites)
        adapter = FavoriteLocationAdapter(
            onItemClick = { favorite ->
                // 点击收藏项，启动模拟定位
                startMockLocation(favorite)
            },
            onEditClick = { favorite ->
                // 编辑收藏名称
                showEditDialog(favorite)
            },
            onDeleteClick = { favorite ->
                // 删除收藏
                showDeleteDialog(favorite)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun observeData() {
        viewModel.allFavorites.observe(this) { favorites ->
            adapter.submitList(favorites)
        }
    }
    
    private fun startMockLocation(favorite: FavoriteLocation) {
        // 增加使用次数
        lifecycleScope.launch {
            repository.useFavorite(favorite.id)
        }
        
        // 返回主界面并启动模拟定位
        val intent = Intent().apply {
            putExtra("latitude", favorite.latitude)
            putExtra("longitude", favorite.longitude)
            putExtra("address", favorite.address)
            putExtra("start_simulation", true)
        }
        setResult(RESULT_OK, intent)
        finish()
    }
    
    private fun showEditDialog(favorite: FavoriteLocation) {
        val editText = EditText(this).apply {
            setText(favorite.name)
            selectAll()
        }
        
        AlertDialog.Builder(this)
            .setTitle("编辑收藏名称")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch {
                        repository.updateFavoriteName(favorite.id, newName)
                        Toast.makeText(this@FavoriteLocationsActivity, "已更新", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteDialog(favorite: FavoriteLocation) {
        AlertDialog.Builder(this)
            .setTitle("删除收藏")
            .setMessage("确定要删除「${favorite.name}」吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteFavorite(favorite)
                    Toast.makeText(this@FavoriteLocationsActivity, "已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_favorites, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_sort_by_frequency -> {
                // 按使用频率排序
                viewModel.sortByFrequency()
                true
            }
            R.id.action_sort_by_time -> {
                // 按时间排序
                viewModel.sortByTime()
                true
            }
            R.id.action_clear_all -> {
                // 清空所有收藏
                showClearAllDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("清空收藏")
            .setMessage("确定要清空所有收藏位置吗？此操作不可恢复。")
            .setPositiveButton("清空") { _, _ ->
                lifecycleScope.launch {
                    repository.clearAllFavorites()
                    Toast.makeText(this@FavoriteLocationsActivity, "已清空所有收藏", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
