package com.signify.vocabulary;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Supported media formats. A file's extension must match its declared type. */
public enum MediaType {
    VIDEO(Map.of("mp4", "video/mp4", "webm", "video/webm")),
    GIF(Map.of("gif", "image/gif")),
    IMAGE(Map.of("png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "webp", "image/webp"));

    private final Map<String, String> contentTypes;

    MediaType(Map<String, String> contentTypes) {
        this.contentTypes = contentTypes;
    }

    /** Content type for the file name, or empty when the extension does not belong to this media type. */
    public Optional<String> contentTypeFor(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(contentTypes.get(fileName.substring(dot + 1).toLowerCase(Locale.ROOT)));
    }
}
