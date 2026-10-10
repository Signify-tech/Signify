package com.signify.translation;

/**
 * What the pipeline did with one input token. {@code action} is KEPT, GROUPED (part of a phrase) or DROPPED;
 * {@code rule} and {@code reason} explain why.
 */
public record TokenAnalysis(int index, int sentence, String text, String lemma, String pos,
                            String action, String rule, String reason, String gloss) {
}
