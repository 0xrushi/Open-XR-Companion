package com.inmoair.xrcompanion.client.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceState { IDLE, LISTENING, PROCESSING, RECOGNIZED, ERROR }

@Singleton
class VoiceInputManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val TAG = "VoiceInput"

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _lastText = MutableStateFlow("")
    val lastText: StateFlow<String> = _lastText.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    var onResult: ((String) -> Unit)? = null

    fun start() {
        if (_state.value == VoiceState.LISTENING) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceState.ERROR
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
        _state.value = VoiceState.LISTENING
        Log.d(TAG, "Listening started")
    }

    fun stop() {
        recognizer?.stopListening()
        _state.value = VoiceState.PROCESSING
    }

    fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        _state.value = VoiceState.IDLE
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { _state.value = VoiceState.LISTENING }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { _state.value = VoiceState.PROCESSING }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            Log.d(TAG, "Result: $text")
            _lastText.value = text
            _state.value = VoiceState.RECOGNIZED
            onResult?.invoke(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
            _lastText.value = partial
        }

        override fun onError(error: Int) {
            Log.e(TAG, "Recognition error: $error")
            _state.value = VoiceState.ERROR
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
