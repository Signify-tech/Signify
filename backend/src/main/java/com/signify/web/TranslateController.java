package com.signify.web;

import com.signify.translation.IslTransformer;
import com.signify.translation.RuleInfo;
import com.signify.translation.TranslationResult;
import com.signify.translation.TranslationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TranslateController {

    private final TranslationService translationService;

    public TranslateController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @PostMapping("/translate")
    public TranslationResult translate(@Valid @RequestBody TranslateRequest request) {
        return translationService.translate(request.text().strip());
    }

    /** The transformation rules implemented by the pipeline, for documentation in the UI. */
    @GetMapping("/rules")
    public List<RuleInfo> rules() {
        return IslTransformer.RULES;
    }
}
