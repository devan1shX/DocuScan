package com.example.mc.tts_fragment.viewModel // Adjust package name if needed

import androidx.lifecycle.ViewModel
import java.io.File // Added
import java.util.Locale

// Define playback states for clarity
enum class PlaybackState {
    IDLE,       // Nothing happening, ready to start synthesis or load PDF
    EXTRACTING_PDF, // Added: PDF text extraction in progress
    SYNTHESIZING, // TTS is converting text to audio file
    PREPARING,  // MediaPlayer is loading the audio file
    PLAYING,    // MediaPlayer is actively playing
    PAUSED      // MediaPlayer is paused
}

class TextToSpeechViewModel : ViewModel() {
    var currentText: String = ""
    var currentRate: Float = 1.0f
    var currentPitch: Float = 1.0f
    var selectedLanguageTag: String = Locale.US.toLanguageTag()

    // --- State Variables for MediaPlayer & TTS Synthesis ---
    var playbackState: PlaybackState = PlaybackState.IDLE
    var audioFilePath: String? = null // Path to the synthesized audio file
    var lastPlaybackPosition: Int = 0 // Position to resume from (in milliseconds)
    var synthesisFailed: Boolean = false // Track if TTS synthesis errored

    // --- State Variables for PDF Handling ---
    var availablePdfFiles: List<File> = emptyList()
    var selectedPdfPath: String? = null // Store the path of the selected PDF
    var pdfExtractionError: String? = null // Store error message from PDF extraction

    // Reset relevant state when text/params change significantly
    fun resetPlaybackAndPdfSelection() {
        playbackState = PlaybackState.IDLE
        lastPlaybackPosition = 0
        synthesisFailed = false
        pdfExtractionError = null
        // Don't clear selectedPdfPath here, let the UI handle clearing the dropdown text
        // audioFilePath should be cleared separately when needed (e.g., by cleanupAudioFile)
    }

    // Reset only TTS playback state (e.g., after completion/error)
    fun resetTtsPlaybackOnly() {
        if (playbackState != PlaybackState.EXTRACTING_PDF) { // Don't reset if PDF is loading
            playbackState = PlaybackState.IDLE
        }
        lastPlaybackPosition = 0
        synthesisFailed = false
        // Don't clear PDF state
    }
}