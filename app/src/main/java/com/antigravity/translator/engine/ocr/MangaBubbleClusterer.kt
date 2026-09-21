package com.antigravity.translator.engine.ocr

import android.graphics.Rect
import com.antigravity.translator.domain.model.DetectedTextBlock
import java.util.UUID

/**
 * Intelligent spatial text clusterer designed for Manga, Manhwa, and Comic speech bubbles.
 *
 * Solves the fragmentation issue where OCR returns individual lines or phrases.
 * Clusters vertically and horizontally adjacent lines inside the same speech bubble,
 * repairs hyphenated word wraps (e.g., "cow-" + "ardly" -> "cowardly"), and unifies
 * their bounding boxes so the translation covers the entire original speech bubble.
 */
object MangaBubbleClusterer {

    /**
     * Groups fragmented text blocks into unified speech bubble blocks.
     *
     * @param blocks Raw detected text blocks from ML Kit
     * @param density Display density scaling factor
     * @return Clustered list with 1 unified DetectedTextBlock per speech bubble
     */
    fun clusterMangaBubbles(
        blocks: List<DetectedTextBlock>,
        density: Float = 2.5f
    ): List<DetectedTextBlock> {
        if (blocks.size <= 1) return blocks

        // Filter out empty or micro noise artifacts
        val validBlocks = blocks.filter {
            it.text.trim().isNotEmpty() && it.boundingBox.width() > 10 && it.boundingBox.height() > 10
        }

        val clusters = mutableListOf<MutableCluster>()
        val maxVerticalGapPx = (24 * density).toInt()
        val maxHorizontalGapPx = (32 * density).toInt()

        // Sort primarily top-to-bottom, secondarily left-to-right
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

        // Convert clusters into final unified DetectedTextBlocks with exact bounds
        return clusters.map { cluster ->
            val unifiedText = cluster.buildUnifiedText()
            val exactRect = Rect(
                cluster.bounds.left,
                cluster.bounds.top,
                cluster.bounds.right,
                cluster.bounds.bottom
            )
            DetectedTextBlock(
                id = UUID.randomUUID().toString(),
                text = unifiedText,
                boundingBox = exactRect
            )
        }
    }

    private class MutableCluster(firstBlock: DetectedTextBlock) {
        val bounds = Rect(firstBlock.boundingBox)
        val textPieces = mutableListOf<TextPiece>()

        init {
            textPieces.add(TextPiece(firstBlock.text.trim(), firstBlock.boundingBox.top, firstBlock.boundingBox.left))
        }

        fun add(block: DetectedTextBlock) {
            bounds.union(block.boundingBox)
            textPieces.add(TextPiece(block.text.trim(), block.boundingBox.top, block.boundingBox.left))
        }

        fun mergeWith(other: MutableCluster) {
            bounds.union(other.bounds)
            textPieces.addAll(other.textPieces)
        }

        fun isNear(rect: Rect, maxVGap: Int, maxHGap: Int): Boolean {
            // Horizontal proximity / overlap
            val hOverlap = (bounds.left <= rect.right + maxHGap) && (bounds.right >= rect.left - maxHGap)
            // Vertical proximity / overlap
            val vOverlap = (bounds.top <= rect.bottom + maxVGap) && (bounds.bottom >= rect.top - maxVGap)

            return hOverlap && vOverlap
        }

        fun buildUnifiedText(): String {
            // Sort lines top to bottom within the speech bubble
            val sortedPieces = textPieces.sortedWith(
                compareBy<TextPiece> { it.top }.thenBy { it.left }
            )

            val sb = StringBuilder()
            for (piece in sortedPieces) {
                val currentText = piece.text
                if (sb.isEmpty()) {
                    sb.append(currentText)
                } else {
                    // Check if previous line ended with hyphenation (e.g., "cow-")
                    if (sb.endsWith("-") && !sb.endsWith("--")) {
                        sb.setLength(sb.length - 1) // Remove hyphen
                        sb.append(currentText)
                    } else {
                        sb.append(" ").append(currentText)
                    }
                }
            }
            return sb.toString().replace(Regex("\\s+"), " ").trim()
        }
    }

    private data class TextPiece(
        val text: String,
        val top: Int,
        val left: Int
    )
}
