package com.signify.vocabulary;

import com.signify.config.SignifyProperties;
import com.signify.media.MediaStorage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Imports the vocabulary/media manifest CSV into the database on startup (insert or update by normalised
 * term). Rows missing from the CSV are left untouched. Invalid rows are skipped and logged.
 */
@Component
public class VocabularySeeder implements ApplicationRunner {

    static final List<String> HEADER = List.of("term", "normalized_term", "category", "media_path", "media_type",
            "source", "license", "attribution", "verification_status", "notes");

    /** Longest phrase the translator tries to match. */
    public static final int MAX_PHRASE_WORDS = 4;

    private static final Logger log = LoggerFactory.getLogger(VocabularySeeder.class);

    private final SignifyProperties properties;
    private final VocabularyRepository repository;
    private final MediaStorage storage;

    public VocabularySeeder(SignifyProperties properties, VocabularyRepository repository, MediaStorage storage) {
        this.properties = properties;
        this.repository = repository;
        this.storage = storage;
    }

    public record SeedReport(int inserted, int updated, List<String> rejected) {
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!properties.vocabulary().seedOnStartup()) {
            return;
        }
        Path file = Path.of(properties.vocabulary().seedFile()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            log.warn("Vocabulary seed file not found: {}", file);
            return;
        }
        SeedReport report = importCsv(Files.readString(file, StandardCharsets.UTF_8));
        log.info("Vocabulary import from {}: {} inserted, {} updated, {} rejected",
                file, report.inserted(), report.updated(), report.rejected().size());
        report.rejected().forEach(r -> log.warn("Rejected vocabulary row: {}", r));
    }

    public SeedReport importCsv(String content) {
        List<List<String>> rows = CsvParser.parse(content);
        if (rows.isEmpty()) {
            return new SeedReport(0, 0, List.of());
        }
        List<String> header = rows.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        if (!header.containsAll(Set.of("term", "category"))) {
            throw new IllegalArgumentException("Vocabulary CSV must have a header row with at least term,category");
        }
        int inserted = 0;
        int updated = 0;
        List<String> rejected = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 1; i < rows.size(); i++) {
            int line = i + 1;
            Map<String, String> row = new HashMap<>();
            for (int c = 0; c < header.size() && c < rows.get(i).size(); c++) {
                row.put(header.get(c), rows.get(i).get(c).trim());
            }
            try {
                SignEntry entry = toEntry(row);
                Integer previous = seen.put(entry.category() + ":" + entry.normalizedTerm(), line);
                if (previous != null) {
                    rejected.add("line " + line + ": duplicate of line " + previous + " ('" + entry.normalizedTerm() + "')");
                    continue;
                }
                if (repository.findByCategoryAndTerm(entry.category(), entry.normalizedTerm()).isPresent()) {
                    repository.update(entry);
                    updated++;
                } else {
                    repository.insert(entry);
                    inserted++;
                }
            } catch (IllegalArgumentException e) {
                rejected.add("line " + line + ": " + e.getMessage());
            }
        }
        return new SeedReport(inserted, updated, rejected);
    }

    SignEntry toEntry(Map<String, String> row) {
        String term = blankToNull(row.get("term"));
        if (term == null) {
            throw new IllegalArgumentException("term is required");
        }
        SignCategory category = parseEnum(SignCategory.class, row.get("category"), "category");
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        String normalized = TermNormalizer.normalize(
                blankToNull(row.get("normalized_term")) == null ? term : row.get("normalized_term"));
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw new IllegalArgumentException("normalized term is empty or longer than 120 characters");
        }
        if ((category == SignCategory.LETTER && !normalized.matches("[a-z]"))
                || (category == SignCategory.DIGIT && !normalized.matches("[0-9]"))) {
            throw new IllegalArgumentException("LETTER entries must be a-z and DIGIT entries 0-9");
        }
        int words = normalized.split(" ").length;
        if (category == SignCategory.PHRASE && (words < 2 || words > MAX_PHRASE_WORDS)) {
            throw new IllegalArgumentException("PHRASE entries must have 2 to " + MAX_PHRASE_WORDS + " words");
        }
        if (category == SignCategory.WORD && words != 1) {
            throw new IllegalArgumentException("WORD entries must be a single word; use PHRASE for multi-word terms");
        }
        String mediaPath = blankToNull(row.get("media_path"));
        MediaType mediaType = parseEnum(MediaType.class, row.get("media_type"), "media_type");
        if ((mediaPath == null) != (mediaType == null)) {
            throw new IllegalArgumentException("media_path and media_type must be given together");
        }
        if (mediaPath != null) {
            if (storage.resolveRelative(mediaPath).isEmpty()) {
                throw new IllegalArgumentException("media_path must be a relative path inside the media directory");
            }
            if (mediaType.contentTypeFor(mediaPath).isEmpty()) {
                throw new IllegalArgumentException("media_path extension does not match media_type " + mediaType);
            }
        }
        VerificationStatus verification = parseEnum(VerificationStatus.class,
                row.get("verification_status"), "verification_status");
        if (verification == null) {
            verification = VerificationStatus.UNVERIFIED;
        }
        if (verification == VerificationStatus.VERIFIED && mediaPath == null) {
            throw new IllegalArgumentException("VERIFIED requires a registered media file");
        }
        return new SignEntry(null, term, normalized, category, mediaPath, mediaType,
                blankToNull(row.get("source")), blankToNull(row.get("license")),
                blankToNull(row.get("attribution")), verification, blankToNull(row.get("notes")));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid " + field + " '" + v + "'");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
