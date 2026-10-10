package com.signify.vocabulary;

public record SignEntry(
        Long id,
        String term,
        String normalizedTerm,
        SignCategory category,
        String mediaPath,
        MediaType mediaType,
        String source,
        String license,
        String attribution,
        VerificationStatus verificationStatus,
        String notes) {

    public boolean hasMediaRegistered() {
        return mediaPath != null && !mediaPath.isBlank() && mediaType != null;
    }
}
