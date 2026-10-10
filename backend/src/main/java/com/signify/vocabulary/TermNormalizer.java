package com.signify.vocabulary;

import java.util.Locale;

/** Normalises vocabulary terms and lookup keys the same way: lower case, single spaces, no edge punctuation. */
public final class TermNormalizer {

    private TermNormalizer() {
    }

    public static String normalize(String term) {
        if (term == null) {
            return "";
        }
        return term.toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("[^\\p{L}\\p{N}' ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
