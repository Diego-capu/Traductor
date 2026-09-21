package com.antigravity.translator.engine.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.antigravity.translator.domain.model.DetectedTextBlock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-Device Optical Character Recognition engine powered by Google ML Kit.
 */
class OcrEngine {

    private val tag = "OcrEngine"
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Processes the provided screen Bitmap and extracts all detected text blocks
     * along with their absolute coordinates.
     *
     * @param bitmap Screen frame buffer
     * @return List of detected text blocks with non-empty text and valid bounding boxes
     */
    suspend fun processFrame(bitmap: Bitmap): List<DetectedTextBlock> = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(inputImage).await()

            val detectedBlocks = mutableListOf<DetectedTextBlock>()

            for (block in visionText.textBlocks) {
                val blockText = block.text.trim()
                val box = block.boundingBox

                if (blockText.isNotEmpty() && box != null && !box.isEmpty) {
                    // Create defensive copy of Rect
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
        }
    }

    /**
     * Releases ML Kit resources.
     */
    fun close() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing TextRecognizer", e)
        }
    }
}
