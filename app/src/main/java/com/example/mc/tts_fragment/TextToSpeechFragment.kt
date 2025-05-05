package com.example.mc.tts_fragment // Adjust package name if needed

import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.mc.R // Make sure this matches your R file location
import com.example.mc.databinding.FragmentTextToSpeechBinding
import com.example.mc.tts_fragment.viewModel.PlaybackState
import com.example.mc.tts_fragment.viewModel.TextToSpeechViewModel
import com.itextpdf.kernel.pdf.PdfDocument // iText Import
import com.itextpdf.kernel.pdf.PdfReader // iText Import
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor // iText Import
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy // iText Import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream // Needed for iText
import java.util.*

class TextToSpeechFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentTextToSpeechBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TextToSpeechViewModel by activityViewModels()

    // --- TTS ---
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var availableLanguages: List<Locale> = listOf()
    private lateinit var selectedLocale: Locale
    private lateinit var languageArrayAdapter: ArrayAdapter<String>
    private val synthesisUtteranceId = "SynthesisID"

    // --- MediaPlayer ---
    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioFile: File? = null // Keep track of the synthesized WAV File object

    // --- PDF Handling ---
    private lateinit var pdfArrayAdapter: ArrayAdapter<String>
    private val PDF_DIRECTORY_NAME = "MyAppScans" // Matches ImageToPdfFragment
    private var isPdfSelectionListenerActive = true // Control listener during programmatic changes
    // Flag to track programmatic text changes
    private var isSettingTextProgrammatically = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextToSpeechBinding.inflate(inflater, container, false)
        restoreSelectedLocaleFromViewModel()
        if (tts == null && !isTtsInitialized) {
            Log.d("TTSFragment", "onCreateView: Initializing TTS")
            tts = TextToSpeech(requireContext().applicationContext, this)
        } else {
            Log.d("TTSFragment", "onCreateView: TTS instance exists (isTtsInitialized=$isTtsInitialized)")
        }
        initializeMediaPlayer()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("TTSFragment", "onViewCreated: ViewModel State: ${viewModel.playbackState}, TTS File: ${viewModel.audioFilePath}, PDF File: ${viewModel.selectedPdfPath}, Pos: ${viewModel.lastPlaybackPosition}")

        restoreSelectedLocaleFromViewModel()
        setupUI()
        findPdfFiles()
        restoreUiState()
        setUIEnabled(false) // Disable UI initially

        // --- TTS Initialization Check ---
        if (isTtsInitialized && tts != null) {
            Log.d("TTSFragment", "onViewCreated: TTS already initialized.")
            populateLanguageDropdown()
            setupLanguageSelectionListener()
            updateSpeechRate(binding.sliderRate.value)
            updateSpeechPitch(binding.sliderPitch.value)
            setLanguage(selectedLocale, forceUpdate = false)
            setupTtsListener()
            setUIEnabled(true)
            handlePlaybackStateOnReturn()
        } else if (tts == null && !isTtsInitialized) {
            Log.d("TTSFragment", "onViewCreated: TTS not yet initialized, waiting for onInit.")
        } else {
            Log.w("TTSFragment", "onViewCreated: TTS state inconsistent. Resetting UI.")
            setUIEnabled(false)
        }
        updatePlaybackButtonState()
    }

    // Initialize or re-initialize MediaPlayer instance
    private fun initializeMediaPlayer() {
        if (mediaPlayer == null) {
            Log.d("TTSFragment", "Initializing MediaPlayer instance")
            mediaPlayer = MediaPlayer()
            setupMediaPlayerListeners()
        } else {
            Log.d("TTSFragment", "MediaPlayer instance already exists")
            setupMediaPlayerListeners() // Ensure listeners are attached
        }
    }

    // Setup listeners for MediaPlayer events
    private fun setupMediaPlayerListeners() {
        mediaPlayer?.setOnPreparedListener { mp ->
            Log.i("TTSFragment", "MediaPlayer prepared.")
            if (!isAdded || _binding == null || viewModel.playbackState != PlaybackState.PREPARING) {
                Log.w("TTSFragment", "MediaPlayer prepared but fragment/binding gone or state changed.")
                resetPlaybackState(clearFile = true)
                updatePlaybackButtonState()
                binding.progressBarSynthesis.isVisible = false
                return@setOnPreparedListener
            }
            viewModel.playbackState = PlaybackState.PLAYING
            viewModel.synthesisFailed = false // Preparation successful
            mp.seekTo(viewModel.lastPlaybackPosition) // Seek before starting
            mp.start()
            updatePlaybackButtonState()
            binding.progressBarSynthesis.isVisible = false // Hide progress bar
        }

        mediaPlayer?.setOnCompletionListener {
            Log.i("TTSFragment", "MediaPlayer playback completed.")
            if (!isAdded || _binding == null) return@setOnCompletionListener
            resetPlaybackState() // Reset to idle after completion (keeps PDF selection)
            updatePlaybackButtonState()
        }

        mediaPlayer?.setOnErrorListener { _, what, extra ->
            Log.e("TTSFragment", "MediaPlayer error: What: $what, Extra: $extra")
            if (!isAdded || _binding == null) return@setOnErrorListener true
            val errorMsg = when (what) {
                MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Media server died"
                MediaPlayer.MEDIA_ERROR_UNKNOWN -> "Unknown media player error"
                else -> "MediaPlayer error ($what, $extra)"
            }
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            resetPlaybackState(clearFile = true) // Reset fully on critical error
            updatePlaybackButtonState()
            binding.progressBarSynthesis.isVisible = false
            true // Indicate error was handled
        }
    }

    // Restore state logic when fragment is recreated or returned to
    private fun handlePlaybackStateOnReturn() {
        when (viewModel.playbackState) {
            PlaybackState.PLAYING, PlaybackState.PAUSED -> {
                val path = viewModel.audioFilePath
                if (path != null && File(path).exists()) {
                    currentAudioFile = File(path)
                    Log.d("TTSFragment", "Resuming state: Preparing MediaPlayer for TTS file: $path")
                    prepareMediaPlayer(currentAudioFile!!)
                } else {
                    Log.w("TTSFragment", "Resuming TTS state: Audio file missing. Resetting.")
                    resetPlaybackState(clearFile = true)
                }
            }
            PlaybackState.EXTRACTING_PDF -> {
                Log.w("TTSFragment", "Resuming state: Was extracting PDF. Resetting.")
                viewModel.pdfExtractionError = "Extraction interrupted."
                resetPlaybackState(clearFile = true) // Reset fully
            }
            PlaybackState.SYNTHESIZING, PlaybackState.PREPARING -> {
                Log.w("TTSFragment", "Resuming state: Was synthesizing/preparing. Resetting.")
                resetPlaybackState(clearFile = true) // Reset fully
            }
            PlaybackState.IDLE -> {
                Log.d("TTSFragment", "Resuming state: Already idle.")
            }
        }
        // Button state updated after potential async prepare
        updatePlaybackButtonState()
    }


    private fun restoreSelectedLocaleFromViewModel() {
        val savedTag = viewModel.selectedLanguageTag
        selectedLocale = try {
            Locale.forLanguageTag(savedTag)
        } catch (e: Exception) {
            Log.w("TTSFragment", "Failed to parse language tag '$savedTag', falling back to US.", e)
            Locale.US // Fallback
        }
        if (selectedLocale.toLanguageTag() != savedTag) {
            viewModel.selectedLanguageTag = selectedLocale.toLanguageTag()
        }
        Log.d("TTSFragment", "Restored locale: ${selectedLocale.displayLanguage}")
    }

    override fun onDestroyView() {
        Log.d("TTSFragment", "onDestroyView called.")
        // Save MediaPlayer position if playing/paused
        if (mediaPlayer?.isPlaying == true) {
            try {
                viewModel.lastPlaybackPosition = mediaPlayer?.currentPosition ?: 0
                Log.d("TTSFragment", "onDestroyView: Saving playback position: ${viewModel.lastPlaybackPosition}")
                mediaPlayer?.pause() // Pause to prevent playing in background briefly
            } catch (e: IllegalStateException) {
                Log.w("TTSFragment", "onDestroyView: Error getting/pausing MediaPlayer position", e)
            }
        } else if (viewModel.playbackState == PlaybackState.PAUSED) {
            Log.d("TTSFragment", "onDestroyView: MediaPlayer was paused. Position: ${viewModel.lastPlaybackPosition}")
        }

        tts?.stop() // Stop any ongoing TTS synthesis or speech

        // Reset MediaPlayer but don't release yet if view might be recreated
        try {
            if (mediaPlayer?.isPlaying == true || viewModel.playbackState == PlaybackState.PAUSED || viewModel.playbackState == PlaybackState.PREPARING) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
        } catch (e: IllegalStateException) {
            Log.w("TTSFragment", "onDestroyView: Error stopping/resetting MediaPlayer", e)
        }

        _binding = null // Crucial for preventing memory leaks
        super.onDestroyView()
        Log.d("TTSFragment", "onDestroyView finished.")
    }

    override fun onDestroy() {
        Log.d("TTSFragment", "onDestroy called.")
        // Fully release resources when Fragment is destroyed permanently
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsInitialized = false

        mediaPlayer?.release() // Release MediaPlayer resources
        mediaPlayer = null

        // Clean up the synthesized audio file if it exists
        cleanupAudioFile()

        super.onDestroy()
        Log.d("TTSFragment", "onDestroy finished.")
    }

    // Helper to delete the synthesized audio file
    private fun cleanupAudioFile() {
        val path = viewModel.audioFilePath
        if (path != null && context != null) { // Add context check
            Log.d("TTSFragment", "Cleaning up TTS audio file: $path")
            try {
                val fileToDelete = File(path)
                // Extra safety: ensure it's in the expected cache dir
                if (fileToDelete.exists() && fileToDelete.parentFile == requireContext().cacheDir) {
                    if (fileToDelete.delete()) {
                        Log.d("TTSFragment", "Successfully deleted TTS audio file.")
                    } else {
                        Log.w("TTSFragment", "Failed to delete TTS audio file.")
                    }
                } else if (fileToDelete.exists()) {
                    Log.w("TTSFragment", "Skipping delete of TTS audio file outside cache: $path")
                }
            } catch (e: Exception) {
                Log.e("TTSFragment", "Error cleaning up TTS audio file", e)
            } finally {
                viewModel.audioFilePath = null // Clear path from ViewModel
                currentAudioFile = null
            }
        }
    }


    override fun onInit(status: Int) {
        if (!isAdded || context == null) {
            Log.w("TTSFragment", "onInit called but fragment detached or context null.")
            tts = null
            isTtsInitialized = false
            viewModel.resetPlaybackAndPdfSelection()
            return
        }

        if (status == TextToSpeech.SUCCESS) {
            Log.i("TTSFragment", "TTS Initialization Successful.")
            isTtsInitialized = true

            if (_binding == null) {
                Log.w("TTSFragment", "onInit successful but binding became null before UI setup.")
                isTtsInitialized = false // Revert flag
                return
            }

            // Setup TTS dependent parts now that TTS is ready
            populateLanguageDropdown()
            setupLanguageSelectionListener()
            updateSpeechRate(binding.sliderRate.value)
            updateSpeechPitch(binding.sliderPitch.value)
            setLanguage(selectedLocale, forceUpdate = false)
            setupTtsListener()
            setUIEnabled(true) // Enable controls
            updatePlaybackButtonState() // Set initial button state

        } else {
            Log.e("TTSFragment", "TTS Initialization Failed. Status: $status")
            Toast.makeText(requireContext(), "TTS Initialization Failed.", Toast.LENGTH_LONG).show()
            setUIEnabled(false) // Disable UI overall
            isTtsInitialized = false
            tts = null
            resetPlaybackState(clearFile = true) // Reset state if TTS fails
            updatePlaybackButtonState()
        }
    }

    private fun setupUI() {
        if (_binding == null) return
        Log.d("TTSFragment", "Setting up UI listeners.")

        // --- PDF Selection ---
        // Initialize adapter first
        pdfArrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, mutableListOf<String>())
        binding.autoCompleteTextViewPdf.setAdapter(pdfArrayAdapter)
        setupPdfSelection() // Setup listener

        // --- Main Play/Pause/Resume Button ---
        binding.buttonSpeak.setOnClickListener { handlePlaybackAction() }

        // --- Sliders ---
        binding.sliderRate.addOnChangeListener { _, value, fromUser ->
            if (fromUser && isTtsInitialized) {
                Log.d("TTSFragment", "Rate slider changed to $value by user")
                updateSpeechRate(value)
                viewModel.currentRate = value
                // Changing rate requires re-synthesis if playing/paused/idle with text
                if (viewModel.playbackState != PlaybackState.EXTRACTING_PDF && binding.editTextToSpeak.text?.isNotEmpty() == true) {
                    resetPlaybackState(clearFile = true)
                }
                updatePlaybackButtonState()
            }
        }
        binding.sliderPitch.addOnChangeListener { _, value, fromUser ->
            if (fromUser && isTtsInitialized) {
                Log.d("TTSFragment", "Pitch slider changed to $value by user")
                updateSpeechPitch(value)
                viewModel.currentPitch = value
                // Changing pitch requires re-synthesis
                if (viewModel.playbackState != PlaybackState.EXTRACTING_PDF && binding.editTextToSpeak.text?.isNotEmpty() == true) {
                    resetPlaybackState(clearFile = true)
                }
                updatePlaybackButtonState()
            }
        }

        // --- Text Input ---
        binding.editTextToSpeak.addTextChangedListener { text ->
            val newText = text?.toString() ?: ""

            // Only proceed if the text content has actually changed
            if (newText != viewModel.currentText) {
                // *** Refined Logic using the flag ***
                if (isSettingTextProgrammatically) {
                    // Text change originated from PDF loading.
                    // Update ViewModel, clear errors, reset playback, but keep PDF selection.
                    Log.d("TTSFragment", "Text changed programmatically (isSettingTextProgrammatically=true)")
                    viewModel.currentText = newText
                    viewModel.pdfExtractionError = null // Clear PDF error on new text load
                    viewModel.synthesisFailed = false   // Clear TTS synthesis error
                    resetPlaybackState(clearFile = true) // Reset playback state
                    updatePlaybackButtonState()
                    // ** Reset the flag HERE, after handling the programmatic change **
                    isSettingTextProgrammatically = false
                    Log.d("TTSFragment", "Reset isSettingTextProgrammatically to false")
                } else {
                    // Text change originated from user typing.
                    Log.d("TTSFragment", "Text changed by user (isSettingTextProgrammatically=false)")
                    viewModel.currentText = newText
                    viewModel.pdfExtractionError = null // Clear any PDF error message
                    viewModel.synthesisFailed = false   // Clear synthesis error

                    // Clear the PDF dropdown selection visually if user types manually
                    if (binding.autoCompleteTextViewPdf.text.isNotEmpty()) {
                        isPdfSelectionListenerActive = false // Disable listener briefly
                        binding.autoCompleteTextViewPdf.setText("", false) // Clear selection text
                        viewModel.selectedPdfPath = null // Clear selected PDF path in ViewModel
                        isPdfSelectionListenerActive = true // Re-enable listener
                        Log.d("TTSFragment", "Cleared PDF selection due to manual text edit.")
                    }

                    resetPlaybackState(clearFile = true) // Reset playback state
                    updatePlaybackButtonState()
                }
            } // End check if text content changed
        } // End addTextChangedListener
    }


    // Central handler for the main button press
    private fun handlePlaybackAction() {
        // Check if PDF is currently being extracted
        if (viewModel.playbackState == PlaybackState.EXTRACTING_PDF) {
            Toast.makeText(context, "Extracting PDF text...", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if TTS/MediaPlayer are ready
        if (!isTtsInitialized || tts == null || mediaPlayer == null) {
            Toast.makeText(context, "TTS or Player not ready.", Toast.LENGTH_SHORT).show()
            Log.w("TTSFragment", "Playback action ignored: TTS Init=$isTtsInitialized, TTS Null=${tts == null}, MP Null=${mediaPlayer == null}")
            // Attempt re-initialization if needed
            if (tts == null && !isTtsInitialized && context != null) {
                tts = TextToSpeech(requireContext().applicationContext, this)
            }
            if (mediaPlayer == null) {
                initializeMediaPlayer()
            }
            return
        }

        // Handle actions based on current state
        when (viewModel.playbackState) {
            PlaybackState.IDLE -> {
                val text = binding.editTextToSpeak.text?.toString()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    startSynthesis(text)
                } else {
                    Toast.makeText(context, "No text to speak. Select PDF or enter text.", Toast.LENGTH_SHORT).show()
                    binding.editTextToSpeak.requestFocus()
                }
            }
            PlaybackState.PLAYING -> pausePlayback()
            PlaybackState.PAUSED -> resumePlayback()
            PlaybackState.SYNTHESIZING, PlaybackState.PREPARING -> {
                Log.d("TTSFragment", "Playback action ignored: Currently synthesizing or preparing.")
                Toast.makeText(context, "Please wait...", Toast.LENGTH_SHORT).show()
            }
            // EXTRACTING_PDF case handled at the beginning
            PlaybackState.EXTRACTING_PDF -> { /* Already handled */ }
        }
    }

    private fun startSynthesis(text: String) {
        if (!isTtsInitialized || tts == null || context == null) {
            Log.e("TTSFragment", "Cannot start synthesis: TTS not ready.")
            Toast.makeText(context, "TTS not available.", Toast.LENGTH_SHORT).show()
            resetPlaybackState(clearFile = true)
            updatePlaybackButtonState()
            return
        }
        if (viewModel.playbackState == PlaybackState.EXTRACTING_PDF) {
            Log.w("TTSFragment", "Cannot start synthesis while extracting PDF.")
            Toast.makeText(context, "Please wait for PDF extraction.", Toast.LENGTH_SHORT).show()
            return
        }

        cleanupAudioFile() // Clean up previous TTS file

        try {
            val outputDir = requireContext().cacheDir
            currentAudioFile = File.createTempFile("tts_audio_", ".wav", outputDir)
            viewModel.audioFilePath = currentAudioFile!!.absolutePath
            Log.i("TTSFragment", "Created TTS audio file: ${viewModel.audioFilePath}")
        } catch (e: IOException) {
            Log.e("TTSFragment", "Failed to create TTS audio file", e)
            Toast.makeText(context, "Error creating audio file.", Toast.LENGTH_SHORT).show()
            resetPlaybackState(clearFile = false) // Keep existing state, file creation failed
            updatePlaybackButtonState()
            return
        }

        viewModel.playbackState = PlaybackState.SYNTHESIZING
        viewModel.lastPlaybackPosition = 0
        viewModel.synthesisFailed = false
        updatePlaybackButtonState() // Show "Synthesizing..." state
        binding.progressBarSynthesis.isVisible = true

        val params = Bundle() // Add TTS parameters if needed

        // Ensure rate/pitch/language are set before synthesis
        updateSpeechRate(viewModel.currentRate)
        updateSpeechPitch(viewModel.currentPitch)
        // Language set via setLanguage -> tts.setLanguage

        Log.i("TTSFragment", "Starting TTS synthesis: UtteranceID=$synthesisUtteranceId")
        val result = tts?.synthesizeToFile(text, params, currentAudioFile!!, synthesisUtteranceId)

        if (result == TextToSpeech.ERROR) {
            Log.e("TTSFragment", "TTS synthesizeToFile call failed immediately.")
            Toast.makeText(context, "TTS synthesis request failed.", Toast.LENGTH_SHORT).show()
            viewModel.synthesisFailed = true // Mark failure
            resetPlaybackState(clearFile = true) // Clean up failed file attempt
            updatePlaybackButtonState()
            binding.progressBarSynthesis.isVisible = false
        } else {
            Log.d("TTSFragment", "TTS synthesis request submitted successfully.")
            // State remains SYNTHESIZING until listener callback
        }
    }


    private fun prepareMediaPlayer(audioFile: File) {
        if (mediaPlayer == null) {
            Log.e("TTSFragment", "Cannot prepare MediaPlayer: instance is null.")
            resetPlaybackState(clearFile = true)
            updatePlaybackButtonState()
            return
        }
        if (!audioFile.exists()) {
            Log.e("TTSFragment", "Cannot prepare MediaPlayer: audio file missing: ${audioFile.absolutePath}")
            Toast.makeText(context, "Error: Audio file missing.", Toast.LENGTH_SHORT).show()
            resetPlaybackState(clearFile = true)
            updatePlaybackButtonState()
            return
        }

        Log.d("TTSFragment", "Preparing MediaPlayer for TTS file: ${audioFile.absolutePath}")
        try {
            // Check current state before resetting
            if (mediaPlayer?.isPlaying == true || viewModel.playbackState == PlaybackState.PAUSED) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(audioFile.absolutePath)
            viewModel.playbackState = PlaybackState.PREPARING
            updatePlaybackButtonState()
            binding.progressBarSynthesis.isVisible = true
            mediaPlayer?.prepareAsync() // Prepare asynchronously
        } catch (e: Exception) { // Catch IOException, IllegalStateException, etc.
            Log.e("TTSFragment", "Error setting data source or preparing MediaPlayer", e)
            Toast.makeText(context, "Error preparing audio playback.", Toast.LENGTH_SHORT).show()
            resetPlaybackState(clearFile = true)
            updatePlaybackButtonState()
            binding.progressBarSynthesis.isVisible = false
        }
    }

    private fun pausePlayback() {
        if (mediaPlayer?.isPlaying == true) {
            try {
                mediaPlayer?.pause()
                viewModel.lastPlaybackPosition = mediaPlayer?.currentPosition ?: 0
                viewModel.playbackState = PlaybackState.PAUSED
                updatePlaybackButtonState()
                Log.i("TTSFragment", "Playback paused at ${viewModel.lastPlaybackPosition} ms.")
            } catch (e: IllegalStateException) {
                Log.e("TTSFragment", "Error pausing MediaPlayer", e)
                resetPlaybackState(clearFile = true)
                updatePlaybackButtonState()
            }
        } else {
            Log.w("TTSFragment", "Pause called but MediaPlayer not playing. State: ${viewModel.playbackState}")
        }
    }

    private fun resumePlayback() {
        if (mediaPlayer != null && viewModel.playbackState == PlaybackState.PAUSED && viewModel.audioFilePath != null) {
            try {
                // MediaPlayer should be prepared if paused state is valid
                mediaPlayer?.seekTo(viewModel.lastPlaybackPosition)
                mediaPlayer?.start()
                viewModel.playbackState = PlaybackState.PLAYING
                updatePlaybackButtonState()
                Log.i("TTSFragment", "Playback resumed from ${viewModel.lastPlaybackPosition} ms.")
            } catch (e: IllegalStateException) {
                Log.e("TTSFragment", "Error resuming MediaPlayer", e)
                // Attempt to re-prepare if resume fails drastically
                val path = viewModel.audioFilePath
                if(path != null && File(path).exists()){
                    prepareMediaPlayer(File(path))
                } else {
                    resetPlaybackState(clearFile = true)
                    updatePlaybackButtonState()
                }
            }
        } else {
            Log.w("TTSFragment", "Resume called but MediaPlayer not ready, not paused, or audio file missing. State: ${viewModel.playbackState}")
            resetPlaybackState(clearFile = true) // Reset if state is inconsistent
            updatePlaybackButtonState()
        }
    }

    // Reset playback state, optionally deleting the current TTS audio file
    // Does NOT clear the PDF selection or loaded text by default.
    private fun resetPlaybackState(clearFile: Boolean = false) {
        Log.d("TTSFragment", "Resetting playback state. Clear TTS file: $clearFile, Current state: ${viewModel.playbackState}")

        // Stop MediaPlayer if it's active
        if (mediaPlayer?.isPlaying == true || viewModel.playbackState == PlaybackState.PAUSED || viewModel.playbackState == PlaybackState.PREPARING) {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.reset() // Reset to idle state
                Log.d("TTSFragment", "MediaPlayer stopped and reset.")
            } catch (e: IllegalStateException) {
                Log.w("TTSFragment", "Error stopping/resetting MediaPlayer during state reset.", e)
                mediaPlayer?.release() // Release potentially broken instance
                mediaPlayer = null
                initializeMediaPlayer() // Get a fresh one
            }
        }
        // Stop TTS synthesis if it was running
        if (viewModel.playbackState == PlaybackState.SYNTHESIZING) {
            tts?.stop()
            Log.d("TTSFragment", "TTS synthesis stopped.")
        }

        // Reset ViewModel state related to playback
        viewModel.resetTtsPlaybackOnly() // Resets state (if not extracting), position, synthesisFailed flag

        // Hide progress bars unless we are still extracting PDF
        if (viewModel.playbackState != PlaybackState.EXTRACTING_PDF) {
            binding.progressBarSynthesis.isVisible = false
            binding.progressBarPdfLoading.isVisible = false
        } else {
            binding.progressBarSynthesis.isVisible = false // Hide TTS progress bar
            binding.progressBarPdfLoading.isVisible = true // Keep PDF progress bar visible
        }


        if (clearFile) {
            cleanupAudioFile() // This also clears viewModel.audioFilePath
        }

        // Caller should call updatePlaybackButtonState() after this
        Log.d("TTSFragment", "Playback state reset finished. New state: ${viewModel.playbackState}")
    }

    private fun restoreUiState() {
        if (_binding == null) return
        Log.d("TTSFragment", "Restoring UI state (Text, Sliders, PDF Selection)")

        // Restore text, sliders
        // ** Use the flag to prevent listener issues during restore **
        isSettingTextProgrammatically = true
        binding.editTextToSpeak.setText(viewModel.currentText)
        isSettingTextProgrammatically = false // Reset flag immediately after restore
        binding.sliderRate.value = viewModel.currentRate
        binding.sliderPitch.value = viewModel.currentPitch

        // Restore PDF selection in dropdown
        val selectedPdfPath = viewModel.selectedPdfPath
        if (selectedPdfPath != null) {
            val selectedFile = File(selectedPdfPath)
            val pdfName = selectedFile.name
            // Check if the file still exists *and* is in the current list of available files
            if (selectedFile.exists() && viewModel.availablePdfFiles.any { it.absolutePath == selectedPdfPath }) {
                // Temporarily disable listener to prevent re-triggering load on restore
                isPdfSelectionListenerActive = false
                binding.autoCompleteTextViewPdf.setText(pdfName, false) // Set display text
                isPdfSelectionListenerActive = true
                Log.d("TTSFragment", "Restored PDF selection: $pdfName")
            } else {
                Log.w("TTSFragment", "Selected PDF path '$selectedPdfPath' not found or invalid on restore.")
                viewModel.selectedPdfPath = null // Clear invalid path
                isPdfSelectionListenerActive = false
                binding.autoCompleteTextViewPdf.setText("", false) // Clear dropdown text
                isPdfSelectionListenerActive = true
            }
        } else {
            isPdfSelectionListenerActive = false
            binding.autoCompleteTextViewPdf.setText("", false) // Ensure dropdown is clear if no path
            isPdfSelectionListenerActive = true
        }

        // PDF extraction error message
        if (viewModel.pdfExtractionError != null) {
            Toast.makeText(context, "Previous PDF Error: ${viewModel.pdfExtractionError}", Toast.LENGTH_LONG).show()
            // Optionally clear error after showing: viewModel.pdfExtractionError = null
        }

        // Language dropdown and button state are handled elsewhere
    }

    private fun setUIEnabled(isEnabled: Boolean) {
        // Enables/disables controls *except* the main playback button,
        // which is handled by updatePlaybackButtonState based on the current playback state.
        _binding?.let { b ->
            Log.d("TTSFragment", "Setting UI enabled state: $isEnabled")
            val controlsAlpha = if (isEnabled) 1.0f else 0.5f

            b.editTextToSpeak.isEnabled = isEnabled
            b.sliderRate.isEnabled = isEnabled
            b.sliderPitch.isEnabled = isEnabled

            // Enable PDF selector only if UI is generally enabled AND not currently extracting
            // Also consider if PDF files are available
            val pdfSelectEnabled = isEnabled
                    && viewModel.playbackState != PlaybackState.EXTRACTING_PDF
                    && viewModel.availablePdfFiles.isNotEmpty()
            b.pdfSelectorInputLayout.isEnabled = pdfSelectEnabled
            b.autoCompleteTextViewPdf.isEnabled = pdfSelectEnabled
            // Ensure dropdown text color indicates enabled/disabled state
            val pdfTextColor = ContextCompat.getColor(requireContext(), if (pdfSelectEnabled) android.R.color.black else android.R.color.darker_gray)
            b.autoCompleteTextViewPdf.setTextColor(pdfTextColor)


            // Enable language selection if UI enabled, TTS ready, and languages available
            val langEnabled = isEnabled && isTtsInitialized && availableLanguages.isNotEmpty()
            b.languageInputLayout.isEnabled = langEnabled
            b.autoCompleteTextViewLanguage.isEnabled = langEnabled
            val langTextColor = ContextCompat.getColor(requireContext(), if (langEnabled) android.R.color.black else android.R.color.darker_gray)
            b.autoCompleteTextViewLanguage.setTextColor(langTextColor)

            b.controlsCard.alpha = controlsAlpha // Visual cue for controls card

            // Playback button enablement is handled separately by updatePlaybackButtonState
        }
    }

    private fun updatePlaybackButtonState() {
        activity?.runOnUiThread {
            if (_binding == null || context == null) return@runOnUiThread

            val b = binding
            val ctx = requireContext()
            val hasText = b.editTextToSpeak.text?.toString()?.trim()?.isNotEmpty() == true
            val isReadyToSpeak = isTtsInitialized && hasText && !viewModel.synthesisFailed && viewModel.pdfExtractionError == null

            // Default state = enabled (will be overridden by states below)
            b.buttonSpeak.isEnabled = true
            b.buttonSpeak.alpha = 1.0f // Default alpha

            when (viewModel.playbackState) {
                PlaybackState.IDLE -> {
                    b.buttonSpeak.text = "Speak"
                    b.buttonSpeak.icon = ContextCompat.getDrawable(ctx, R.drawable.ic_play)
                    b.buttonSpeak.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.primaryBlue)
                    b.buttonSpeak.isEnabled = isReadyToSpeak // Enable only if ready
                }
                PlaybackState.EXTRACTING_PDF -> {
                    b.buttonSpeak.text = "Loading PDF..."
                    b.buttonSpeak.icon = null // Or a loading icon
                    b.buttonSpeak.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.darkGray)
                    b.buttonSpeak.isEnabled = false // Disable interaction
                }
                PlaybackState.SYNTHESIZING -> {
                    b.buttonSpeak.text = "Synthesizing..."
                    b.buttonSpeak.icon = null
                    b.buttonSpeak.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.darkGray)
                    b.buttonSpeak.isEnabled = false // Disable interaction
                }
                PlaybackState.PREPARING -> {
                    b.buttonSpeak.text = "Preparing..."
                    b.buttonSpeak.icon = null
                    b.buttonSpeak.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.darkGray)
                    b.buttonSpeak.isEnabled = false // Disable interaction
                }
                PlaybackState.PLAYING -> {
                    b.buttonSpeak.text = "Pause"
                    b.buttonSpeak.icon = ContextCompat.getDrawable(ctx, R.drawable.ic_pause)
                    b.buttonSpeak.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.darkGray)
                    b.buttonSpeak.isEnabled = true // Allow pausing
                }
                PlaybackState.PAUSED -> {
                    b.buttonSpeak.text = "Resume"
                    b.buttonSpeak.icon = ContextCompat.getDrawable(ctx, R.drawable.ic_play)
                    b.buttonSpeak.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.primaryBlue)
                    b.buttonSpeak.isEnabled = true // Allow resuming
                }
            }

            // Dim button if disabled
            if (!b.buttonSpeak.isEnabled) {
                b.buttonSpeak.alpha = 0.5f
            }

            Log.d("TTSFragment", "Updated button state to: ${b.buttonSpeak.text}, Enabled: ${b.buttonSpeak.isEnabled}, State: ${viewModel.playbackState}")
        }
    }

    // --- Language Handling ---
    // (No changes needed in this section from previous version)
    private fun populateLanguageDropdown() {
        if (!isTtsInitialized || tts == null || context == null || _binding == null) {
            Log.w("TTSFragment", "populateLanguageDropdown precondition failed.")
            _binding?.languageInputLayout?.isEnabled = false
            return
        }
        try {
            availableLanguages = tts?.availableLanguages?.filterNotNull()
                ?.filter { it.displayName.isNotBlank() && it.language.isNotBlank() }
                ?.sortedBy { it.displayName }
                ?.toList() ?: emptyList()
            Log.d("TTSFragment", "Found ${availableLanguages.size} valid languages.")
        } catch (e: Exception) {
            Log.e("TTSFragment", "Error getting available languages", e)
            availableLanguages = emptyList()
        }

        val langLayoutEnabled = availableLanguages.isNotEmpty() && isTtsInitialized // Also check TTS init
        binding.languageInputLayout.isEnabled = langLayoutEnabled
        binding.autoCompleteTextViewLanguage.isEnabled = langLayoutEnabled

        if (availableLanguages.isEmpty()) {
            if (isAdded) Toast.makeText(requireContext(), "No TTS languages found.", Toast.LENGTH_LONG).show()
            try { binding.autoCompleteTextViewLanguage.setAdapter(null) }
            catch (e: Exception) { Log.e("TTSFragment", "Error clearing language adapter", e) }
            binding.autoCompleteTextViewLanguage.setText("", false)
            return
        }

        val languageNames = availableLanguages.map { it.displayName }
        languageArrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, languageNames)
        binding.autoCompleteTextViewLanguage.setAdapter(languageArrayAdapter)

        val initialIndex = availableLanguages.indexOfFirst { it == selectedLocale }
        if (initialIndex >= 0) {
            isPdfSelectionListenerActive = false // Prevent listener firing on restore
            binding.autoCompleteTextViewLanguage.setText(selectedLocale.displayName, false)
            isPdfSelectionListenerActive = true
            setLanguage(selectedLocale, forceUpdate = false) // Ensure engine matches
        } else {
            val defaultLocale = availableLanguages.firstOrNull() ?: Locale.getDefault()
            Log.w("TTSFragment", "Saved locale '${selectedLocale.displayName}' not found. Using default: ${defaultLocale.displayName}")
            setLanguage(defaultLocale, forceUpdate = true) // Set and update UI/ViewModel
        }
    }

    private fun setupLanguageSelectionListener() {
        if (_binding == null) return
        binding.autoCompleteTextViewLanguage.setOnItemClickListener { _, _, position, _ ->
            if (!isTtsInitialized || tts == null || !::languageArrayAdapter.isInitialized || position < 0 || position >= availableLanguages.size) {
                Log.w("TTSFragment", "Language selection ignored: Prerequisites not met or invalid position.")
                return@setOnItemClickListener
            }
            val newlySelectedLocale = availableLanguages[position]
            if (newlySelectedLocale != selectedLocale) {
                Log.d("TTSFragment", "Language selected: ${newlySelectedLocale.displayName}")
                setLanguage(newlySelectedLocale, forceUpdate = true) // Force update, reset playback
            }
        }
    }

    private fun setLanguage(locale: Locale, forceUpdate: Boolean) {
        if (!isTtsInitialized || tts == null || context == null) {
            Log.w("TTSFragment", "setLanguage precondition failed.")
            return
        }
        if (locale == selectedLocale && !forceUpdate) {
            Log.d("TTSFragment", "setLanguage: Locale unchanged and not forced.")
            if (_binding != null && binding.autoCompleteTextViewLanguage.text.toString() != locale.displayName) {
                isPdfSelectionListenerActive = false
                binding.autoCompleteTextViewLanguage.setText(locale.displayName, false)
                isPdfSelectionListenerActive = true
            }
            return
        }

        Log.i("TTSFragment", "Setting language to ${locale.displayName}. Force: $forceUpdate")
        resetPlaybackState(clearFile = true) // Language change requires re-synthesis

        val result = tts?.setLanguage(locale)
        Log.d("TTSFragment", "tts.setLanguage result code: $result")

        if (_binding == null) {
            Log.w("TTSFragment", "Binding became null during setLanguage.")
            return
        }

        var languageSetSuccessfully = false
        when (result) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                Toast.makeText(requireContext(), "'${locale.displayName}' not fully supported.", Toast.LENGTH_SHORT).show()
                Log.w("TTSFragment", "Language ${locale.displayName} not supported/missing data.")
                tts?.setLanguage(selectedLocale) // Revert engine
                isPdfSelectionListenerActive = false
                binding.autoCompleteTextViewLanguage.setText(selectedLocale.displayName, false) // Revert UI
                isPdfSelectionListenerActive = true
            }
            TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                Log.i("TTSFragment", "Language successfully set to: ${locale.displayName}")
                selectedLocale = locale // Update the current locale state
                viewModel.selectedLanguageTag = locale.toLanguageTag() // Save to ViewModel
                if (binding.autoCompleteTextViewLanguage.text.toString() != locale.displayName) {
                    isPdfSelectionListenerActive = false
                    binding.autoCompleteTextViewLanguage.setText(locale.displayName, false)
                    isPdfSelectionListenerActive = true
                }
                languageSetSuccessfully = true
            }
            else -> { // Other errors
                Toast.makeText(requireContext(), "Error setting language.", Toast.LENGTH_SHORT).show()
                Log.e("TTSFragment", "Unknown error setting language. Result: $result.")
                tts?.setLanguage(selectedLocale) // Revert engine
                isPdfSelectionListenerActive = false
                binding.autoCompleteTextViewLanguage.setText(selectedLocale.displayName, false) // Revert UI
                isPdfSelectionListenerActive = true
            }
        }
        updatePlaybackButtonState()
    }


    // --- TTS Rate/Pitch ---
    // (No changes needed in this section from previous version)
    private fun updateSpeechRate(rate: Float) {
        if (!isTtsInitialized || tts == null) return
        val result = tts?.setSpeechRate(rate)
        if (result == TextToSpeech.ERROR) Log.e("TTSFragment", "Error setting speech rate to $rate")
        else Log.d("TTSFragment", "Speech rate set to $rate")
    }

    private fun updateSpeechPitch(pitch: Float) {
        if (!isTtsInitialized || tts == null) return
        val result = tts?.setPitch(pitch)
        if (result == TextToSpeech.ERROR) Log.e("TTSFragment", "Error setting pitch to $pitch")
        else Log.d("TTSFragment", "Speech pitch set to $pitch")
    }

    // --- TTS Listener ---
    // (No changes needed in this section from previous version)
    private fun setupTtsListener() {
        if (tts == null) {
            Log.w("TTSFragment", "setupTtsListener: TTS is null.")
            return
        }
        val listenerResult = tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { }

            override fun onDone(id: String?) {
                if (id == synthesisUtteranceId) {
                    Log.i("TTSFragment", "TTS synthesis onDone: $id")
                    activity?.runOnUiThread {
                        if (!isAdded || _binding == null) return@runOnUiThread
                        if (viewModel.playbackState == PlaybackState.SYNTHESIZING && currentAudioFile != null && currentAudioFile!!.exists()) {
                            Log.d("TTSFragment", "Synthesis done, preparing MediaPlayer.")
                            prepareMediaPlayer(currentAudioFile!!)
                        } else {
                            Log.w("TTSFragment", "Synthesis onDone but state wrong or file missing. State: ${viewModel.playbackState}, File: ${currentAudioFile?.absolutePath}")
                            resetPlaybackState(clearFile = true)
                            updatePlaybackButtonState()
                            binding.progressBarSynthesis.isVisible = false
                        }
                    }
                }
            }

            @Deprecated("Deprecated in API level 21", ReplaceWith("onError(id, errorCode)"))
            override fun onError(id: String?) {
                if (id == synthesisUtteranceId) {
                    Log.e("TTSFragment", "TTS synthesis onError (deprecated): $id")
                    handleSynthesisError("Synthesis Error (Legacy)")
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == synthesisUtteranceId) {
                    Log.e("TTSFragment", "TTS synthesis onError: $id, Code: $errorCode")
                    handleSynthesisError("TTS Synthesis Error ($errorCode)")
                }
            }

            private fun handleSynthesisError(message: String) {
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    Log.e("TTSFragment", "Handling synthesis error: $message")
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    viewModel.synthesisFailed = true
                    resetPlaybackState(clearFile = true)
                    updatePlaybackButtonState()
                    binding.progressBarSynthesis.isVisible = false
                }
            }
        })

        if (listenerResult == TextToSpeech.ERROR) {
            Log.e("TTSFragment", "Failed to set UtteranceProgressListener.")
            Toast.makeText(context, "Critical Error: Cannot monitor TTS synthesis.", Toast.LENGTH_LONG).show()
        } else {
            Log.d("TTSFragment", "UtteranceProgressListener set successfully.")
        }
    }

    // --- PDF Handling ---

    private fun getPdfDirectory(): File? {
        return context?.let { File(it.filesDir, PDF_DIRECTORY_NAME) }
    }

    private fun findPdfFiles() {
        if (_binding == null || context == null) return

        val pdfDir = getPdfDirectory()
        if (pdfDir == null || !pdfDir.exists() || !pdfDir.isDirectory) {
            Log.w("TTSFragment", "PDF directory '$PDF_DIRECTORY_NAME' not found or not a directory.")
            viewModel.availablePdfFiles = emptyList()
            updatePdfDropdownAdapter() // Update adapter with empty list
            return
        }

        try {
            val files = pdfDir.listFiles { _, name -> name.endsWith(".pdf", ignoreCase = true) }
            viewModel.availablePdfFiles = files?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
            Log.d("TTSFragment", "Found ${viewModel.availablePdfFiles.size} PDF files in ${pdfDir.absolutePath}")
        } catch (e: SecurityException) {
            Log.e("TTSFragment", "Security error accessing PDF directory", e)
            Toast.makeText(context, "Error accessing PDF directory.", Toast.LENGTH_SHORT).show()
            viewModel.availablePdfFiles = emptyList()
        } catch (e: Exception) {
            Log.e("TTSFragment", "Error listing PDF files", e)
            Toast.makeText(context, "Error finding PDF files.", Toast.LENGTH_SHORT).show()
            viewModel.availablePdfFiles = emptyList()
        }
        updatePdfDropdownAdapter()
    }

    private fun updatePdfDropdownAdapter() {
        if (!::pdfArrayAdapter.isInitialized || _binding == null) return

        val pdfNames = viewModel.availablePdfFiles.map { it.name }
        // Store current selection text before clearing
        val currentSelectionText = binding.autoCompleteTextViewPdf.text?.toString()

        pdfArrayAdapter.clear()
        if (pdfNames.isNotEmpty()) {
            pdfArrayAdapter.addAll(pdfNames)
        }
        // It's generally better to notify after potential changes
        pdfArrayAdapter.notifyDataSetChanged()


        // Restore selection text if it's still a valid PDF name
        // Only restore if the adapter actually has items now
        if (!currentSelectionText.isNullOrEmpty() && pdfNames.contains(currentSelectionText)) {
            isPdfSelectionListenerActive = false // Prevent listener firing
            binding.autoCompleteTextViewPdf.setText(currentSelectionText, false)
            isPdfSelectionListenerActive = true
        } else if (!currentSelectionText.isNullOrEmpty()) {
            // Clear selection if the previously selected file is gone or list is empty
            isPdfSelectionListenerActive = false
            binding.autoCompleteTextViewPdf.setText("", false)
            if (viewModel.selectedPdfPath != null && File(viewModel.selectedPdfPath!!).name == currentSelectionText) {
                viewModel.selectedPdfPath = null // Clear viewmodel path only if it matched cleared text
            }
            isPdfSelectionListenerActive = true
        }


        // Enable/disable dropdown based on availability
        val hasPdfs = pdfNames.isNotEmpty()
        binding.pdfSelectorInputLayout.isEnabled = hasPdfs
        binding.autoCompleteTextViewPdf.isEnabled = hasPdfs
        if (!hasPdfs && isAdded) {
            binding.autoCompleteTextViewPdf.setText("", false) // Clear text if no PDFs
            Toast.makeText(context, "No scanned PDFs found in '$PDF_DIRECTORY_NAME'.", Toast.LENGTH_SHORT).show()
        }
        Log.d("TTSFragment", "PDF Dropdown updated. Count: ${pdfNames.size}, Enabled: $hasPdfs")
        // Enable/disable general UI based on state
        setUIEnabled(isTtsInitialized) // Re-evaluate UI enabled state
    }

    private fun setupPdfSelection() {
        if (_binding == null) return
        // Adapter initialized in setupUI before findPdfFiles is called

        binding.autoCompleteTextViewPdf.setOnItemClickListener { adapterView, _, position, _ ->
            if (!isPdfSelectionListenerActive) { // Prevent action during programmatic changes
                Log.d("TTSFragment","PDF selection listener skipped (inactive).")
                return@setOnItemClickListener
            }
            // Double check adapter and position validity
            val adapter = adapterView.adapter
            if (adapter == null || position < 0 || position >= adapter.count || position >= viewModel.availablePdfFiles.size) {
                Log.w("TTSFragment", "PDF selection ignored: Adapter invalid or position out of bounds ($position).")
                return@setOnItemClickListener
            }

            val selectedFile = viewModel.availablePdfFiles[position] // Get File object directly
            Log.d("TTSFragment", "PDF selected via dropdown: ${selectedFile.name}")

            // Check if selection actually changed to avoid redundant loads
            if (viewModel.selectedPdfPath != selectedFile.absolutePath) {
                viewModel.selectedPdfPath = selectedFile.absolutePath
                viewModel.pdfExtractionError = null // Clear previous error
                viewModel.synthesisFailed = false // Reset TTS synthesis error state
                resetPlaybackState(clearFile = true) // Reset TTS/MediaPlayer state
                loadTextFromSelectedPdf(selectedFile) // Load the new content

                // Optionally dismiss the dropdown after selection
                binding.autoCompleteTextViewPdf.dismissDropDown()
                // Optionally clear focus from dropdown
                // binding.autoCompleteTextViewPdf.clearFocus()

            } else {
                Log.d("TTSFragment", "PDF selection unchanged, not reloading.")
                // Ensure the dropdown text is correctly displayed even if selection didn't change logic-wise
                if (binding.autoCompleteTextViewPdf.text.toString() != selectedFile.name) {
                    isPdfSelectionListenerActive = false
                    binding.autoCompleteTextViewPdf.setText(selectedFile.name, false)
                    isPdfSelectionListenerActive = true
                }
                // Dismiss dropdown even if selection didn't change logic
                binding.autoCompleteTextViewPdf.dismissDropDown()
            }
        }
    }


    private fun loadTextFromSelectedPdf(pdfFile: File) {
        if (_binding == null || context == null) return

        viewModel.playbackState = PlaybackState.EXTRACTING_PDF
        updatePlaybackButtonState() // Show "Loading PDF..."
        setUIEnabled(false) // Disable controls during loading
        binding.progressBarPdfLoading.isVisible = true
        binding.progressBarSynthesis.isVisible = false

        // Perform extraction in background
        viewLifecycleOwner.lifecycleScope.launch {
            val result = extractTextFromPdf(pdfFile)

            // Update UI on main thread
            if (!isAdded || _binding == null) return@launch // Check if fragment still exists

            binding.progressBarPdfLoading.isVisible = false
            // Note: setUIEnabled will be called after text is set below,
            // which might trigger TextChangedListener -> resetPlaybackState -> updatePlaybackButtonState

            if (result.isSuccess) {
                val extractedText = result.getOrNull() ?: ""
                Log.i("TTSFragment", "PDF text extracted successfully (${extractedText.length} chars). File: ${pdfFile.name}")
                viewModel.pdfExtractionError = null // Clear error on success

                // Set flag before setting text
                isSettingTextProgrammatically = true
                Log.d("TTSFragment", "Set isSettingTextProgrammatically to true before setText")
                viewModel.currentText = extractedText // Update ViewModel's text *before* UI
                binding.editTextToSpeak.setText(extractedText) // Update UI EditText (triggers listener)
                // Flag is reset inside the listener now

                viewModel.playbackState = PlaybackState.IDLE // Ready to speak (listener called resetPlaybackState)
                if(isAdded) Toast.makeText(context, "PDF loaded.", Toast.LENGTH_SHORT).show()

            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown PDF extraction error."
                Log.e("TTSFragment", "Failed to extract text from PDF: ${pdfFile.name}", result.exceptionOrNull())
                if(isAdded) Toast.makeText(context, "Error loading PDF: $errorMsg", Toast.LENGTH_LONG).show()

                // Set flag before clearing text
                isSettingTextProgrammatically = true
                Log.d("TTSFragment", "Set isSettingTextProgrammatically to true before clearing text on error")
                viewModel.currentText = "" // Clear text on error
                binding.editTextToSpeak.setText("") // Triggers listener
                // Flag is reset inside the listener now

                viewModel.playbackState = PlaybackState.IDLE // Back to idle, but with error
                viewModel.pdfExtractionError = errorMsg // Store error
            }
            // Re-enable UI and update button state *after* text processing is complete
            setUIEnabled(true)
            updatePlaybackButtonState()
        }
    }


    // Extracts text using iText7 - runs on IO dispatcher
    private suspend fun extractTextFromPdf(pdfFile: File): Result<String> = withContext(Dispatchers.IO) {
        var pdfReader: PdfReader? = null
        var pdfDocument: PdfDocument? = null
        try {
            if (!pdfFile.exists()) {
                throw FileNotFoundException("PDF file not found: ${pdfFile.absolutePath}")
            }
            if (!pdfFile.canRead()) {
                throw IOException("Cannot read PDF file (permissions?): ${pdfFile.absolutePath}")
            }

            Log.d("TTSFragment_iText", "Attempting to open PDF: ${pdfFile.absolutePath}")
            pdfReader = PdfReader(pdfFile)
            pdfDocument = PdfDocument(pdfReader)
            val numPages = pdfDocument.numberOfPages
            if (numPages == 0) {
                Log.w("TTSFragment_iText", "PDF has 0 pages: ${pdfFile.name}")
                return@withContext Result.success("") // No text if no pages
            }

            val extractedText = StringBuilder()
            Log.d("TTSFragment_iText", "Reading $numPages pages from ${pdfFile.name}...")

            for (i in 1..numPages) {
                try {
                    val page = pdfDocument.getPage(i)
                    // Using LocationTextExtractionStrategy often gives better results for reading order
                    val strategy = LocationTextExtractionStrategy()
                    val pageText = PdfTextExtractor.getTextFromPage(page, strategy)
                    extractedText.append(pageText)
                    if (i < numPages) extractedText.append("\n\n") // Add separator between pages
                    // Add logs for progress if needed: Log.v("TTSFragment_iText", "Read page $i")
                } catch (e: Exception) {
                    Log.e("TTSFragment_iText", "Error reading page $i from ${pdfFile.name}", e)
                    // Decide whether to continue or fail entirely
                    extractedText.append("\n--- Error reading page $i ---\n") // Option: mark errors
                    // Option: Fail fast -> throw IOException("Error processing page $i", e)
                }
            }
            Log.d("TTSFragment_iText", "Finished reading PDF: ${pdfFile.name}. Text length: ${extractedText.length}")
            Result.success(extractedText.toString())

        } catch (fnf: FileNotFoundException) {
            Log.e("TTSFragment_iText", "File not found exception", fnf)
            Result.failure(IOException("PDF file not found.", fnf))
        } catch (ioe: IOException) {
            Log.e("TTSFragment_iText", "IO exception reading PDF", ioe)
            Result.failure(ioe) // Keep specific IO error
        } catch (e: Exception) {
            // Catch potential iText-specific errors or other issues
            Log.e("TTSFragment_iText", "General error extracting PDF text from ${pdfFile.name}", e)
            Result.failure(IOException("Failed to process PDF: ${e.message}", e))
        } finally {
            try {
                pdfDocument?.close()
                Log.d("TTSFragment_iText", "Closed PdfDocument for ${pdfFile.name}")
            } catch (e: Exception) { Log.e("TTSFragment_iText", "Error closing PdfDocument", e) }
            try {
                pdfReader?.close()
                Log.d("TTSFragment_iText", "Closed PdfReader for ${pdfFile.name}")
            } catch (e: Exception) { Log.e("TTSFragment_iText", "Error closing PdfReader", e) }
        }
    }
}