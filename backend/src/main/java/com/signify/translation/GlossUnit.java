package com.signify.translation;

import com.signify.nlp.NlpToken;
import java.util.List;

/**
 * A unit of the ISL-oriented sequence: one word or one registered multi-word phrase.
 *
 * @param key      normalised lookup key (lemma or base form, e.g. "learn", "thank you")
 * @param surface  normalised surface form, used as a second lookup candidate
 * @param role     grammatical role used by the ordering rules
 */
public record GlossUnit(String key, String surface, String pos, Role role, boolean phrase, int sentence,
                        List<NlpToken> tokens) {

    public enum Role { TIME, VERB, NEGATION, WH, CONJUNCTION, OTHER }

    public String gloss() {
        return key.toUpperCase(java.util.Locale.ROOT);
    }
}
