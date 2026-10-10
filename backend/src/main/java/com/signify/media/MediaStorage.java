package com.signify.media;

import com.signify.config.SignifyProperties;
import com.signify.vocabulary.SignEntry;
import com.signify.vocabulary.SignStatus;
import com.signify.vocabulary.VerificationStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves registered media paths inside the media root. Paths are relative to the root; anything that
 * escapes the root (absolute paths, "..", symlinks pointing outside) or whose extension does not match the
 * declared media type is treated as unavailable.
 */
@Component
public class MediaStorage {

    private static final Logger log = LoggerFactory.getLogger(MediaStorage.class);

    private final Path root;

    public MediaStorage(SignifyProperties properties) {
        this.root = Path.of(properties.mediaDir()).toAbsolutePath().normalize();
        log.info("Media root: {} (exists: {})", root, Files.isDirectory(root));
    }

    public Path root() {
        return root;
    }

    /** The media file for this entry, if it is registered, inside the root, of the declared type and present. */
    public Optional<Path> resolve(SignEntry entry) {
        if (!entry.hasMediaRegistered()) {
            return Optional.empty();
        }
        return resolveRelative(entry.mediaPath())
                .filter(p -> entry.mediaType().contentTypeFor(p.getFileName().toString()).isPresent())
                .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(p));
    }

    /** Validates a relative path against the root without checking that the file exists. */
    public Optional<Path> resolveRelative(String relative) {
        if (relative == null || relative.isBlank() || relative.contains("\0") || relative.contains("\\")) {
            return Optional.empty();
        }
        Path candidate = Path.of(relative);
        if (candidate.isAbsolute()) {
            return Optional.empty();
        }
        Path resolved = root.resolve(candidate).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            return Optional.empty();
        }
        try {
            if (Files.exists(resolved) && !resolved.toRealPath().startsWith(root.toRealPath())) {
                return Optional.empty();
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    public SignStatus statusOf(SignEntry entry) {
        if (resolve(entry).isEmpty()) {
            return SignStatus.MEDIA_MISSING;
        }
        return entry.verificationStatus() == VerificationStatus.VERIFIED
                ? SignStatus.VERIFIED : SignStatus.AVAILABLE_UNVERIFIED;
    }

    /** Public URL served by {@link MediaController}; only for entries whose file is available. */
    public static String mediaUrl(long id) {
        return "/api/media/" + id;
    }
}
