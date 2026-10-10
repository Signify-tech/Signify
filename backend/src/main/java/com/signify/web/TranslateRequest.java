package com.signify.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TranslateRequest(
        @NotBlank(message = "Enter some English text to translate.")
        @Size(max = 500, message = "Text must be 500 characters or fewer.")
        String text) {
}
