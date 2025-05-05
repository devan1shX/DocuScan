package com.example.mc.imageToPdf_fragment.viewModel

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel

class ImageToPdfViewModel : ViewModel() {

    private val _capturedImageUriStrings = mutableListOf<String>()
    val capturedImageUris: List<Uri>
        get() = synchronized(_capturedImageUriStrings) {
            _capturedImageUriStrings.mapNotNull { try { it.toUri() } catch (e: Exception) { null } }
        }

    fun addUri(uri: Uri) {
        synchronized(_capturedImageUriStrings) {
            _capturedImageUriStrings.add(uri.toString())
        }
    }

    fun removeUri(uri: Uri) {
        synchronized(_capturedImageUriStrings) {
            _capturedImageUriStrings.remove(uri.toString())
        }
    }

    fun getUriStringsSnapshot(): List<String> {
        synchronized(_capturedImageUriStrings) {
            return _capturedImageUriStrings.toList()
        }
    }

    fun clearUris() {
        synchronized(_capturedImageUriStrings) {
            _capturedImageUriStrings.clear()
        }
    }

    fun hasUris(): Boolean {
        synchronized(_capturedImageUriStrings) {
            return _capturedImageUriStrings.isNotEmpty()
        }
    }
}
