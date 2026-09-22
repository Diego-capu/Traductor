package com.antigravity.translator.engine.ocr

import android.graphics.Rect
import com.antigravity.translator.domain.model.DetectedTextBlock
import java.util.UUID

/**
 * Intelligent spatial text clusterer designed for Manga, Manhwa, and Comic speech bubbles.
 *
 * Implements:
 * 1. Tategaki reading order (Right-to-Left columns, Top-to-Bottom lines) strictly for Japanese (JA)
 *    and Chinese (ZH), preserving standard Left-to-Right order for Korean (KO) and Western languages.
 * 2. Furigana & noise micro-box filtering (discards blocks with height < 40% of cluster average).
 * 3. Narrative page order across speech bubbles (Top-to-Bottom by page band, Right-to-Left for Manga).
 * 4. CJK-aware string concatenation avoiding artificial spaces between kanji/kana characters.
 */
object MangaBubbleClusterer {

    /**
     * Groups fragmented text blocks into unified speech bubble blocks.
     *
     * @param blocks Raw detected text blocks from ML Kit
     * @param density Display density scaling factor
     * @param sourceLanguage Configured source language code (e.g., "JA", "ZH", "KO", "EN")
     * @return Clustered list with 1 unified DetectedTextBlock per speech bubble
     */
    fun clusterMangaBubbles(
        blocks: List<DetectedTextBlock>,
        density: Float = 2.5f,
        sourceLanguage: String = ""
    ): List<DetectedTextBlock> {
        if (blocks.size <= 1) return blocks

        // Filter out empty or micro noise artifacts
        val validBlocks = blocks.filter {
            val w = it.boundingBox.right - it.boundingBox.left
            val h = it.boundingBox.bottom - it.boundingBox.top
            it.text.trim().isNotEmpty() && w > 8 && h > 8
        }

        if (validBlocks.isEmpty()) return emptyList()

        val isTategaki = isTategakiLanguage(sourceLanguage, validBlocks)
        val clusters = mutableListOf<MutableCluster>()
        val maxVerticalGapPx = (24 * density).toInt()
        val maxHorizontalGapPx = (32 * density).toInt()

        // Initial spatial grouping: sort primarily top-to-bottom, secondarily left-to-right
        val sortedBlocks = validBlocks.sortedWith(
            compareBy<DetectedTextBlock> { it.boundingBox.top }
                .thenBy { it.boundingBox.left }
        )

        for (block in sortedBlocks) {
            var matchedCluster: MutableCluster? = null

            for (cluster in clusters) {
                if (cluster.isNear(block.boundingBox, maxVerticalGapPx, maxHorizontalGapPx)) {
                    matchedCluster = cluster
                    break
                }
            }

            if (matchedCluster != null) {
                matchedCluster.add(block)
            } else {
                clusters.add(MutableCluster(block))
            }
        }

        // Iteratively merge clusters that overlap after expansion
        var merged = true
        while (merged) {
            merged = false
            outer@ for (i in 0 until clusters.size) {
                for (j in i + 1 until clusters.size) {
                    val c1 = clusters[i]
                    val c2 = clusters[j]
                    if (c1.isNear(c2.bounds, maxVerticalGapPx / 2, maxHorizontalGapPx / 2)) {
                        c1.mergeWith(c2)
                        clusters.removeAt(j)
                        merged = true
                        break@outer
                    }
                }
            }
        }

        // Sort speech bubbles in chronological narrative order for DeepL contextual comprehension
        val bandHeight = (120 * density).toInt().coerceAtLeast(100)
        val sortedClusters = if (isTategaki) {
            // Manga narrative reading order: Top-to-Bottom by page band, then Right-to-Left
            clusters.sortedWith(
                compareBy<MutableCluster> { it.bounds.top / bandHeight }
                    .thenByDescending { it.bounds.right }
            )
        } else {
            // Standard reading order: Top-to-Bottom by page band, then Left-to-Right
            clusters.sortedWith(
                compareBy<MutableCluster> { it.bounds.top / bandHeight }
                    .thenBy { it.bounds.left }
            )
        }

        // Convert clusters into final unified DetectedTextBlocks with exact bounds
        return sortedClusters.map { cluster ->
            val unifiedText = cluster.buildUnifiedText(isTategaki)
            val exactRect = Rect().apply {
                left = cluster.bounds.left
                top = cluster.bounds.top
                right = cluster.bounds.right
                bottom = cluster.bounds.bottom
            }
            DetectedTextBlock(
                id = UUID.randomUUID().toString(),
                text = unifiedText,
                boundingBox = exactRect
            )
        }
    }

    /**
     * Determines whether Tategaki (Right-to-Left vertical column reading order) applies.
     * Strictly true for Japanese (JA) and Chinese (ZH).
     * Strictly false for Korean (KO) and Western languages.
     */
    fun isTategakiLanguage(sourceLanguage: String, blocks: List<DetectedTextBlock>): Boolean {
        val lang = sourceLanguage.trim().uppercase()
        if (lang == "JA" || lang == "ZH") return true
        if (lang.isNotEmpty()) return false // Explicit non-Tategaki (KO, EN, ES, FR, DE, etc.)

        // If sourceLanguage is auto/unspecified, detect if characters are Japanese/Chinese
        return blocks.any { block ->
            block.text.any { c ->
                (c in '\u3040'..'\u30ff') || // Hiragana / Katakana
                (c in '\u4e00'..'\u9fa5')   // CJK Unified Ideographs
            }
        }
    }

    private class MutableCluster(firstBlock: DetectedTextBlock) {
        val bounds = Rect().apply {
            left = firstBlock.boundingBox.left
            top = firstBlock.boundingBox.top
            right = firstBlock.boundingBox.right
            bottom = firstBlock.boundingBox.bottom
        }
        val textPieces = mutableListOf<TextPiece>()

