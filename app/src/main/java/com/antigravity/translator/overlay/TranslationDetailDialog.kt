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

        val density = context.resources.displayMetrics.density

        // Semi-transparent deep cyber scrim backdrop
        val backdrop = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#B30B0F19"))
            setOnClickListener { dismiss() }
        }

        // Center card container: Frosted glassmorphism with crisp Miku Teal border
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padH = (24 * density).toInt()
            val padV = (20 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F0171B24")) // 94% opacity dark cyber charcoal
                cornerRadius = 18f * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#39C5BB"))
            }
            elevation = 24f
            isClickable = true
        }

        val cardParams = FrameLayout.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.90).toInt(),
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
            text = "Miku_AI • Traducción"
            textSize = 13.5f
            setTextColor(Color.parseColor("#39C5BB"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = p
        }

        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.parseColor("#8F9BA8"))
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
            setOnClickListener { dismiss() }
        }

        headerRow.addView(titleText)
        headerRow.addView(closeBtn)

        // 2. Large Translated Text Box
        val translatedText = TextView(context).apply {
            text = block.translatedText.ifEmpty { block.originalText }
            textSize = 18.5f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setLineSpacing(5f * density, 1.15f)
            setPadding(0, (14 * density).toInt(), 0, (14 * density).toInt())
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }

        // 3. Original Text Sub-card
        val originalContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12151D"))
                cornerRadius = 12f * density
                setStroke((1f * density).toInt(), Color.parseColor("#2A3245"))
            }
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).toInt()
                bottomMargin = (16 * density).toInt()
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
            setTextColor(Color.parseColor("#8F9BA8"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = p
        }

        // Audio in Miku Accent Magenta
        val origTtsBtn = TextView(context).apply {
            text = "🔊 Escuchar"
            textSize = 11f
            setTextColor(Color.parseColor("#E040FB"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26E040FB"))
                cornerRadius = 10f * density
            }
            setOnClickListener {
                val srcLang = getSourceLanguage?.invoke() ?: "EN"
                ttsManager?.speak(block.originalText, srcLang)
            }
        }

        val origCopyBtn = TextView(context).apply {
            text = "📋 Copiar"
            textSize = 11f
            setTextColor(Color.parseColor("#39C5BB"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (6 * density).toInt()
            }
            layoutParams = p
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2639C5BB"))
                cornerRadius = 10f * density
            }
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
            setTextColor(Color.parseColor("#8F9BA8"))
            setLineSpacing(3f * density, 1.1f)
            setPadding(0, (6 * density).toInt(), 0, 0)
        }

        originalContainer.addView(origHeaderRow)
        originalContainer.addView(origContent)

        // 4. Action Buttons Row
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Primary TTS button styled in Miku Accent Magenta
        val ttsSpeakBtn = TextView(context).apply {
            text = "🔊 Escuchar"
            textSize = 12.5f
            setTextColor(Color.parseColor("#E040FB"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26E040FB"))
                cornerRadius = 14f * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#E040FB"))
            }
            setOnClickListener {
                val targetLang = getTargetLanguage?.invoke() ?: "ES"
                val textToSpeak = block.translatedText.ifEmpty { block.originalText }
                ttsManager?.speak(textToSpeak, targetLang)
            }
        }

        // Copy button in Miku Teal
        val copyBtn = TextView(context).apply {
            text = "📋 Copiar"
            textSize = 12.5f
            setTextColor(Color.parseColor("#39C5BB"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (8 * density).toInt()
            }
            layoutParams = p
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2639C5BB"))
                cornerRadius = 14f * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#39C5BB"))
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

        // Confirm "Listo" CTA
        val okBtn = TextView(context).apply {
            text = "Listo"
            textSize = 12.5f
            setTextColor(Color.parseColor("#0B1326"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((20 * density).toInt(), (10 * density).toInt(), (20 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#39C5BB"))
                cornerRadius = 14f * density
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
