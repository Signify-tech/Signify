package com.signify.vocabulary;

/**
 * Counts computed from the vocabulary table and the media directory.
 * {@code wordsAndPhrases} is compared with {@code targetWordsAndPhrases}, the project target.
 */
public record VocabularyStats(
        long totalEntries,
        long words,
        long phrases,
        long letters,
        long digits,
        long wordsAndPhrases,
        int targetWordsAndPhrases,
        long mediaRegistered,
        long mediaAvailable,
        long mediaMissing,
        long verified,
        long availableUnverified,
        long lettersWithMedia,
        double mediaCoveragePercent) {
}
