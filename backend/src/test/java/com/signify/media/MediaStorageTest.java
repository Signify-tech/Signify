package com.signify.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.signify.config.SignifyProperties;
import com.signify.vocabulary.MediaType;
import com.signify.vocabulary.SignCategory;
import com.signify.vocabulary.SignEntry;
import com.signify.vocabulary.SignStatus;
import com.signify.vocabulary.VerificationStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaStorageTest {

    @TempDir
    Path dir;

    private MediaStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createDirectories(dir.resolve("media"));
        Files.createDirectories(root.resolve("words"));
        Files.writeString(root.resolve("words/hello.mp4"), "x");
        Files.writeString(dir.resolve("secret.mp4"), "x");
        storage = new MediaStorage(new SignifyProperties(List.of(), root.toString(),
                new SignifyProperties.Vocabulary("x.csv", false, 100)));
    }

    private static SignEntry entry(String path, MediaType type, VerificationStatus status) {
        return new SignEntry(1L, "hello", "hello", SignCategory.WORD, path, type, null, null, null, status, null);
    }

    @Test
    void rejectsPathsOutsideTheMediaRoot() {
        assertThat(storage.resolveRelative("../secret.mp4")).isEmpty();
        assertThat(storage.resolveRelative("words/../../secret.mp4")).isEmpty();
        assertThat(storage.resolveRelative(dir.resolve("secret.mp4").toString())).isEmpty();
        assertThat(storage.resolveRelative("words\\..\\..\\secret.mp4")).isEmpty();
        assertThat(storage.resolveRelative("")).isEmpty();
        assertThat(storage.resolveRelative("words/hello.mp4")).isPresent();
    }

    @Test
    void statusReflectsFilePresenceTypeAndVerification() {
        assertThat(storage.statusOf(entry(null, null, VerificationStatus.UNVERIFIED)))
                .isEqualTo(SignStatus.MEDIA_MISSING);
        assertThat(storage.statusOf(entry("words/missing.mp4", MediaType.VIDEO, VerificationStatus.VERIFIED)))
                .isEqualTo(SignStatus.MEDIA_MISSING);
        assertThat(storage.statusOf(entry("words/hello.mp4", MediaType.IMAGE, VerificationStatus.VERIFIED)))
                .as("extension does not match declared type").isEqualTo(SignStatus.MEDIA_MISSING);
        assertThat(storage.statusOf(entry("words/hello.mp4", MediaType.VIDEO, VerificationStatus.UNVERIFIED)))
                .isEqualTo(SignStatus.AVAILABLE_UNVERIFIED);
        assertThat(storage.statusOf(entry("words/hello.mp4", MediaType.VIDEO, VerificationStatus.VERIFIED)))
                .isEqualTo(SignStatus.VERIFIED);
    }
}
