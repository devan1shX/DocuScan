package com.example.mc.imageToPdf_fragment.model

import android.net.Uri


data class PdfFile(
    val name: String,
    val uri: Uri,
    val creationTimestamp: Long,
    val pageCount: Int
)