package com.example.mc.imageToPdf_fragment

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mc.R
import com.example.mc.databinding.FragmentImageToPdfBinding
import com.example.mc.imageToPdf_fragment.adapter.PdfItemClickListener
import com.example.mc.imageToPdf_fragment.model.PdfFile
import com.example.mc.imageToPdf_fragment.adapter.PdfListAdapter
import com.example.mc.imageToPdf_fragment.adapter.PdfOptionsClickListener
import com.example.mc.imageToPdf_fragment.adapter.ThumbnailAdapter
import com.example.mc.imageToPdf_fragment.adapter.ThumbnailRemoveListener
import com.example.mc.imageToPdf_fragment.viewModel.ImageToPdfViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ImageToPdfFragment : Fragment(), PdfItemClickListener, ThumbnailRemoveListener,
    PdfOptionsClickListener {

    private var _binding: FragmentImageToPdfBinding? = null
    private val binding get() = _binding!!

    private val imageToPdfViewModel: ImageToPdfViewModel by activityViewModels()

    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var textRecognizer: TextRecognizer
    private lateinit var pdfAdapter: PdfListAdapter
    private lateinit var thumbnailAdapter: ThumbnailAdapter

    private var nextImageUri: Uri? = null
    private var fullPdfList: List<PdfFile> = emptyList()

    private val fileProviderAuthority = "com.example.mc.provider"
    private val pdfInternalSubDir = "MyAppScans"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeLaunchers()
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        prepareNextImageUri()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageToPdfBinding.inflate(inflater, container, false)
        setupRecyclerViews()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSearchViewPdf()
        setupClickListeners()
        updateThumbnailAdapter()
        loadSavedPdfs()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        textRecognizer.close()
        binding.recyclerViewPdfs.adapter = null
        binding.recyclerViewThumbnails.adapter = null
        _binding = null
    }

    private fun initializeLaunchers() {
        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                nextImageUri?.let {
                    imageToPdfViewModel.addUri(it)
                    updateThumbnailAdapter()
                    prepareNextImageUri()
                } ?: prepareNextImageUri()
            }
        }
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) launchCamera() else showToast("Camera permission required.")
        }
    }

    private fun setupRecyclerViews() {
        pdfAdapter = PdfListAdapter(this, this)
        binding.recyclerViewPdfs.adapter = pdfAdapter
        binding.recyclerViewPdfs.layoutManager = LinearLayoutManager(requireContext())

        thumbnailAdapter = ThumbnailAdapter(this)
        binding.recyclerViewThumbnails.adapter = thumbnailAdapter
        binding.recyclerViewThumbnails.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
    }

    private fun setupSearchViewPdf() {
        binding.searchViewPdf.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                binding.searchViewPdf.clearFocus()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                filterPdfList(newText)
                return true
            }
        })
        binding.searchViewPdf.setOnCloseListener {
            binding.searchViewPdf.clearFocus()
            true
        }
    }

    private fun setupClickListeners() {
        binding.buttonScan.setOnClickListener { requestCameraPermission() }
        binding.buttonCreatePdf.setOnClickListener {
            if (imageToPdfViewModel.hasUris()) processImagesAndCreatePdf() else showToast("Add at least one page.")
        }
    }

    private fun updateEmptyPdfViewVisibility(isListEmpty: Boolean, isQueryEmpty: Boolean) {
        if (isListEmpty) {
            binding.recyclerViewPdfs.visibility = View.GONE
            binding.layoutEmptyStatePdf.visibility = View.VISIBLE
            binding.textViewEmptyListPdf.text = if (isQueryEmpty) {
                "No saved PDFs here yet."
            } else {
                "No saved PDFs match search."
            }
        } else {
            binding.recyclerViewPdfs.visibility = View.VISIBLE
            binding.layoutEmptyStatePdf.visibility = View.GONE
        }
    }

    private fun updateThumbnailAdapter() {
        val currentUris = imageToPdfViewModel.capturedImageUris
        thumbnailAdapter.submitList(currentUris)
        val hasThumbnails = currentUris.isNotEmpty()
        binding.cardViewThumbnails.visibility = if (hasThumbnails) View.VISIBLE else View.GONE
        binding.buttonCreatePdf.isEnabled = hasThumbnails
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonScan.isEnabled = !isLoading
        binding.buttonCreatePdf.isEnabled = !isLoading && imageToPdfViewModel.hasUris()
        binding.recyclerViewPdfs.isEnabled = !isLoading
        binding.recyclerViewThumbnails.isEnabled = !isLoading
        binding.searchViewPdf.isEnabled = !isLoading
    }

    private fun showToast(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun requestCameraPermission() {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun prepareNextImageUri(): Uri? {
        nextImageUri = try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val storageDir = context?.cacheDir ?: return null
            val imageFile = File.createTempFile("CAPTURE_${timeStamp}_", ".jpg", storageDir)
            context?.let { FileProvider.getUriForFile(it, fileProviderAuthority, imageFile) }
        } catch (e: Exception) { null }
        return nextImageUri
    }

    private fun launchCamera() {
        val uriToLaunch = nextImageUri ?: prepareNextImageUri()
        if (uriToLaunch == null) {
            showToast("Error preparing image capture.")
            return
        }
        cameraLauncher.launch(uriToLaunch)
    }

    private fun deleteTempImageFile(uriString: String?) {
        if (uriString == null) return
        try {
            val uri = uriString.toUri()
            if (uri.scheme == "content" && uri.authority == fileProviderAuthority) {
                context?.contentResolver?.delete(uri, null, null)
            }
        } catch (e: Exception) { /* Ignore */ }
    }

    private fun clearCapturedImagesAndFiles() {
        val urisToDelete = imageToPdfViewModel.getUriStringsSnapshot()
        imageToPdfViewModel.clearUris()
        updateThumbnailAdapter()
        prepareNextImageUri()
        lifecycleScope.launch(Dispatchers.IO) {
            urisToDelete.forEach { deleteTempImageFile(it) }
        }
    }

    private fun processImagesAndCreatePdf() {
        showLoading(true)
        val urisToProcess = imageToPdfViewModel.capturedImageUris
        val combinedText = StringBuilder()

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                var pagesProcessed = 0
                for ((index, imageUri) in urisToProcess.withIndex()) {
                    val recognizedText = recognizeTextInImage(imageUri)
                    if (recognizedText != null) {
                        pagesProcessed++
                        combinedText.append(recognizedText)
                        if (index < urisToProcess.size - 1) {
                            combinedText.append("\n\n--- Page ${pagesProcessed + 1} ---\n\n")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            if (isAdded) showToast("Warning: Cannot process page ${index + 1}.")
                        }
                    }
                }
            }

            if (combinedText.isNotEmpty()) {
                createPdfFromText(combinedText.toString())?.let { (pdfUri, pageCount) ->
                    if (pdfUri != null) {
                        if (isAdded) showToast("PDF created successfully!")
                        addPdfToList(pdfUri, pageCount)
                        clearCapturedImagesAndFiles()
                    } else {
                        if (isAdded) showToast("Failed to create PDF.")
                    }
                } ?: if (isAdded) showToast("Failed to create PDF.") else TODO()
            } else {
                if (isAdded) showToast("No text extracted.")
                clearCapturedImagesAndFiles()
            }
            if (isAdded) showLoading(false)
        }
    }

    private suspend fun recognizeTextInImage(imageUri: Uri): String? = suspendCoroutine { continuation ->
        try {
            context?.let { ctx ->
                val inputImage = InputImage.fromFilePath(ctx, imageUri)
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText -> continuation.resume(visionText.text) }
                    .addOnFailureListener { continuation.resume(null) }
            } ?: continuation.resume(null)
        } catch (e: Exception) {
            continuation.resume(null)
        }
    }

    private suspend fun createPdfFromText(text: String): Pair<Uri?, Int>? = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val pageInfoTemplate = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val paint = Paint().apply { textSize = 10f; isAntiAlias = true; color = android.graphics.Color.BLACK }
        val margin = 40f
        val contentWidth = pageInfoTemplate.pageWidth - 2 * margin
        var currentPageNumber = 1
        var currentPage: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var yPosition = margin

        fun startNewPage() {
            if (currentPage != null) pdfDocument.finishPage(currentPage)
            currentPage = pdfDocument.startPage(pageInfoTemplate.toBuilder(currentPageNumber).create())
            canvas = currentPage?.canvas
            yPosition = margin
        }

        startNewPage()

        text.split('\n').forEach { line ->
            if (line.trim().startsWith("--- Page") && line.trim().endsWith("---")) {
                currentPageNumber++
                startNewPage()
                return@forEach
            }

            var remainingLine = line
            while (remainingLine.isNotEmpty()) {
                if (canvas == null) break

                if (yPosition + (paint.descent() - paint.ascent()) > pageInfoTemplate.pageHeight - margin) {
                    currentPageNumber++
                    startNewPage()
                    if (canvas == null) break
                }

                val charsFitted = paint.breakText(remainingLine, true, contentWidth, null)
                canvas?.drawText(remainingLine, 0, charsFitted, margin, yPosition, paint)
                yPosition += (paint.descent() - paint.ascent()) + 2f
                remainingLine = remainingLine.substring(charsFitted)
            }
        }

        currentPage?.let { pdfDocument.finishPage(it) }

        val finalPageCount = currentPageNumber
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Generated_Doc_${timeStamp}_${finalPageCount}p.pdf"
        var savedFile: File? = null
        var savedUri: Uri? = null

        try {
            val currentContext = activity?.applicationContext ?: return@withContext null
            val appDir = File(currentContext.filesDir, pdfInternalSubDir).apply { mkdirs() }
            savedFile = File(appDir, fileName)
            FileOutputStream(savedFile).use { fos -> pdfDocument.writeTo(fos) }
            savedUri = FileProvider.getUriForFile(currentContext, fileProviderAuthority, savedFile)
        } catch (e: Exception) {
            savedFile?.delete()
            savedUri = null
        } finally {
            pdfDocument.close()
        }

        if (savedUri != null) {
            Pair(savedUri, finalPageCount)
        } else {
            null
        }
    }

    private fun filterPdfList(query: String?) {
        val filteredList = if (query.isNullOrBlank()) {
            fullPdfList
        } else {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            fullPdfList.filter { pdf ->
                pdf.name.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
            }
        }
        pdfAdapter.submitList(filteredList)
        updateEmptyPdfViewVisibility(filteredList.isEmpty(), query.isNullOrBlank())
    }

    private fun addPdfToList(pdfFileUri: Uri, pageCount: Int) {
        val pdfName = getFileNameFromUri(pdfFileUri) ?: "Gen_${System.currentTimeMillis()}.pdf"
        var creationTimestamp = 0L

        try {
            context?.contentResolver?.openFileDescriptor(pdfFileUri, "r")?.use { _ ->
                creationTimestamp = System.currentTimeMillis()
            } ?: run { creationTimestamp = System.currentTimeMillis() }
        } catch (e: Exception) {
            creationTimestamp = System.currentTimeMillis()
        }


        val newPdfFile = PdfFile(pdfName, pdfFileUri, creationTimestamp, pageCount)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            if (!isAdded) return@launch

            val newList = mutableListOf<PdfFile>().apply {
                add(newPdfFile)
                addAll(fullPdfList)
                sortByDescending { it.creationTimestamp }
            }
            fullPdfList = newList
            filterPdfList(binding.searchViewPdf.query?.toString())
            binding.recyclerViewPdfs.layoutManager?.scrollToPosition(0)
        }
    }

    private fun loadSavedPdfs() {
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val loadedPdfs = mutableListOf<PdfFile>()
            val currentContext = context?.applicationContext ?: return@launch

            try {
                val appDir = File(currentContext.filesDir, pdfInternalSubDir)
                if (appDir.isDirectory) {
                    appDir.listFiles { file -> file.isFile && file.name.endsWith(".pdf", ignoreCase = true) }
                        ?.sortedByDescending { it.lastModified() }
                        ?.forEach { file ->
                            try {
                                val fileUri = FileProvider.getUriForFile(currentContext, fileProviderAuthority, file)
                                val timestamp = file.lastModified()
                                val pageCount = getPdfPageCount(currentContext, fileUri)
                                loadedPdfs.add(PdfFile(file.name, fileUri, timestamp, pageCount))
                            } catch (e: Exception) {
                                // Skip problematic file
                            }
                        }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { if (isAdded) showToast("Error loading PDFs.") }
            }

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                fullPdfList = loadedPdfs
                filterPdfList(binding.searchViewPdf.query?.toString())
                showLoading(false)
            }
        }
    }

    private fun getPdfPageCount(context: Context, pdfUri: Uri): Int {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                renderer.pageCount
            } else {
                -1
            }
        } catch (e: IOException) {
            -1
        } catch (e: SecurityException) {
            -1
        } catch (e: Exception) {
            -1
        } finally {
            try { renderer?.close() } catch (e: Exception) { /* Ignore */ }
            try { pfd?.close() } catch (e: Exception) { /* Ignore */ }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        context?.contentResolver?.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        if (fileName == null && uri.scheme == "file") {
            fileName = uri.path?.let { File(it).name }
        }
        return fileName ?: uri.lastPathSegment?.takeIf { it.isNotEmpty() }
    }

    override fun onPdfItemClicked(pdfFile: PdfFile) {
        val currentContext = context ?: return
        val file = File(File(currentContext.filesDir, pdfInternalSubDir), pdfFile.name)

        if (!file.exists()) {
            showToast("PDF file not found or inaccessible.")
            loadSavedPdfs()
            return
        }

        val uri: Uri? = try {
            FileProvider.getUriForFile(currentContext, fileProviderAuthority, file)
        } catch (e: IllegalArgumentException) {
            showToast("Error creating URI for the file.")
            null
        } catch (e: Exception) {
            showToast("Error accessing the file.")
            null
        }

        uri?.let { fileUri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                showToast("No PDF viewer app found.")
            } catch (e: SecurityException){
                showToast("Permission denied to open PDF.")
            } catch (e: Exception) {
                showToast("Could not open PDF.")
            }
        }
    }

    override fun onRemoveThumbnailClicked(imageUri: Uri) {
        imageToPdfViewModel.removeUri(imageUri)
        updateThumbnailAdapter()
        lifecycleScope.launch(Dispatchers.IO) { deleteTempImageFile(imageUri.toString()) }
    }

    override fun onPdfOptionsClicked(pdfFile: PdfFile, anchorView: View) {
        val currentContext = context ?: return
        PopupMenu(currentContext, anchorView).apply {
            try {
                menuInflater.inflate(R.menu.document_options_menu, menu)
            } catch (e: Exception) {
                showToast("Error showing options.")
                return@apply
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_open_doc -> onPdfItemClicked(pdfFile)
                    R.id.action_rename_doc -> showRenameDialog(pdfFile)
                    R.id.action_delete_doc -> showDeleteConfirmationDialog(pdfFile)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }.show()
    }

    private fun showRenameDialog(pdfFile: PdfFile) {
        val currentContext = context ?: return
        val currentName = pdfFile.name.removeSuffix(".pdf")
        val editText = EditText(currentContext).apply {
            setText(currentName)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSelection(currentName.length)
        }
        val container = FrameLayout(currentContext).apply {
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
            addView(editText)
        }

        AlertDialog.Builder(currentContext)
            .setTitle("Rename PDF")
            .setView(container)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                when {
                    newName.isEmpty() -> showToast("Filename cannot be empty.")
                    newName == currentName -> { /* No change */ }
                    newName.contains("/") || newName.contains("\\") -> showToast("Invalid filename.")
                    else -> lifecycleScope.launch { renamePdfFile(pdfFile, "$newName.pdf") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(pdfFile: PdfFile) {
        val currentContext = context ?: return
        AlertDialog.Builder(currentContext)
            .setTitle("Delete PDF")
            .setMessage("Delete '${pdfFile.name}'?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch { deletePdfFile(pdfFile) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun renamePdfFile(pdfFileToRename: PdfFile, newName: String) {
        showLoading(true)
        var success = false
        var message = "Failed to rename PDF."
        withContext(Dispatchers.IO) {
            try {
                context?.let { ctx ->
                    val appContext = ctx.applicationContext
                    val appDir = File(appContext.filesDir, pdfInternalSubDir)
                    val oldFile = File(appDir, pdfFileToRename.name)
                    val newFile = File(appDir, newName)

                    if (!oldFile.exists()) {
                        message = "Original file not found."
                        withContext(Dispatchers.Main) { if (isAdded) removePdfItemFromListUI(pdfFileToRename.uri) }
                    } else if (newFile.exists()) {
                        message = "A file with that name already exists."
                    } else if (oldFile.renameTo(newFile)) {
                        try {
                            val newUri = FileProvider.getUriForFile(appContext, fileProviderAuthority, newFile)
                            val newTimestamp = newFile.lastModified()
                            val pageCount = pdfFileToRename.pageCount

                            withContext(Dispatchers.Main) {
                                if (isAdded) updatePdfItemInListUI(pdfFileToRename.uri, newName, newUri, newTimestamp, pageCount)
                            }
                            message = "Rename successful."
                            success = true
                        } catch (e: Exception) {
                            message = "Rename failed (cannot get new URI or info)."
                            withContext(Dispatchers.Main) { if (isAdded) loadSavedPdfs() }
                        }
                    }
                } ?: run { message = "Rename failed (context null)." }
            } catch (e: Exception) {
                message = "Error during rename."
            }
        }
        if (isAdded) {
            showToast(message)
            showLoading(false)
        }
    }

    private suspend fun deletePdfFile(pdfFileToDelete: PdfFile) {
        showLoading(true)
        var success = false
        var message = "Failed to delete PDF."
        withContext(Dispatchers.IO) {
            try {
                context?.let { ctx ->
                    val appDir = File(ctx.filesDir, pdfInternalSubDir)
                    val fileToDeleteOnDisk = File(appDir, pdfFileToDelete.name)
                    if (fileToDeleteOnDisk.exists()) {
                        success = fileToDeleteOnDisk.delete()
                        message = if(success) "PDF deleted." else "Failed to delete file."
                    } else {
                        success = true
                        message = "File already deleted from storage."
                    }
                } ?: run { message = "Delete failed (context null)." }
            } catch (e: Exception) {
                message = "Error during delete."
            }
        }
        if (isAdded) {
            if (success) {
                removePdfItemFromListUI(pdfFileToDelete.uri)
            }
            showToast(message)
            showLoading(false)
        }
    }

    private fun updatePdfItemInListUI(oldUri: Uri, newName: String, newUri: Uri, newTimestamp: Long, pageCount: Int) {
        if (!isAdded) return
        val index = fullPdfList.indexOfFirst { it.uri == oldUri }
        if (index != -1) {
            val updatedList = fullPdfList.toMutableList()
            updatedList[index] = PdfFile(newName, newUri, newTimestamp, pageCount)
            updatedList.sortByDescending { it.creationTimestamp }
            fullPdfList = updatedList.toList()
            filterPdfList(binding.searchViewPdf.query?.toString())
        } else {
            loadSavedPdfs() // Fallback
        }
    }

    private fun removePdfItemFromListUI(uriToDelete: Uri) {
        if (!isAdded) return
        val initialSize = fullPdfList.size
        fullPdfList = fullPdfList.filterNot { it.uri == uriToDelete }
        if (fullPdfList.size < initialSize) {
            filterPdfList(binding.searchViewPdf.query?.toString())
        }
    }

    private fun PdfDocument.PageInfo.toBuilder(pageNumber: Int): PdfDocument.PageInfo.Builder {
        return PdfDocument.PageInfo.Builder(this.pageWidth, this.pageHeight, pageNumber)
    }
}