package com.antigravity.translator.engine.ocr

import android.graphics.Rect
import com.antigravity.translator.domain.model.DetectedTextBlock
import com.antigravity.translator.domain.model.ReadingProfile
import java.util.UUID

/**
 * Spatial clustering engine designed for comic / manga dialogue segmentation.
 *
 * Merges adjacent text blocks belonging to the same speech bubble, sorts them
 * according to reading orientation (Tategaki RTL for Manga, standard horizontal LTR
 * for Manhwa and Comics), filters micro-box noise and furigana, and unifies fragmented
 * lines into continuous narrative blocks for machine translation.
 */
object MangaBubbleClusterer {

    /**
     * Clusters raw OCR text blocks into cohesive manga speech bubbles.
     *
     * @param blocks Raw text blocks detected by ML Kit
     * @param density Screen density factor
     * @param sourceLanguage Source language code ("JA", "ZH", "KO", "EN", etc.)
     * @param readingProfile Selected reading medium profile (Manga, Manhwa, Manhua, Comic)
     * @return Clustered list with 1 unified DetectedTextBlock per speech bubble
     */
    fun clusterMangaBubbles(
        blocks: List<DetectedTextBlock>,
        density: Float = 2.5f,
        sourceLanguage: String = "",
        readingProfile: ReadingProfile? = null
    ): List<DetectedTextBlock> {
        if (blocks.size <= 1) return blocks

        // Filter out empty or micro noise artifacts
        val validBlocks = blocks.filter {
            val w = it.boundingBox.right - it.boundingBox.left
            val h = it.boundingBox.bottom - it.boundingBox.top
            it.text.trim().isNotEmpty() && ((w >= 6 && h >= 4) || (w >= 4 && h >= 6))
        }

        if (validBlocks.isEmpty()) return emptyList()

        val hasCjk = validBlocks.any { b ->
            b.text.any { c -> (c in '\u3040'..'\u30ff') || (c in '\u4e00'..'\u9fa5') }
        }
        val isTategaki = if (readingProfile != null) {
            readingProfile.isTategaki && hasCjk
        } else {
            isTategakiLanguage(sourceLanguage, validBlocks)
        }
        val usesSpacedWords = if (!hasCjk) true else (readingProfile?.usesSpacedWords ?: !isTategaki)
        val clusters = mutableListOf<MutableCluster>()
        val verticalGapMultiplier = if (readingProfile == ReadingProfile.MANGA_JA) 1.35f else 1.0f
        val maxVerticalGapPx = (24 * density * verticalGapMultiplier).toInt()
        val maxHorizontalGapPx = (32 * density).toInt()

        // Initial spatial grouping: sort primarily top-to-bottom, secondarily left-to-right
        val sortedBlocks = validBlocks.sortedWith(
            compareBy<DetectedTextBlock> { it.boundingBox.top }
                .thenBy { it.boundingBox.left }
        )

        for (block in sortedBlocks) {
            var matchedCluster: MutableCluster? = null

            for (cluster in clusters) {
                if (cluster.canMergeWith(block.boundingBox, isTategaki, density, readingProfile, maxVerticalGapPx, maxHorizontalGapPx)) {
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
                    if (c1.canMergeWith(c2.bounds, isTategaki, density, readingProfile, maxVerticalGapPx / 2, maxHorizontalGapPx / 2)) {
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
            val unifiedText = cluster.buildUnifiedText(isTategaki, usesSpacedWords, readingProfile)
            val exactRect = Rect().apply {
                left = cluster.bounds.left
                top = cluster.bounds.top
                right = cluster.bounds.right
                bottom = cluster.bounds.bottom
            }
            DetectedTextBlock(
                id = UUID.randomUUID().toString(),
                text = unifiedText,
                boundingBox = exactRect,
                originalTextSizePx = cluster.getAverageTextSize()
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
            textPieces.add(TextPiece(firstBlock.text.trim(), b.top, b.left, b.right, b.bottom, firstBlock.originalTextSizePx))
        }

        fun add(block: DetectedTextBlock) {
            unionRect(bounds, block.boundingBox)
            val b = block.boundingBox
            textPieces.add(TextPiece(block.text.trim(), b.top, b.left, b.right, b.bottom, block.originalTextSizePx))
        }

        fun mergeWith(other: MutableCluster) {
            unionRect(bounds, other.bounds)
            textPieces.addAll(other.textPieces)
        }

        /**
         * Computes the weighted average font size across all merged lines,
         * ensuring longer narrative dialogue dominates over short punctuation marks.
         */
        fun getAverageTextSize(): Float {
            val validPieces = textPieces.filter { it.textSizePx > 0f }
            if (validPieces.isEmpty()) return 0f
            val totalWeight = validPieces.sumOf { it.text.length.coerceAtLeast(1) }
            val weightedSum = validPieces.sumOf { (it.textSizePx * it.text.length.coerceAtLeast(1)).toDouble() }
            return (weightedSum / totalWeight).toFloat()
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
         * Enforces strict clustering constraints to prevent distinct dialogue bubbles from merging:
         * 1. Bounding Box Proportion Guard:
         *    In Manga mode, speech bubbles are predominantly tall or oval.
         *    If merging two candidate blocks would create width > height * 1.5, reject the merge.
         * 2. Strict Spatial Distance Limits:
         *    - Vertical (Manga/Tategaki): horizontal gap must be < 1.2 * columnWidth.
         *      Columns must share >= 30% vertical span, unless continuation within the same column.
         *    - Horizontal (Manhwa/Comic): vertical line gap must be < 0.8 * lineHeight.
         */
        fun canMergeWith(
            rect: Rect,
            isTategaki: Boolean,
            density: Float,
            readingProfile: ReadingProfile?,
            maxVerticalGapPx: Int,
            maxHorizontalGapPx: Int
        ): Boolean {
            val unionLeft = kotlin.math.min(bounds.left, rect.left)
            val unionTop = kotlin.math.min(bounds.top, rect.top)
            val unionRight = kotlin.math.max(bounds.right, rect.right)
            val unionBottom = kotlin.math.max(bounds.bottom, rect.bottom)
            val unionWidth = unionRight - unionLeft
            val unionHeight = unionBottom - unionTop

            // 1. Proportion Guard:
            // Tategaki mode: reject abnormally wide merged bubbles
            if (isTategaki && unionWidth > unionHeight * 1.5f) {
                return false
            }
            // Horizontal mode: reject abnormally tall/narrow merged bubbles (prevents vertical chains across panels)
            if (!isTategaki && unionHeight > unionWidth * 1.8f) {
                return false
            }

            // 2. Strict Spatial Distance Limits
            if (isTategaki) {
                val b1Width = (bounds.right - bounds.left).coerceAtLeast(1)
                val b2Width = (rect.right - rect.left).coerceAtLeast(1)
                val columnWidth = kotlin.math.min(b1Width, b2Width).toFloat().coerceAtLeast(8f * density)

                val hGap = if (bounds.left > rect.right) bounds.left - rect.right else if (rect.left > bounds.right) rect.left - bounds.right else 0
                val vGap = if (bounds.top > rect.bottom) bounds.top - rect.bottom else if (rect.top > bounds.bottom) rect.top - bounds.bottom else 0

                // Two lines/blocks can ONLY be merged if horizontal gap < 1.2 * columnWidth
                val maxAllowedHGap = 1.2f * columnWidth
                if (hGap > maxAllowedHGap) {
                    return false
                }

                // Check if they are continuation fragments aligned within the exact same vertical column
                val center1X = (bounds.left + bounds.right) / 2
                val center2X = (rect.left + rect.right) / 2
                val horizontalSpanOverlap = kotlin.math.max(0, kotlin.math.min(bounds.right, rect.right) - kotlin.math.max(bounds.left, rect.left))
                val inSameColumn = kotlin.math.abs(center1X - center2X) <= (columnWidth * 0.35f).toInt() &&
                        horizontalSpanOverlap >= (columnWidth * 0.60f).toInt()

                if (inSameColumn) {
                    // Vertical continuation within same column: allow up to vertical tolerance
                    return vGap <= maxVerticalGapPx
                }

                // Different vertical columns: require at least 30% vertical span overlap
                val verticalSpanOverlap = kotlin.math.max(0, kotlin.math.min(bounds.bottom, rect.bottom) - kotlin.math.max(bounds.top, rect.top))
                val minHeight = kotlin.math.min(bounds.bottom - bounds.top, rect.bottom - rect.top)
                return minHeight > 0 && verticalSpanOverlap > 0.30f * minHeight
            } else {
                val b1Height = (bounds.bottom - bounds.top).coerceAtLeast(1)
                val b2Height = (rect.bottom - rect.top).coerceAtLeast(1)
                val lineHeight = kotlin.math.min(b1Height, b2Height).toFloat().coerceAtLeast(8f * density)

                val vGap = if (bounds.top > rect.bottom) bounds.top - rect.bottom else if (rect.top > bounds.bottom) rect.top - bounds.bottom else 0
                val hGap = if (bounds.left > rect.right) bounds.left - rect.right else if (rect.left > bounds.right) rect.left - bounds.right else 0

                // Two blocks can ONLY be merged if vertical line gap <= 0.75 * lineHeight
                val maxAllowedVGap = 0.75f * lineHeight
                if (vGap > maxAllowedVGap) {
                    return false
                }

                val horizontalSpanOverlap = kotlin.math.max(0, kotlin.math.min(bounds.right, rect.right) - kotlin.math.max(bounds.left, rect.left))
                val minWidth = kotlin.math.min(bounds.right - bounds.left, rect.right - rect.left)
                val verticalSpanOverlap = kotlin.math.max(0, kotlin.math.min(bounds.bottom, rect.bottom) - kotlin.math.max(bounds.top, rect.top))

                // Words side-by-side on the exact same horizontal line
                val isInlineWord = vGap <= (4 * density).toInt() && hGap <= maxHorizontalGapPx && verticalSpanOverlap >= 0.50f * lineHeight
                // Stacked lines: candidate lines must share at least 50% horizontal span
                val isStackedLine = minWidth > 0 && horizontalSpanOverlap >= 0.50f * minWidth

                return isInlineWord || isStackedLine
            }
        }

        /**
         * Discards micro furigana ruby text and builds a single coherent paragraph
         * ordered according to the language reading model.
         */
        fun buildUnifiedText(
            isTategaki: Boolean,
            usesSpacedWords: Boolean = !isTategaki,
            readingProfile: ReadingProfile? = null
        ): String {
            // 1. Furigana & noise filtering: discard micro-rectangles < 20% of average cluster height,
            // only applicable to Japanese Tategaki (MANGA_JA). Completely bypassed for MANGA_EN and horizontal text.
            val candidatePieces = if ((isTategaki || readingProfile == ReadingProfile.MANGA_JA) && textPieces.size >= 2) {
                val avgHeight = textPieces.map { (it.bottom - it.top).toDouble() }.average()
                val minHeightThreshold = avgHeight * 0.20
                val filtered = textPieces.filter { piece ->
                    val pieceHeight = piece.bottom - piece.top
                    if (pieceHeight >= minHeightThreshold) {
                        true
                    } else if (isTategaki) {
                        val pieceCenterX = (piece.left + piece.right) / 2
                        textPieces.any { other ->
                            if (other === piece) return@any false
                            val otherCenterX = (other.left + other.right) / 2
                            val otherWidth = (other.right - other.left).coerceAtLeast(1)
                            val colTolerance = (otherWidth * 0.5f).toInt().coerceAtLeast(8)
                            kotlin.math.abs(pieceCenterX - otherCenterX) <= colTolerance ||
                                    (piece.left < other.right && piece.right > other.left)
                        }
                    } else {
                        false
                    }
                }
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
                    if (!usesSpacedWords) {
                        // In Japanese/Chinese: do not insert spaces between CJK characters
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
                        // In Western / Korean languages (Manhwa / Comic): join with spaces or handle hyphens
                        val prevChar = if (sb.length >= 2) sb[sb.length - 2] else ' '
                        val nextChar = currentText.firstOrNull() ?: ' '
                        val isWordBreakHyphen = sb.endsWith("-") && !sb.endsWith("--") && prevChar.isLetter() && nextChar.isLetter()

                        if (isWordBreakHyphen) {
                            sb.setLength(sb.length - 1) // Remove syllabic hyphen wrap (e.g. "cow-" + "ardly" -> "cowardly")
                            sb.append(currentText)
                        } else {
                            // Punctuation, em-dashes (— or --), or independent words: separate with natural space
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
        val bottom: Int,
        val textSizePx: Float = 0f
    )
}
