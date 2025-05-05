package com.example.mc.tts_fragment // Adjust package name if needed

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.mc.databinding.FragmentTextToSpeechBinding
import com.example.mc.tts_fragment.viewModel.TextToSpeechViewModel
import java.util.*

class TextToSpeechFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentTextToSpeechBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TextToSpeechViewModel by activityViewModels()

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var availableLanguages: List<Locale> = listOf()

    private lateinit var selectedLocale: Locale
    private lateinit var languageArrayAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextToSpeechBinding.inflate(inflater, container, false)
        restoreSelectedLocaleFromViewModel()
        if (tts == null) {
            tts = TextToSpeech(requireContext().applicationContext, this)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        restoreUiState()
        setUIEnabled(false)

        if (isTtsInitialized && tts != null) {
            populateLanguageDropdown()
            setupLanguageSelectionListener()
            updateSpeechRate(binding.sliderRate.value)
            updateSpeechPitch(binding.sliderPitch.value)
            setLanguage(selectedLocale)
            setupUtteranceListener()
            setUIEnabled(true)
        }
    }

    private fun restoreSelectedLocaleFromViewModel() {
        val savedTag = viewModel.selectedLanguageTag
        try {
            selectedLocale = Locale.forLanguageTag(savedTag)
        } catch (e: Exception) {
            selectedLocale = Locale.US // Fallback
            viewModel.selectedLanguageTag = selectedLocale.toLanguageTag()
        }
    }

    override fun onDestroyView() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsInitialized = false
        _binding = null
        super.onDestroyView()
    }

    override fun onInit(status: Int) {
        if (!isAdded || _binding == null || context == null) {
            tts = null
            isTtsInitialized = false
            return
        }

        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            populateLanguageDropdown()
            setupLanguageSelectionListener()
            updateSpeechRate(binding.sliderRate.value)
            updateSpeechPitch(binding.sliderPitch.value)
            setLanguage(selectedLocale)
            setUIEnabled(true)
            setupUtteranceListener()
        } else {
            Toast.makeText(requireContext(), "TTS Initialization Failed.", Toast.LENGTH_LONG).show()
            setUIEnabled(false)
            isTtsInitialized = false
            tts = null
        }
    }

    private fun setupUI() {
        if (_binding == null) return

        binding.buttonSpeak.setOnClickListener { speakOut() }

        binding.sliderRate.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                if (isTtsInitialized) updateSpeechRate(value)
                viewModel.currentRate = value
            }
        }

        binding.sliderPitch.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                if (isTtsInitialized) updateSpeechPitch(value)
                viewModel.currentPitch = value
            }
        }

        binding.editTextToSpeak.addTextChangedListener { text ->
            viewModel.currentText = text?.toString() ?: ""
            _binding?.let { b ->
                b.buttonSpeak.isEnabled = isTtsInitialized && b.editTextToSpeak.text?.isNotEmpty() == true
            }
        }
    }

    private fun restoreUiState() {
        if (_binding == null) return

        binding.editTextToSpeak.setText(viewModel.currentText)
        binding.sliderRate.value = viewModel.currentRate
        binding.sliderPitch.value = viewModel.currentPitch

        if (isTtsInitialized && tts != null) {
            updateSpeechRate(viewModel.currentRate)
            updateSpeechPitch(viewModel.currentPitch)
        }
    }

    private fun setUIEnabled(isEnabled: Boolean) {
        _binding?.let { b ->
            val canSpeak = isEnabled && b.editTextToSpeak.text?.isNotEmpty() == true
            b.buttonSpeak.isEnabled = canSpeak
            b.editTextToSpeak.isEnabled = isEnabled
            b.sliderRate.isEnabled = isEnabled
            b.sliderPitch.isEnabled = isEnabled
            b.languageInputLayout.isEnabled = isEnabled
            b.autoCompleteTextViewLanguage.isEnabled = isEnabled
            b.controlsCard.alpha = if (isEnabled) 1.0f else 0.5f
        }
    }

    private fun populateLanguageDropdown() {
        if (!isTtsInitialized || tts == null || context == null || _binding == null) {
            return
        }

        availableLanguages = try {
            tts?.availableLanguages?.filterNotNull()?.sortedBy { it.displayName } ?: listOf()
        } catch (e: Exception) {
            listOf()
        }

        if (availableLanguages.isEmpty()) {
            Toast.makeText(requireContext(), "No TTS languages found on device.", Toast.LENGTH_LONG).show()
            binding.languageInputLayout.isEnabled = false
            try { binding.autoCompleteTextViewLanguage.setAdapter(null) }
            catch (e: IllegalStateException) { /* Ignore */ }
            return
        }

        val languageNames = availableLanguages.map { it.displayName }
        languageArrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, languageNames)
        binding.autoCompleteTextViewLanguage.setAdapter(languageArrayAdapter)

        val currentLocaleDisplayName = selectedLocale.displayName
        val initialIndex = availableLanguages.indexOfFirst { it == selectedLocale }

        if (initialIndex >= 0) {
            binding.autoCompleteTextViewLanguage.setText(currentLocaleDisplayName, false)
        } else {
            if (availableLanguages.isNotEmpty()) {
                selectedLocale = availableLanguages[0]
                viewModel.selectedLanguageTag = selectedLocale.toLanguageTag()
                binding.autoCompleteTextViewLanguage.setText(selectedLocale.displayName, false)
                setLanguage(selectedLocale)
            } else {
                binding.languageInputLayout.isEnabled = false
            }
        }
        binding.languageInputLayout.isEnabled = true
    }

    private fun setupLanguageSelectionListener() {
        if (_binding == null) return

        binding.autoCompleteTextViewLanguage.setOnItemClickListener { _, _, position, _ ->
            if (!isTtsInitialized || tts == null || !::languageArrayAdapter.isInitialized) {
                return@setOnItemClickListener
            }
            if (position < 0 || position >= availableLanguages.size) {
                return@setOnItemClickListener
            }
            val newlySelectedLocale = availableLanguages[position]
            if (newlySelectedLocale != selectedLocale) {
                setLanguage(newlySelectedLocale)
            }
        }
    }

    private fun setLanguage(locale: Locale) {
        if (!isTtsInitialized || tts == null || context == null) {
            return
        }
        val result = tts?.setLanguage(locale)
        if (_binding == null) {
            return
        }

        when (result) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                Toast.makeText(requireContext(), "Language '${locale.displayName}' not fully supported.", Toast.LENGTH_SHORT).show()
                val previousValidDisplayName = selectedLocale.displayName
                if (::languageArrayAdapter.isInitialized) {
                    binding.autoCompleteTextViewLanguage.setText(previousValidDisplayName, false)
                    tts?.setLanguage(selectedLocale)
                }
                setUIEnabled(isTtsInitialized)
            }
            TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                selectedLocale = locale
                viewModel.selectedLanguageTag = locale.toLanguageTag()
                if (binding.autoCompleteTextViewLanguage.text.toString() != locale.displayName) {
                    binding.autoCompleteTextViewLanguage.setText(locale.displayName, false)
                }
                setUIEnabled(isTtsInitialized)
            }
            else -> {
                Toast.makeText(requireContext(), "Error setting language.", Toast.LENGTH_SHORT).show()
                val previousValidDisplayName = selectedLocale.displayName
                if (::languageArrayAdapter.isInitialized) {
                    binding.autoCompleteTextViewLanguage.setText(previousValidDisplayName, false)
                    tts?.setLanguage(selectedLocale)
                }
                setUIEnabled(isTtsInitialized)
            }
        }
    }

    private fun updateSpeechRate(rate: Float) {
        if (!isTtsInitialized || tts == null) return
        tts?.setSpeechRate(rate)
    }

    private fun updateSpeechPitch(pitch: Float) {
        if (!isTtsInitialized || tts == null) return
        tts?.setPitch(pitch)
    }

    private fun setupUtteranceListener() {
        if (tts == null) return
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { activity?.runOnUiThread { _binding?.let { it.buttonSpeak.isEnabled = false } } }
            override fun onDone(utteranceId: String?) { activity?.runOnUiThread { _binding?.let { it.buttonSpeak.isEnabled = isTtsInitialized && it.editTextToSpeak.text?.isNotEmpty() == true } } }
            @Deprecated("Deprecated in API level 21") override fun onError(utteranceId: String?) { handleSpeechError(utteranceId, TextToSpeech.ERROR) }
            override fun onError(utteranceId: String?, errorCode: Int) { handleSpeechError(utteranceId, errorCode) }
            private fun handleSpeechError(utteranceId: String?, errorCode: Int) {
                activity?.runOnUiThread {
                    _binding?.let {
                        it.buttonSpeak.isEnabled = isTtsInitialized && it.editTextToSpeak.text?.isNotEmpty() == true
                        Toast.makeText(context, "TTS Error: $errorCode", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun speakOut() {
        if (!isTtsInitialized || tts == null) {
            Toast.makeText(requireContext(), "TTS is not ready yet.", Toast.LENGTH_SHORT).show()
            if (tts == null && context != null && !isTtsInitialized) {
                tts = TextToSpeech(requireContext().applicationContext, this)
            }
            return
        }
        val text = binding.editTextToSpeak.text?.toString()?.trim() ?: ""
        if (text.isNotEmpty()) {
            val utteranceId = UUID.randomUUID().toString()
            val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                Toast.makeText(requireContext(), "Error starting speech.", Toast.LENGTH_SHORT).show()
                _binding?.let { it.buttonSpeak.isEnabled = isTtsInitialized && it.editTextToSpeak.text?.isNotEmpty() == true }
            }
        } else {
            Toast.makeText(requireContext(), "Please enter text to speak.", Toast.LENGTH_SHORT).show()
            _binding?.editTextToSpeak?.requestFocus()
        }
    }
}