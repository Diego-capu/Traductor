package com.antigravity.translator.overlay

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.antigravity.translator.domain.model.TranslatedBlock

import com.antigravity.translator.tts.TtsManager

/**
 * Large modal dialog that pops up when the reader taps any translated speech bubble.
 * Displays the translation in large, comfortable typography along with the original text.
 * Integrates Text-to-Speech (TTS) and independent clipboard copy for both translated and original text.
 */
@SuppressLint("SetTextI18n")
class TranslationDetailDialog(
    private val context: Context,
    private val windowManager: WindowManager,
    private val ttsManager: TtsManager? = null,
    private val getTargetLanguage: (() -> String)? = null,
    private val getSourceLanguage: (() -> String)? = null
) {
    private var rootContainer: FrameLayout? = null

    fun show(block: TranslatedBlock) {
        dismiss()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        // Semi-transparent scrim backdrop
        val backdrop = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
            setOnClickListener { dismiss() }
        }

        // Center card container
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFFDFD"))
                cornerRadius = 32f
                setStroke(3, Color.parseColor("#1A1A1A"))
            }
            elevation = 24f
            // Prevent clicks inside card from dismissing
            isClickable = true
        }

        val cardParams = FrameLayout.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.88).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // 1. Header Row
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(context).apply {
            text = "Traducción del Bocadillo"
            textSize = 14f
            setTextColor(Color.parseColor("#6200EE"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = p
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.parseColor("#757575"))
            setPadding(16, 8, 16, 8)
            setOnClickListener { dismiss() }
        }

        headerRow.addView(titleText)
        headerRow.addView(closeBtn)

        // 2. Large Translated Text Box
        val translatedText = TextView(context).apply {
            text = block.translatedText.ifEmpty { block.originalText }
            textSize = 20f
            setTextColor(Color.parseColor("#0A0A0A"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setLineSpacing(6f, 1.2f)
            setPadding(0, 16, 0, 16)
        }

        // 3. Original Text Sub-card
        val originalContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 14, 20, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F4F4F6"))
                cornerRadius = 16f
            }
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12
                bottomMargin = 20
            }
            layoutParams = p
        }

        val origHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val origLabel = TextView(context).apply {
            text = "ORIGINAL"
            textSize = 10f
            setTextColor(Color.parseColor("#8E8E93"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = p
        }

        val origTtsBtn = TextView(context).apply {
            text = "🔊 Escuchar"
            textSize = 11f
            setTextColor(Color.parseColor("#6200EE"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(12, 4, 12, 4)
            setOnClickListener {
                val srcLang = getSourceLanguage?.invoke() ?: "EN"
                ttsManager?.speak(block.originalText, srcLang)
            }
        }

        val origCopyBtn = TextView(context).apply {
            text = "📋 Copiar"
            textSize = 11f
            setTextColor(Color.parseColor("#6200EE"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(12, 4, 12, 4)
            setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Texto Original", block.originalText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Texto original copiado", Toast.LENGTH_SHORT).show()
            }
        }

        origHeaderRow.addView(origLabel)
        origHeaderRow.addView(origTtsBtn)
        origHeaderRow.addView(origCopyBtn)

        val origContent = TextView(context).apply {
            text = block.originalText
            textSize = 13f
            setTextColor(Color.parseColor("#424242"))
            setLineSpacing(4f, 1.1f)
            setPadding(0, 4, 0, 0)
        }

        originalContainer.addView(origHeaderRow)
        originalContainer.addView(origContent)

        // 4. Action Buttons Row
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val ttsSpeakBtn = TextView(context).apply {
            text = "🔊 Escuchar"
            textSize = 13f
            setTextColor(Color.parseColor("#00838F"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(20, 12, 20, 12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A00E5FF"))
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#00ACC1"))
            }
            setOnClickListener {
                val targetLang = getTargetLanguage?.invoke() ?: "ES"
                val textToSpeak = block.translatedText.ifEmpty { block.originalText }
                ttsManager?.speak(textToSpeak, targetLang)
            }
        }

        val copyBtn = TextView(context).apply {
            text = "📋 Copiar"
            textSize = 13f
            setTextColor(Color.parseColor("#6200EE"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(20, 12, 20, 12)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8
            }
            layoutParams = p
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A6200EE"))
                cornerRadius = 20f
            }
            setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Traducción Manga", block.translatedText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Traducción copiada al portapapeles", Toast.LENGTH_SHORT).show()
            }
        }

        val spacer = View(context).apply {
            val p = LinearLayout.LayoutParams(0, 1, 1f)
            layoutParams = p
        }

        val okBtn = TextView(context).apply {
            text = "Listo"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(24, 12, 24, 12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#6200EE"))
                cornerRadius = 20f
            }
            setOnClickListener {
                ttsManager?.stop()
                dismiss()
            }
        }

        actionRow.addView(ttsSpeakBtn)
        actionRow.addView(copyBtn)
        actionRow.addView(spacer)
        actionRow.addView(okBtn)

        val scrollContainer = ScrollView(context).apply {
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = p
            addView(translatedText)
        }

        cardLayout.addView(headerRow)
        cardLayout.addView(scrollContainer)
        cardLayout.addView(originalContainer)
        cardLayout.addView(actionRow)

        backdrop.addView(cardLayout, cardParams)
        rootContainer = backdrop

        try {
            windowManager.addView(backdrop, params)
        } catch (e: Exception) {
            // Error handling
        }
    }

    fun dismiss() {
        rootContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore if not attached
            }
            rootContainer = null
        }
    }
}
