package com.example.mc.home_fragment.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mc.R
import com.example.mc.databinding.ListItemDocumentBinding
import com.example.mc.home_fragment.db.ScannedDocument

class DocumentAdapter(
    private val onItemClicked: (ScannedDocument) -> Unit,
    private val onOptionsMenuClicked: (ScannedDocument, View) -> Unit
) : ListAdapter<ScannedDocument, DocumentAdapter.DocumentViewHolder>(DocumentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val binding = ListItemDocumentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DocumentViewHolder(binding, onItemClicked, onOptionsMenuClicked)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DocumentViewHolder(
        private val binding: ListItemDocumentBinding,
        private val onItemClicked: (ScannedDocument) -> Unit,
        private val onOptionsMenuClicked: (ScannedDocument, View) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClicked(getItem(position))
                }
            }
            binding.buttonOptions.setOnClickListener { view ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onOptionsMenuClicked(getItem(position), view)
                }
            }
        }

        fun bind(document: ScannedDocument) {
            binding.textViewTitle.text = document.title
            binding.textViewTimestamp.text = document.getFormattedTimestamp()

            if (document.pageCount > 0) {
                binding.textViewPageCount.visibility = View.VISIBLE
                binding.textViewPageCount.text = itemView.context.resources.getQuantityString(
                    R.plurals.page_count_format, document.pageCount, document.pageCount
                )
            } else {
                binding.textViewPageCount.visibility = View.GONE
            }
        }
    }

    class DocumentDiffCallback : DiffUtil.ItemCallback<ScannedDocument>() {
        override fun areItemsTheSame(oldItem: ScannedDocument, newItem: ScannedDocument): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ScannedDocument, newItem: ScannedDocument): Boolean {
            return oldItem == newItem
        }
    }
}
