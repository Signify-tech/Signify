package com.signify.nlp;

/**
 * One analysed token. {@code pos} is a Universal Dependencies UPOS tag (NOUN, VERB, AUX, PART, ...).
 */
public record NlpToken(int index, int sentence, String text, String lemma, String pos) {

    public String lowerText() {
        return text.toLowerCase(java.util.Locale.ROOT);
    }

    public boolean isPunctuation() {
        return "PUNCT".equals(pos) || "SYM".equals(pos);
    }
}
