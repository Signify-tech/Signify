package com.signify.translation;

import com.signify.vocabulary.SignStatus;

/**
 * One gloss of the transformed sequence and how it was mapped. {@code status} is NOT_IN_VOCABULARY for
 * fingerspelled words; otherwise it is the status of the matched entry.
 */
public record SequenceUnit(int index, int sentence, String gloss, String role, String mappingSource,
                           String matchedBy, Long entryId, SignStatus status, int firstPosition, int signCount) {
}
