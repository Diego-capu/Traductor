package com.antigravity.translator.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import java.util.Locale

/**
 * Clean wrapper for Android's native TextToSpeech engine.
 *
 * Handles:
 * - Asynchronous initialization with a pending speech queue.
 * - Dynamic language switching between target and source languages (Spanish, Japanese, English, Korean, etc.).
 * - Explicit detection of LANG_MISSING_DATA and LANG_NOT_SUPPORTED with user toast notifications.
 * - Resource cleanup on service termination.
 */
class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val tag = "TtsManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeech: Pair<String, String>? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to instantiate TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            Log.d(tag, "TextToSpeech engine successfully initialized")
            // Process any queued speech request that was triggered while initializing
            pendingSpeech?.let { (text, lang) ->
                speak(text, lang)
                pendingSpeech = null
            }
        } else {
            isInitialized = false
            Log.e(tag, "Failed to initialize TextToSpeech engine. Status: $status")
            showToast("No se pudo inicializar el motor de síntesis de voz (TTS)")
        }
    }

    /**
     * Speaks the given text aloud in the specified language.
     *
     * @param text The text string to synthesize.
     * @param languageCode Two-letter language code (e.g., "ES", "EN", "JA", "KO").
     */
    fun speak(text: String, languageCode: String = "ES") {
        if (text.isBlank()) return

        if (!isInitialized) {
            Log.d(tag, "TTS not ready yet, queuing speech request")
            pendingSpeech = Pair(text, languageCode)
            return
        }

        val ttsEngine = tts ?: return
        val targetLocale = mapLanguageCodeToLocale(languageCode)

        try {
            val result = ttsEngine.setLanguage(targetLocale)
            when (result) {
                TextToSpeech.LANG_MISSING_DATA -> {
                    Log.w(tag, "TTS missing voice data for locale: $targetLocale")
                    showToast("Falta el paquete de voz para ${targetLocale.displayLanguage}. Instálalo en Ajustes de Texto a Voz.")
                    // Fallback to default locale
                    ttsEngine.setLanguage(Locale.getDefault())
                }
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.w(tag, "TTS language not supported for locale: $targetLocale")
                    showToast("El idioma ${targetLocale.displayLanguage} no es soportado por el motor TTS del dispositivo.")
                    // Fallback to default locale
                    ttsEngine.setLanguage(Locale.getDefault())
                }
                else -> {
                    // Supported and available
                }
            }

            val utteranceId = "TTS_UTTERANCE_${System.currentTimeMillis()}"
            val params = Bundle()
            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e(tag, "Error during TTS playback", e)
        }
    }

    /**
     * Halts any currently playing speech synthesis.
     */
    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping TTS playback", e)
        }
    }

    /**
     * Shuts down and releases TTS engine resources.
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            Log.d(tag, "TextToSpeech engine shut down")
        } catch (e: Exception) {
            Log.w(tag, "Error shutting down TTS", e)
        }
    }

    /**
     * Maps ISO 639-1 / DeepL language codes to Java Locales.
     */
    private fun mapLanguageCodeToLocale(code: String): Locale {
        val cleanCode = code.trim().uppercase()
        return when (cleanCode) {
            "ES" -> Locale("es", "ES")
            "EN", "EN-US" -> Locale.US
            "EN-GB" -> Locale.UK
            "JA" -> Locale.JAPANESE
            "KO" -> Locale.KOREAN
            "ZH" -> Locale.SIMPLIFIED_CHINESE
            "FR" -> Locale.FRENCH
            "DE" -> Locale.GERMAN
            "IT" -> Locale.ITALIAN
            "PT", "PT-BR" -> Locale("pt", "BR")
            "PT-PT" -> Locale("pt", "PT")
            "RU" -> Locale("ru", "RU")
            else -> Locale.getDefault()
        }
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
