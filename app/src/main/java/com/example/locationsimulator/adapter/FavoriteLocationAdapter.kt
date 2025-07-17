package com.example.locationsimulator.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.locationsimulator.R
import com.example.locationsimulator.data.FavoriteLocation
import java.text.SimpleDateFormat
import java.util.*

/**
 * 收藏位置列表适配器
 */
class FavoriteLocationAdapter(
    private val onItemClick: (FavoriteLocation) -> Unit,
    private val onEditClick: (FavoriteLocation) -> Unit,
    private val onDeleteClick: (FavoriteLocation) -> Unit
) : ListAdapter<FavoriteLocation, FavoriteLocationAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_location, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.textFavoriteName)
        private val textAddress: TextView = itemView.findViewById(R.id.textFavoriteAddress)
        private val textCoordinates: TextView = itemView.findViewById(R.id.textFavoriteCoordinates)
        private val textInfo: TextView = itemView.findViewById(R.id.textFavoriteInfo)
        private val buttonEdit: ImageButton = itemView.findViewById(R.id.buttonEditFavorite)
        private val buttonDelete: ImageButton = itemView.findViewById(R.id.buttonDeleteFavorite)
        
        fun bind(favorite: FavoriteLocation) {
            textName.text = favorite.name
            textAddress.text = favorite.address
            textCoordinates.text = "${favorite.latitude}, ${favorite.longitude}"
            
            // 显示使用次数和创建时间
            val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            val createdTime = dateFormat.format(Date(favorite.createdTime))
            textInfo.text = "使用${favorite.useCount}次 • $createdTime"
            
            // 设置点击事件
            itemView.setOnClickListener {
                onItemClick(favorite)
            }
            
            buttonEdit.setOnClickListener {
                onEditClick(favorite)
            }
            
            buttonDelete.setOnClickListener {
                onDeleteClick(favorite)
            }
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<FavoriteLocation>() {
        override fun areItemsTheSame(oldItem: FavoriteLocation, newItem: FavoriteLocation): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: FavoriteLocation, newItem: FavoriteLocation): Boolean {
            return oldItem == newItem
        }
    }
}
