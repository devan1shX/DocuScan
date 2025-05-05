package com.example.mc

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.mc.databinding.ActivityMainBinding
import com.example.mc.home_fragment.viewModel.MainViewModel
import com.google.mlkit.vision.documentscanner.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var scanner: GmsDocumentScanner
    private lateinit var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewPager = binding.viewPager

        initializeScanner()
        setupScannerLauncher()
        setupViewPager()
        setupBottomNavigation()

        binding.fabScan.visibility = View.VISIBLE
        binding.fabScan.setOnClickListener { startScan() }
    }

    private fun initializeScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        scanner = GmsDocumentScanning.getClient(options)
    }

    private fun setupScannerLauncher() {
        scannerLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                handleScanSuccess(GmsDocumentScanningResult.fromActivityResultIntent(result.data))
            }
        }
    }

    private fun setupViewPager() {
        viewPager.adapter = MainPagerAdapter(this)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.bottomNavigation.menu.getItem(position).isChecked = true
                binding.fabScan.visibility = if (position == 0) View.VISIBLE else View.GONE
            }
        })
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val position = when (item.itemId) {
                R.id.navigation_home -> 0
                R.id.navigation_image_to_pdf -> 1
                R.id.navigation_tts -> 2
                else -> 0
            }
            viewPager.setCurrentItem(position, false)
            true
        }
    }

    fun startScan() {
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                try {
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "Scanner not available.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error starting scan: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun handleScanSuccess(result: GmsDocumentScanningResult?) {
        result?.getPdf()?.let { pdfResult ->
            copyPdfToInternalStorage(pdfResult.uri)?.let { path ->
                mainViewModel.insertNewScan(path)
                Toast.makeText(this, "Scan saved successfully.", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "Error saving scan.", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(this, "Scan produced no PDF result.", Toast.LENGTH_SHORT).show()
    }

    private fun copyPdfToInternalStorage(sourceUri: Uri): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ScanFile_${timeStamp}_${UUID.randomUUID()}.pdf"
        val destinationFile = File(filesDir, fileName)
        return try {
            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            destinationFile.absolutePath
        } catch (e: Exception) {
            destinationFile.delete()
            null
        }
    }
}
