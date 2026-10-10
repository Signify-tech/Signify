package com.signify.vocabulary;

/** API view of a vocabulary entry with its computed media status. */
public record VocabularyEntryView(
        long id,
        String term,
        String normalizedTerm,
        SignCategory category,
        VerificationStatus verificationStatus,
        SignStatus status,
        boolean mediaRegistered,
        boolean mediaAvailable,
        boolean playable,
        boolean verified,
        MediaType mediaType,
        String mediaUrl,
        String source,
        String license,
        String attribution,
        String notes) {
}