        init {
            val b = firstBlock.boundingBox
            textPieces.add(TextPiece(firstBlock.text.trim(), b.top, b.left, b.right, b.bottom))
        }

        fun add(block: DetectedTextBlock) {
            unionRect(bounds, block.boundingBox)
            val b = block.boundingBox
            textPieces.add(TextPiece(block.text.trim(), b.top, b.left, b.right, b.bottom))
        }

        fun mergeWith(other: MutableCluster) {
            unionRect(bounds, other.bounds)
            textPieces.addAll(other.textPieces)
        }

        private fun unionRect(target: Rect, source: Rect) {
            if (source.left < target.left) target.left = source.left
            if (source.top < target.top) target.top = source.top
            if (source.right > target.right) target.right = source.right
            if (source.bottom > target.bottom) target.bottom = source.bottom
        }

        fun isNear(rect: Rect, maxVGap: Int, maxHGap: Int): Boolean {
            val hOverlap = (bounds.left <= rect.right + maxHGap) && (bounds.right >= rect.left - maxHGap)
            val vOverlap = (bounds.top <= rect.bottom + maxVGap) && (bounds.bottom >= rect.top - maxVGap)
            return hOverlap && vOverlap
        }

        /**
         * Discards micro furigana ruby text and builds a single coherent paragraph
         * ordered according to the language reading model.
         */
        fun buildUnifiedText(isTategaki: Boolean): String {
            // 1. Furigana & noise filtering: discard micro-rectangles < 40% of average cluster height
            val candidatePieces = if (textPieces.size >= 2) {
                val avgHeight = textPieces.map { (it.bottom - it.top).toDouble() }.average()
                val minHeightThreshold = avgHeight * 0.40
                val filtered = textPieces.filter { (it.bottom - it.top) >= minHeightThreshold }
                if (filtered.isNotEmpty()) filtered else textPieces
            } else {
                textPieces
            }

            // 2. Sort lines according to reading direction
            val sortedPieces = if (isTategaki) {
                // Tategaki (Japanese/Chinese Manga):
                // Columns ordered from Right to Left (descending X)
                // Lines within each vertical column ordered from Top to Bottom (ascending Y)
                candidatePieces.sortedWith { a, b ->
                    val aCenterX = (a.left + a.right) / 2
                    val bCenterX = (b.left + b.right) / 2
                    val aWidth = (a.right - a.left).coerceAtLeast(1)
                    val bWidth = (b.right - b.left).coerceAtLeast(1)
                    val colTolerance = (kotlin.math.min(aWidth, bWidth) * 0.45f).toInt().coerceAtLeast(8)

                    val inSameColumn = kotlin.math.abs(aCenterX - bCenterX) <= colTolerance ||
                            (a.left < b.right && a.right > b.left)

                    if (inSameColumn) {
                        a.top.compareTo(b.top)
                    } else {
                        bCenterX.compareTo(aCenterX) // Right to Left
                    }
                }
            } else {
                // Standard Horizontal (Korean Manhwa / Western Comics):
                // Lines ordered from Top to Bottom, Left to Right
                candidatePieces.sortedWith { a, b ->
                    val aCenterY = (a.top + a.bottom) / 2
                    val bCenterY = (b.top + b.bottom) / 2
                    val aHeight = (a.bottom - a.top).coerceAtLeast(1)
                    val bHeight = (b.bottom - b.top).coerceAtLeast(1)
                    val rowTolerance = (kotlin.math.min(aHeight, bHeight) * 0.45f).toInt().coerceAtLeast(8)

                    val inSameRow = kotlin.math.abs(aCenterY - bCenterY) <= rowTolerance ||
                            (a.top < b.bottom && a.bottom > b.top)

                    if (inSameRow) {
                        a.left.compareTo(b.left)
                    } else {
                        aCenterY.compareTo(bCenterY) // Top to Bottom
                    }
                }
            }

            // 3. Concatenate lines into a unified continuous paragraph
            val sb = StringBuilder()
            for (piece in sortedPieces) {
                val currentText = piece.text
                if (sb.isEmpty()) {
                    sb.append(currentText)
                } else {
                    if (isTategaki) {
                        // In Japanese/Chinese, do not insert spaces between CJK characters
                        val lastChar = sb.lastOrNull()
                        val firstChar = currentText.firstOrNull()

                        val isLastCjk = lastChar != null && isCjkCharacter(lastChar)
                        val isFirstCjk = firstChar != null && isCjkCharacter(firstChar)

                        if (isLastCjk || isFirstCjk) {
                            sb.append(currentText)
                        } else {
                            sb.append(" ").append(currentText)
                        }
                    } else {
                        // In Western / Korean languages: join with spaces or handle hyphens
                        if (sb.endsWith("-") && !sb.endsWith("--")) {
                            sb.setLength(sb.length - 1) // Remove hyphen wrap
                            sb.append(currentText)
                        } else {
                            sb.append(" ").append(currentText)
                        }
                    }
                }
            }

            return sb.toString().replace(Regex("\\s+"), " ").trim()
        }

        private fun isCjkCharacter(c: Char): Boolean {
            return (c in '\u3040'..'\u30ff') || // Hiragana / Katakana
                    (c in '\u4e00'..'\u9fa5') || // Kanji / Hanzi
                    (c in '\u3000'..'\u303f') || // CJK Symbols and Punctuation
                    (c in '\uff00'..'\uffef')    // Halfwidth and Fullwidth Forms
        }
    }

    private data class TextPiece(
        val text: String,
        val top: Int,
        val left: Int,
        val right: Int,
        val bottom: Int
    )
}
