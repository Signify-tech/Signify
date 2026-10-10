package com.signify.translation;

import java.util.List;

public record SentenceTransformation(int sentence, boolean question, List<TokenAnalysis> tokens,
                                     List<GlossUnit> units, List<RuleApplication> rules, List<String> notes) {
}
