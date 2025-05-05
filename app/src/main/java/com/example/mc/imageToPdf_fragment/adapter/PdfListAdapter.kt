package com.example.mc.imageToPdf_fragment.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mc.databinding.ListItemPdfBinding
import com.example.mc.imageToPdf_fragment.model.PdfFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface PdfItemClickListener {
    fun onPdfItemClicked(pdfFile: PdfFile)
}

interface PdfOptionsClickListener {
    fun onPdfOptionsClicked(pdfFile: PdfFile, anchorView: View)
}

class PdfListAdapter(
    private val itemClickListener: PdfItemClickListener,
    private val optionsClickListener: PdfOptionsClickListener
) : ListAdapter<PdfFile, PdfListAdapter.PdfViewHolder>(PdfDiffCallback()) {

    private val dateTimeFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    inner class PdfViewHolder(private val binding: ListItemPdfBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    itemClickListener.onPdfItemClicked(getItem(position))
                }
            }

            binding.buttonPdfOptions.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    optionsClickListener.onPdfOptionsClicked(getItem(position), binding.buttonPdfOptions)
                }
            }
        }

        fun bind(pdfFile: PdfFile) {
            binding.textViewPdfName.text = pdfFile.name

            if (pdfFile.creationTimestamp > 0) {
                binding.textViewPdfDateTime.text = dateTimeFormatter.format(Date(pdfFile.creationTimestamp))
                binding.textViewPdfDateTime.visibility = View.VISIBLE
            } else {
                binding.textViewPdfDateTime.visibility = View.GONE
            }

            when {
                pdfFile.pageCount > 1 -> {
                    binding.textViewPdfPageCount.text = "${pdfFile.pageCount} pages"
                    binding.textViewPdfPageCount.visibility = View.VISIBLE
                }
                pdfFile.pageCount == 1 -> {
                    binding.textViewPdfPageCount.text = "1 page"
                    binding.textViewPdfPageCount.visibility = View.VISIBLE
                }
                else -> {
                    binding.textViewPdfPageCount.visibility = View.GONE
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfViewHolder {
        val binding = ListItemPdfBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PdfViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PdfViewHolder, position: Int) {
        val currentPdf = getItem(position)
        holder.bind(currentPdf)
    }

    class PdfDiffCallback : DiffUtil.ItemCallback<PdfFile>() {
        override fun areItemsTheSame(oldItem: PdfFile, newItem: PdfFile): Boolean {
            return oldItem.uri == newItem.uri
        }

        override fun areContentsTheSame(oldItem: PdfFile, newItem: PdfFile): Boolean {
            return oldItem.name == newItem.name &&
                    oldItem.creationTimestamp == newItem.creationTimestamp &&
                    oldItem.pageCount == newItem.pageCount
        }
    }
}
