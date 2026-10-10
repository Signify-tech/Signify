package com.signify.translation;

import com.signify.vocabulary.MediaType;
import com.signify.vocabulary.SignCategory;
import com.signify.vocabulary.SignStatus;

/**
 * One item in the playback sequence.
 *
 * @param entryId           vocabulary entry id, null when no entry exists (for example an unregistered letter)
 * @param representation    WORD, PHRASE, LETTER or DIGIT
 * @param mappingSource     DICTIONARY (direct vocabulary match) or FINGERSPELLING (fallback for an unknown word)
 * @param mediaUrl          only set when a media file is registered and present on disk
 * @param placeholder       true when there is no playable media and the player must show a labelled placeholder
 * @param fingerspelledWord the unknown word this letter belongs to, for fingerspelled items
 */
public record SignItem(
        int position,
        int sentence,
        int unitIndex,
        Long entryId,
        String gloss,
        SignCategory representation,
        String mappingSource,
        SignStatus status,
        boolean verified,
        boolean playable,
        boolean placeholder,
        MediaType mediaType,
        String mediaUrl,
        String source,
        String license,
        String attribution,
        String fingerspelledWord,
        Integer letterIndex,
        Integer letterCount,
        String message) {
}
