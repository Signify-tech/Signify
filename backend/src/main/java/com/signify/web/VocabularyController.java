package com.signify.web;

import com.signify.vocabulary.VocabularyEntryView;
import com.signify.vocabulary.VocabularyService;
import com.signify.vocabulary.VocabularyStats;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vocabulary")
public class VocabularyController {

    private final VocabularyService service;

    public VocabularyController(VocabularyService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<VocabularyEntryView> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(200) int size) {
        return service.page(null, VocabularyService.parseCategory(category), page, size);
    }

    @GetMapping("/search")
    public PageResponse<VocabularyEntryView> search(
            @RequestParam @NotBlank(message = "q must not be blank") @Size(max = 120) String q,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(200) int size) {
        return service.page(q, VocabularyService.parseCategory(category), page, size);
    }

    @GetMapping("/stats")
    public VocabularyStats stats() {
        return service.stats();
    }
}
