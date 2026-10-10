package com.signify.translation;

import java.util.List;

public record TranslationResult(
        String originalText,
        int sentenceCount,
        List<TokenAnalysis> tokens,
        List<String> transformedSequence,
        List<SequenceUnit> units,
        List<RuleApplication> rulesApplied,
        List<SignItem> signs,
        Summary summary,
        List<String> warnings,
        double processingTimeMs) {

    public record Summary(int tokenCount, int keptTokens, int droppedTokens, int units, int dictionaryMatches,
                          int fingerspelledWords, int signItems, int playableItems, int verifiedItems,
                          int placeholderItems) {
    }
}
