package com.signify.vocabulary;

/**
 * Availability of a sign representation, from least to most usable.
 *
 * <ul>
 *   <li>{@link #NOT_IN_VOCABULARY}: the word or letter has no vocabulary entry.</li>
 *   <li>{@link #MEDIA_MISSING}: a vocabulary entry exists but no media file is registered or present on disk.</li>
 *   <li>{@link #AVAILABLE_UNVERIFIED}: a media file exists but has not been verified as authentic ISL.</li>
 *   <li>{@link #VERIFIED}: a media file exists and is marked as a verified ISL sign.</li>
 * </ul>
 */
public enum SignStatus {
    NOT_IN_VOCABULARY, MEDIA_MISSING, AVAILABLE_UNVERIFIED, VERIFIED;

    public boolean playable() {
        return this == AVAILABLE_UNVERIFIED || this == VERIFIED;
    }
}
