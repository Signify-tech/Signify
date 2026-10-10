package com.signify.media;

import com.signify.vocabulary.SignEntry;
import com.signify.vocabulary.VocabularyRepository;
import com.signify.web.NotFoundException;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves media only for registered vocabulary entries, addressed by entry id, never by a client-supplied
 * path. Spring MVC handles HTTP Range requests for {@link Resource} bodies, so videos can seek.
 */
@RestController
public class MediaController {

    private final VocabularyRepository repository;
    private final MediaStorage storage;

    public MediaController(VocabularyRepository repository, MediaStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @GetMapping("/api/media/{id}")
    public ResponseEntity<Resource> media(@PathVariable long id) {
        SignEntry entry = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No vocabulary entry with id " + id));
        Path file = storage.resolve(entry)
                .orElseThrow(() -> new NotFoundException("Sign media not added yet for '" + entry.term() + "'"));
        String contentType = entry.mediaType().contentTypeFor(file.getFileName().toString()).orElseThrow();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }
}
