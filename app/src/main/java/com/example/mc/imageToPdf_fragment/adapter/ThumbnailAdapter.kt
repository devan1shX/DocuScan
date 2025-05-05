package com.example.mc.imageToPdf_fragment.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mc.databinding.ListItemThumbnailBinding

interface ThumbnailRemoveListener {
    fun onRemoveThumbnailClicked(imageUri: Uri)
}

class ThumbnailAdapter(private val listener: ThumbnailRemoveListener) :
    ListAdapter<Uri, ThumbnailAdapter.ThumbnailViewHolder>(UriDiffCallback()) {

    inner class ThumbnailViewHolder(private val binding: ListItemThumbnailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.buttonRemoveThumbnail.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onRemoveThumbnailClicked(getItem(position))
                }
            }
        }

        fun bind(imageUri: Uri) {
            Glide.with(binding.imageViewThumbnail.context)
                .load(imageUri)
                .centerCrop()
                .into(binding.imageViewThumbnail)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val binding = ListItemThumbnailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ThumbnailViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class UriDiffCallback : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            return oldItem == newItem
        }
    }
}
