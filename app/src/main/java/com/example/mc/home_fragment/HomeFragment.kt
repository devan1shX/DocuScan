package com.example.mc.home_fragment

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.mc.home_fragment.adapter.DocumentAdapter
import com.example.mc.R
import com.example.mc.databinding.FragmentHomeBinding
import com.example.mc.home_fragment.db.ScannedDocument
import com.example.mc.home_fragment.viewModel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: DocumentAdapter
    private var fullDocumentList: List<ScannedDocument> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearchView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = DocumentAdapter(
            onItemClicked = { document -> openPdf(document.pdfPath) },
            onOptionsMenuClicked = { document, anchorView -> showPopupMenu(document, anchorView) }
        )
        binding.recyclerViewDocuments.adapter = adapter
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                binding.searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })
        binding.searchView.setOnCloseListener {
            binding.searchView.clearFocus()
            true
        }
    }

    private fun observeViewModel() {
        mainViewModel.allDocuments.observe(viewLifecycleOwner) { documents ->
            fullDocumentList = documents ?: emptyList()
            filterList(binding.searchView.query?.toString())
        }
    }

    private fun filterList(query: String?) {
        val filteredList = if (query.isNullOrBlank()) {
            fullDocumentList
        } else {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            fullDocumentList.filter { document ->
                document.title.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
            }
        }
        adapter.submitList(filteredList)
        updateEmptyState(filteredList.isEmpty(), query.isNullOrBlank())
    }

    private fun updateEmptyState(isListEmpty: Boolean, isQueryEmpty: Boolean) {
        if (isListEmpty) {
            binding.recyclerViewDocuments.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.textViewEmptyState.text = getString(
                if (isQueryEmpty) R.string.no_documents_available
                else R.string.no_search_results
            )
        } else {
            binding.recyclerViewDocuments.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
        }
    }

    private fun openPdf(filePath: String) {
        val currentContext = context ?: return
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(currentContext, R.string.pdf_not_found, Toast.LENGTH_SHORT).show()
            return
        }
        val uri: Uri? = try {
            FileProvider.getUriForFile(
                currentContext,
                "${currentContext.applicationContext.packageName}.provider",
                file
            )
        } catch (e: IllegalArgumentException) {
            Toast.makeText(currentContext, R.string.error_sharing_file, Toast.LENGTH_SHORT).show()
            null
        }

        uri?.let { fileUri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                val messageRes = when (e) {
                    is ActivityNotFoundException -> R.string.no_pdf_viewer
                    is SecurityException -> R.string.permission_denied_opening
                    else -> R.string.could_not_open_pdf
                }
                Toast.makeText(currentContext, messageRes, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPopupMenu(document: ScannedDocument, anchorView: View) {
        val currentContext = context ?: return
        val popup = PopupMenu(currentContext, anchorView)
        try {
            popup.menuInflater.inflate(R.menu.document_options_menu, popup.menu)
        } catch (e: Exception) {
            Toast.makeText(currentContext, R.string.error_showing_options, Toast.LENGTH_SHORT).show()
            return
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_open_doc -> openPdf(document.pdfPath)
                R.id.action_rename_doc -> showRenameDialog(document)
                R.id.action_delete_doc -> showDeleteConfirmationDialog(document)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    private fun showRenameDialog(document: ScannedDocument) {
        val currentContext = context ?: return
        val editText = EditText(currentContext).apply {
            setText(document.title)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSelection(document.title.length)
        }
        val container = FrameLayout(currentContext).apply {
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
            addView(editText)
        }
        AlertDialog.Builder(currentContext)
            .setTitle(R.string.rename_document_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                handleRename(document, editText.text.toString().trim())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun handleRename(document: ScannedDocument, newTitle: String) {
        val currentContext = context ?: return
        when {
            newTitle.isEmpty() -> {
                Toast.makeText(currentContext, R.string.title_cannot_be_empty, Toast.LENGTH_SHORT).show()
            }
            newTitle != document.title -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = mainViewModel.renameDocument(document, newTitle)
                    if (isAdded) {
                        val messageRes = if (success) R.string.rename_successful else R.string.rename_failed
                        Toast.makeText(currentContext, messageRes, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(document: ScannedDocument) {
        val currentContext = context ?: return
        AlertDialog.Builder(currentContext)
            .setTitle(R.string.confirm_deletion_title)
            .setMessage(getString(R.string.confirm_deletion_message, document.title))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(R.string.delete) { _, _ ->
                mainViewModel.delete(document)
                if (isAdded) Toast.makeText(currentContext, R.string.document_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewDocuments.adapter = null
        _binding = null
    }
}
