package com.example.mc.home_fragment.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.mc.home_fragment.db.AppDatabase
import com.example.mc.home_fragment.db.ScannedDocument
import com.example.mc.home_fragment.db.ScannedDocumentDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val documentDao: ScannedDocumentDao
    val allDocuments: LiveData<List<ScannedDocument>>

    init {
        val database = AppDatabase.getDatabase(application)
        documentDao = database.scannedDocumentDao()
        allDocuments = documentDao.getAllDocuments()
    }

    fun insertNewScan(pdfPath: String) = viewModelScope.launch {
        val pageCount = withContext(Dispatchers.IO) {
            ScannedDocument.getPdfPageCount(pdfPath)
        }
        val newDocument = ScannedDocument(
            title = ScannedDocument.generateDefaultTitle(),
            pdfPath = pdfPath,
            pageCount = pageCount
        )
        documentDao.insert(newDocument)
    }

    suspend fun renameDocument(document: ScannedDocument, newTitle: String): Boolean {
        return withContext(Dispatchers.IO) {
            val oldPath = document.pdfPath
            val renameResultPath = renamePdfFile(oldPath, newTitle)
            if (renameResultPath != null) {
                val updatedDocument = document.copy(title = newTitle, pdfPath = renameResultPath)
                try {
                    documentDao.update(updatedDocument)
                    true
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }
        }
    }

    fun delete(document: ScannedDocument) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val fileDeleted = deletePdfFile(document.pdfPath)
            if (fileDeleted) {
                documentDao.deleteById(document.id)
            } else {
                documentDao.deleteById(document.id)
            }
        } catch (e: Exception) { }
    }

    private suspend fun renamePdfFile(oldPath: String, newTitle: String): String? = withContext(Dispatchers.IO) {
        val oldFile = File(oldPath)
        if (!oldFile.exists()) return@withContext null
        val parentDir = oldFile.parentFile ?: return@withContext null
        val sanitizedTitleStem = sanitizeFilename(newTitle)
        val newFileName = "$sanitizedTitleStem.pdf"
        var newFile = File(parentDir, newFileName)
        var counter = 1
        val maxAttempts = 100
        while (newFile.exists() && newFile.absolutePath != oldFile.absolutePath && counter < maxAttempts) {
            newFile = File(parentDir, "$sanitizedTitleStem($counter).pdf")
            counter++
        }
        if (newFile.absolutePath == oldFile.absolutePath) return@withContext oldPath
        if (newFile.exists()) return@withContext null

        return@withContext try {
            if (oldFile.renameTo(newFile)) newFile.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun deletePdfFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            (file.exists() && file.delete()) || !file.exists()
        } catch (e: Exception) {
            false
        }
    }

    private fun sanitizeFilename(name: String): String {
        val invalidChars = Regex("[\\\\/:*?\"<>|]")
        var sanitized = name.replace(invalidChars, "").trim().replace(Regex("\\s+"), "_")
        sanitized = sanitized.trim('_').trim('.')
        val maxLength = 100
        if (sanitized.length > maxLength) {
            sanitized = sanitized.substring(0, maxLength).trimEnd('_').trimEnd('.')
        }
        return if (sanitized.isEmpty()) "Scan_${System.currentTimeMillis()}" else sanitized
    }
}
