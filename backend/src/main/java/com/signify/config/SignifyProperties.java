package com.signify.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "signify")
public record SignifyProperties(
        @DefaultValue("http://localhost:5173") List<String> corsAllowedOrigins,
        @DefaultValue("../media") String mediaDir,
        @DefaultValue Vocabulary vocabulary) {

    public record Vocabulary(
            @DefaultValue("../data/vocabulary.csv") String seedFile,
            @DefaultValue("true") boolean seedOnStartup,
            @DefaultValue("100") int targetSize) {
    }
}
