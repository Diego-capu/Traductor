package com.antigravity.translator.engine.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.antigravity.translator.domain.model.DetectedTextBlock
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
    private var recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val recognizerLock = Any()

    /**
     * Dynamically updates the active ML Kit TextRecognizer according to source language.
     * Invokes previousRecognizer.close() immediately to release native GPU/CPU memory.
     */
    fun setSourceLanguage(langCode: String) {
        val normalized = langCode.trim().uppercase()
        synchronized(recognizerLock) {
            if (normalized == currentLangCode) return

            val previousRecognizer = recognizer
            try {
                previousRecognizer.close()
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
     * Preprocesses manga frame buffer (grayscale + contrast enhancement)
     * to eliminate screentones and shadows, ensuring crisp text strokes for OCR.
     */
    private fun preprocessMangaBitmap(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)

        val contrast = 1.35f
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
     * Processes the provided screen Bitmap and extracts all detected text blocks
     * along with their absolute coordinates.
     *
     * @param bitmap Screen frame buffer
     * @return List of detected text blocks with non-empty text and valid bounding boxes
     */
    suspend fun processFrame(bitmap: Bitmap): List<DetectedTextBlock> = withContext(Dispatchers.Default) {
        val preprocessed = try {
            preprocessMangaBitmap(bitmap)
        } catch (e: Exception) {
            Log.w(tag, "Preprocessing failed, using raw bitmap", e)
            bitmap
        }

        try {
            val inputImage = InputImage.fromBitmap(preprocessed, 0)
            val currentRecognizer = synchronized(recognizerLock) { recognizer }
            val visionText = currentRecognizer.process(inputImage).await()

            val detectedBlocks = mutableListOf<DetectedTextBlock>()

            for (block in visionText.textBlocks) {
                val blockText = block.text.trim()
                val box = block.boundingBox

                if (blockText.isNotEmpty() && box != null && !box.isEmpty) {
                    val validRect = Rect(box.left, box.top, box.right, box.bottom)
                    detectedBlocks.add(
                        DetectedTextBlock(
                            text = blockText,
                            boundingBox = validRect
                        )
                    )
                }
            }

            detectedBlocks
        } catch (e: Exception) {
            Log.e(tag, "ML Kit OCR recognition failed", e)
            emptyList()
        } finally {
            if (preprocessed !== bitmap) {
                preprocessed.recycle()
            }
        }
    }

    /**
     * Releases ML Kit resources.
     */
    fun close() {
        synchronized(recognizerLock) {
            try {
                recognizer.close()
            } catch (e: Exception) {
                Log.w(tag, "Error releasing TextRecognizer", e)
            }
        }
    }
}
