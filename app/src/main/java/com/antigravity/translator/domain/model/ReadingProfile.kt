package com.antigravity.translator.domain.model

/**
 * Reading profiles tailored for different comic and literature traditions.
 *
 * Configures OCR model selection, spatial reading order, lexical word spacing,
 * and translation formality parameters.
 *
 * @param title Human-readable label for UI display (without emojis)
 * @param defaultSourceLang Standard source language code for ML Kit OCR
 * @param isTategaki True for Right-to-Left vertical column reading order (Japanese/Chinese Manga)
 * @param usesSpacedWords True for languages using spaces between words (Korean, Western); False for JA/ZH
 * @param defaultFormality DeepL formality preference ("less" for casual dialogue, null for standard)
 */
enum class ReadingProfile(
    val title: String,
    val defaultSourceLang: String,
    val isTategaki: Boolean,
    val usesSpacedWords: Boolean,
    val defaultFormality: String?
) {
    MANGA_JA(
        title = "Manga (JA)",
        defaultSourceLang = "JA",
        isTategaki = true,
        usesSpacedWords = false,
        defaultFormality = "less"
    ),
    MANGA_EN(
        title = "Manga (EN)",
        defaultSourceLang = "EN",
        isTategaki = false,
        usesSpacedWords = true,
        defaultFormality = "less"
    ),
    MANHWA_KO(
        title = "Manhwa (KO)",
        defaultSourceLang = "KO",
        isTategaki = false,
        usesSpacedWords = true,
        defaultFormality = "less"
    ),
    MANHWA_EN(
        title = "Manhwa (EN)",
        defaultSourceLang = "EN",
        isTategaki = false,
        usesSpacedWords = true,
        defaultFormality = "less"
    ),
    MANHUA(
        title = "Manhua (ZH)",
        defaultSourceLang = "ZH",
        isTategaki = false,
        usesSpacedWords = false,
        defaultFormality = null
    ),
    COMIC(
        title = "Cómic (Occidental)",
        defaultSourceLang = "EN",
        isTategaki = false,
        usesSpacedWords = true,
        defaultFormality = null
    );

    /**
     * Cycles to the next reading profile in order.
     */
    fun next(): ReadingProfile {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}
