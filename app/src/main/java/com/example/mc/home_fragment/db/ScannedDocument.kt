package com.example.mc.home_fragment.db

import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "scanned_documents")
data class ScannedDocument(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var title: String,
    val pdfPath: String,
    val scanTimestamp: Long = System.currentTimeMillis(),
    val pageCount: Int = 0
) {
    fun getFormattedTimestamp(): String {
        return try {
            val date = Date(scanTimestamp)
            val format = SimpleDateFormat("MMM d, yyyy hh:mm a", Locale.getDefault())
            format.format(date)
        } catch (e: Exception) {
            Date(scanTimestamp).toString()
        }
    }

    companion object {
        fun generateDefaultTitle(timestamp: Long = System.currentTimeMillis()): String {
            return try {
                val date = Date(timestamp)
                val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                "Scan_${format.format(date)}"
            } catch (e: Exception) {
                "Scan_${timestamp}"
            }
        }

        fun getPdfPageCount(filePath: String): Int {
            var count = 0
            try {
                val file = File(filePath)
                if (file.exists()) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            count = renderer.pageCount
                        }
                    }
                }
            } catch (e: Exception) {
                count = 0
            }
            return count
        }
    }
}
