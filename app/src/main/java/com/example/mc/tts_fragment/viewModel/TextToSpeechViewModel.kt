package com.example.mc.tts_fragment.viewModel

import androidx.lifecycle.ViewModel
import java.util.Locale

class TextToSpeechViewModel : ViewModel() {
    var currentText: String = ""
    var currentRate: Float = 1.0f
    var currentPitch: Float = 1.0f
    var selectedLanguageTag: String = Locale.US.toLanguageTag()

    override fun onCleared() {
        super.onCleared()
    }

    init {
    }
}
