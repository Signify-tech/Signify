package com.signify.web;

import com.signify.media.MediaStorage;
import com.signify.vocabulary.VocabularyRepository;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final VocabularyRepository repository;
    private final MediaStorage storage;

    public HealthController(VocabularyRepository repository, MediaStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean dbUp;
        try {
            body.put("vocabularyEntries", repository.countAll());
            dbUp = true;
        } catch (RuntimeException e) {
            dbUp = false;
        }
        body.put("status", dbUp ? "UP" : "DEGRADED");
        body.put("database", dbUp ? "UP" : "DOWN");
        body.put("nlp", "UP");
        body.put("mediaDirectory", Files.isDirectory(storage.root()) ? "PRESENT" : "MISSING");
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
