package com.antigravity.translator.engine.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.antigravity.translator.domain.model.DetectedTextBlock
import com.antigravity.translator.domain.model.ReadingProfile
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-Device Optical Character Recognition engine powered by Google ML Kit.
 *
 * Supports dynamic switching between specialized CJK models (Japanese, Chinese, Korean)
 * and Latin/Default models, with fast ColorMatrix contrast enhancement and explicit native
 * memory cleanup on model transitions.
 */
class OcrEngine {

    private val tag = "OcrEngine"
    private var currentLangCode: String = ""
    private var activeProfile: ReadingProfile = ReadingProfile.MANGA_JA
    private var recognizer: TextRecognizer? = null
    private val recognizerLock = Any()
    @Volatile
    var lastOcrError: String? = null
        private set

    init {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Intelligently configures OCR based on user's selected language and reading medium.
     * When user selects "Auto-detect" (""), defaults to the optimal recognizer for the selected ReadingProfile.
     */
    fun configure(userSourceLang: String, profile: ReadingProfile) {
        activeProfile = profile
        val lang = userSourceLang.trim().uppercase()
        if (lang.isNotEmpty()) {
            val targetEngine = when (lang) {
                "JA" -> "JA"
                "KO" -> "KO"
                "ZH" -> "ZH"
                else -> "DEFAULT"
            }
            setSourceLanguage(targetEngine)
        } else {
            // Auto-detect / empty: use profile-specific recognizer directly
            setReadingProfile(profile)
        }
    }

    /**
     * Directly configures the optimal ML Kit recognizer for the selected ReadingProfile,
     * releasing native GPU/CPU memory from any previous instance.
     *
     * - MANGA_JA: JapaneseTextRecognizerOptions
     * - MANGA_EN: TextRecognizerOptions.DEFAULT_OPTIONS (faster, Latin-optimized)
     * - MANHWA: KoreanTextRecognizerOptions
     * - MANHUA: ChineseTextRecognizerOptions
     * - COMIC: TextRecognizerOptions.DEFAULT_OPTIONS
     */
    fun setReadingProfile(profile: ReadingProfile) {
        activeProfile = profile
        val targetEngine = when (profile) {
            ReadingProfile.MANGA_JA -> "JA"
            ReadingProfile.MANGA_EN -> "DEFAULT"
            ReadingProfile.MANHWA -> "KO"
            ReadingProfile.MANHUA -> "ZH"
            ReadingProfile.COMIC -> "DEFAULT"
        }
        synchronized(recognizerLock) {
            if (targetEngine == currentLangCode && recognizer != null) return

            try {
                recognizer?.close()
                Log.d(tag, "Closed previous TextRecognizer for $currentLangCode")
            } catch (e: Exception) {
                Log.w(tag, "Error closing previous TextRecognizer", e)
            }

            recognizer = when (profile) {
                ReadingProfile.MANGA_JA -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                ReadingProfile.MANGA_EN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                ReadingProfile.MANHWA -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                ReadingProfile.MANHUA -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                ReadingProfile.COMIC -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
            currentLangCode = targetEngine
            Log.d(tag, "Configured specialized TextRecognizer for profile ${profile.name} ($targetEngine)")
        }
    }

    /**
     * Dynamically updates the active ML Kit TextRecognizer according to source language.
     * Invokes previousRecognizer.close() immediately to release native GPU/CPU memory.
     */
    fun setSourceLanguage(langCode: String) {
        val normalized = langCode.trim().uppercase()
        synchronized(recognizerLock) {
            if (normalized == currentLangCode && recognizer != null) return

            try {
                recognizer?.close()
                Log.d(tag, "Closed previous TextRecognizer for $currentLangCode")
            } catch (e: Exception) {
                Log.w(tag, "Error closing previous TextRecognizer", e)
            }

            recognizer = when (normalized) {
                "JA" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                "ZH" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                "KO" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
            currentLangCode = normalized
            Log.d(tag, "Configured specialized TextRecognizer for $normalized")
        }
    }

    /**
     * Preprocesses manga frame buffer (soft grayscale + balanced +15% contrast)
     * to eliminate screentones and shadows without empasting dense kanji radicals.
     */
    private fun preprocessMangaBitmap(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)

        val contrast = 1.15f // Balanced +15% contrast to preserve delicate kanji strokes
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)

        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Checks if bitmap is completely black or blank (often due to DRM/FLAG_SECURE).
     */
    fun isBitmapBlank(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 10 || h < 10) return true
        val stepX = (w / 12).coerceAtLeast(1)
        val stepY = (h / 12).coerceAtLeast(1)
        var nonBlackCount = 0
        for (x in 0 until w step stepX) {
            for (y in 0 until h step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                val red = (pixel ushr 16) and 0xFF
                val green = (pixel ushr 8) and 0xFF
                val blue = pixel and 0xFF
                if (alpha > 0 && (red > 18 || green > 18 || blue > 18)) {
                    nonBlackCount++
                }
            }
        }
        return nonBlackCount == 0
    }

    private suspend fun runOcrPass(
        recognizerInstance: TextRecognizer,
        image: InputImage
    ): List<DetectedTextBlock> {
        return try {
            val visionText = recognizerInstance.process(image).await()
            val detectedBlocks = mutableListOf<DetectedTextBlock>()

            for (block in visionText.textBlocks) {
                val blockText = block.text.trim()
                val box = block.boundingBox

                if (blockText.isNotEmpty() && box != null && !box.isEmpty) {
                    val validRect = Rect(box.left, box.top, box.right, box.bottom)

                    // Calculate average physical font size, handling vertical Tategaki vs horizontal text
                    val lineHeights = block.lines.mapNotNull { line ->
                        val lBox = line.boundingBox
                        if (lBox != null && lBox.height() > 0 && lBox.width() > 0) {
                            val lHeight = lBox.height().toFloat()
                            val lWidth = lBox.width().toFloat()
                            if (lHeight > lWidth * 1.4f) {
                                // Vertical Tategaki: Japanese characters are square columns -> width * 0.85f
                                val elemHeights = line.elements.mapNotNull { it.boundingBox?.height()?.takeIf { h -> h > 0 } }
                                if (elemHeights.isNotEmpty()) {
                                    elemHeights.average().toFloat() * 0.85f
                                } else {
                                    lWidth * 0.85f
                                }
                            } else {
                                // Horizontal text: use line height * 0.85f
                                lHeight * 0.85f
                            }
                        } else {
                            null
                        }
                    }

                    val avgLineHeight = if (lineHeights.isNotEmpty()) {
                        lineHeights.average().toFloat()
                    } else {
                        val lineCount = kotlin.math.max(1, block.lines.size)
                        (validRect.height().toFloat() / lineCount * 0.85f).coerceAtLeast(14f)
                    }

                    detectedBlocks.add(
                        DetectedTextBlock(
                            text = blockText,
                            boundingBox = validRect,
                            originalTextSizePx = avgLineHeight
                        )
                    )
                }
            }
            detectedBlocks
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (e is IllegalStateException || msg.contains("closed", ignoreCase = true) || msg.contains("released", ignoreCase = true)) {
                Log.w(tag, "Recognizer was closed/released concurrently during profile switch, ignoring frame safely: $msg")
                return emptyList()
            }
            if (msg.contains("download", ignoreCase = true) ||
                msg.contains("Waiting for", ignoreCase = true) ||
                msg.contains("model", ignoreCase = true)
            ) {
                lastOcrError = "Descargando modelo de reconocimiento OCR en segundo plano... Intenta en unos segundos."
            }
            Log.w(tag, "OCR pass failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun hasCjkText(blocks: List<DetectedTextBlock>): Boolean {
        return blocks.any { block ->
            block.text.any { c ->
                (c in '\u3040'..'\u30ff') || // Hiragana / Katakana
                (c in '\u4e00'..'\u9fa5') || // Kanji / Hanzi
                (c in '\uac00'..'\ud7af') || // Hangul Syllables
                (c in '\u1100'..'\u11ff')    // Hangul Jamo
            }
        }
    }

    private fun hasLatinLetters(blocks: List<DetectedTextBlock>): Boolean {
        return blocks.any { block ->
            block.text.any { c -> c in 'a'..'z' || c in 'A'..'Z' }
        }
    }

    private fun createCjkRecognizer(langCode: String): TextRecognizer? {
        return when (langCode) {
            "JA" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "ZH" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            "KO" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            else -> null
        }
    }

    /**
     * Processes the provided screen Bitmap and extracts all detected text blocks
     * along with their absolute coordinates.
     *
     * Utilizes a 3-pass resilient pipeline:
     * 1. Primary recognizer on raw uncompressed RGB bitmap with content-aware validation.
     * 2. Bidirectional fallback (Latin <-> CJK) when character script mismatches or returns 0 blocks.
     * 3. Contrast-enhanced pass as last resort for faint screentones.
     *
     * @param bitmap Screen frame buffer
     * @return List of detected text blocks with non-empty text and valid bounding boxes
     */
    suspend fun processFrame(bitmap: Bitmap): List<DetectedTextBlock> = withContext(Dispatchers.Default) {
        if (bitmap.width <= 0 || bitmap.height <= 0) return@withContext emptyList()
        lastOcrError = null

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val activeRecognizer = synchronized(recognizerLock) {
            recognizer ?: run {
                val fallback = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer = fallback
                fallback
            }
        }
        val lang = currentLangCode
        val isCurrentCjk = lang in listOf("JA", "ZH", "KO")

        // PASS 1: Run primary recognizer directly on raw, uncompressed RGB bitmap
        var detectedBlocks = runOcrPass(activeRecognizer, inputImage)

        // Content-aware validation:
        if (detectedBlocks.isNotEmpty()) {
            if (isCurrentCjk && !hasCjkText(detectedBlocks) && hasLatinLetters(detectedBlocks)) {
                // Primary is CJK (e.g. Japanese), but detected ONLY Latin text (English scanlation).
                // Specialized CJK recognizers produce broken or misaligned Latin boxes.
                Log.d(tag, "CJK recognizer ($lang) detected Latin text. Falling back to Latin recognizer for optimal scanlation accuracy...")
                val defaultRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    val latinBlocks = runOcrPass(defaultRecognizer, inputImage)
                    if (latinBlocks.isNotEmpty()) {
                        Log.d(tag, "Latin recognizer successfully recognized ${latinBlocks.size} English scanlation blocks")
                        return@withContext latinBlocks
                    }
                } finally {
                    try { defaultRecognizer.close() } catch (_: Exception) {}
                }
            } else if (!isCurrentCjk && !hasLatinLetters(detectedBlocks) && activeProfile.defaultSourceLang in listOf("JA", "ZH", "KO")) {
                // Primary is Latin, but detected no Latin letters at all. Maybe raw CJK text with stray dots.
                val cjkRecognizer = createCjkRecognizer(activeProfile.defaultSourceLang)
                if (cjkRecognizer != null) {
                    try {
                        val cjkBlocks = runOcrPass(cjkRecognizer, inputImage)
                        if (cjkBlocks.isNotEmpty() && hasCjkText(cjkBlocks)) {
                            Log.d(tag, "CJK fallback for ${activeProfile.defaultSourceLang} detected ${cjkBlocks.size} CJK blocks")
                            return@withContext cjkBlocks
                        }
                    } finally {
                        try { cjkRecognizer.close() } catch (_: Exception) {}
                    }
                }
            } else {
                Log.d(tag, "Pass 1 (Primary: $lang) detected ${detectedBlocks.size} blocks")
                return@withContext detectedBlocks
            }
        }

        // PASS 2: If primary returned 0 blocks, try opposite script recognizer
        if (detectedBlocks.isEmpty()) {
            if (isCurrentCjk) {
                Log.d(tag, "Primary recognizer ($lang) found 0 blocks. Attempting fallback to Default Latin recognizer...")
                val defaultRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    detectedBlocks = runOcrPass(defaultRecognizer, inputImage)
                    if (detectedBlocks.isNotEmpty()) {
                        Log.d(tag, "Pass 2 (Default Latin fallback) successfully detected ${detectedBlocks.size} blocks!")
                        return@withContext detectedBlocks
                    }
                } finally {
                    try { defaultRecognizer.close() } catch (_: Exception) {}
                }
            } else if (activeProfile.defaultSourceLang in listOf("JA", "ZH", "KO")) {
                Log.d(tag, "Default Latin found 0 blocks. Attempting fallback to specialized CJK (${activeProfile.defaultSourceLang})...")
                val cjkRecognizer = createCjkRecognizer(activeProfile.defaultSourceLang)
                if (cjkRecognizer != null) {
                    try {
                        detectedBlocks = runOcrPass(cjkRecognizer, inputImage)
                        if (detectedBlocks.isNotEmpty()) {
                            Log.d(tag, "Pass 2 (CJK fallback: ${activeProfile.defaultSourceLang}) detected ${detectedBlocks.size} blocks!")
                            return@withContext detectedBlocks
                        }
                    } finally {
                        try { cjkRecognizer.close() } catch (_: Exception) {}
                    }
                }
            }
        }

        // PASS 3: If still 0 blocks, try contrast-enhanced grayscale as last resort for faint screentones
        val preprocessed = try {
            preprocessMangaBitmap(bitmap)
        } catch (e: Exception) {
            null
        }
        if (preprocessed != null) {
            try {
                val preprocessedImage = InputImage.fromBitmap(preprocessed, 0)
                val currentPass3Recognizer = synchronized(recognizerLock) { recognizer } ?: activeRecognizer
                detectedBlocks = runOcrPass(currentPass3Recognizer, preprocessedImage)
                if (detectedBlocks.isEmpty()) {
                    if (isCurrentCjk) {
                        val defaultRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        try {
                            detectedBlocks = runOcrPass(defaultRecognizer, preprocessedImage)
                        } finally {
                            try { defaultRecognizer.close() } catch (_: Exception) {}
                        }
                    } else if (activeProfile.defaultSourceLang in listOf("JA", "ZH", "KO")) {
                        val cjkRecognizer = createCjkRecognizer(activeProfile.defaultSourceLang)
                        if (cjkRecognizer != null) {
                            try {
                                detectedBlocks = runOcrPass(cjkRecognizer, preprocessedImage)
                            } finally {
                                try { cjkRecognizer.close() } catch (_: Exception) {}
                            }
                        }
                    }
                }
                if (detectedBlocks.isNotEmpty()) {
                    Log.d(tag, "Pass 3 (Contrast-enhanced) detected ${detectedBlocks.size} blocks")
                }
            } finally {
                preprocessed.recycle()
            }
        }

        detectedBlocks
    }

    /**
     * Releases ML Kit resources and clears native references.
     */
    fun close() {
        synchronized(recognizerLock) {
            try {
                recognizer?.close()
                Log.d(tag, "TextRecognizer released and closed")
            } catch (e: Exception) {
                Log.w(tag, "Error releasing TextRecognizer", e)
            } finally {
                recognizer = null
                currentLangCode = ""
            }
        }
    }
}
