package com.signify.vocabulary;

import com.signify.config.SignifyProperties;
import com.signify.media.MediaStorage;
import com.signify.web.PageResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class VocabularyService {

    private final VocabularyRepository repository;
    private final MediaStorage storage;
    private final SignifyProperties properties;

    public VocabularyService(VocabularyRepository repository, MediaStorage storage, SignifyProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
    }

    public PageResponse<VocabularyEntryView> page(String query, SignCategory category, int page, int size) {
        String q = query == null ? null : TermNormalizer.normalize(query);
        long total = repository.count(category, q);
        List<VocabularyEntryView> items = repository.findPage(category, q, page * size, size).stream()
                .map(this::view)
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    public VocabularyStats stats() {
        List<SignEntry> all = repository.findAll();
        long words = count(all, SignCategory.WORD);
        long phrases = count(all, SignCategory.PHRASE);
        long registered = all.stream().filter(SignEntry::hasMediaRegistered).count();
        long verified = 0;
        long unverified = 0;
        long lettersWithMedia = 0;
        for (SignEntry e : all) {
            SignStatus status = storage.statusOf(e);
            if (status == SignStatus.VERIFIED) {
                verified++;
            } else if (status == SignStatus.AVAILABLE_UNVERIFIED) {
                unverified++;
            }
            if (status.playable() && e.category() == SignCategory.LETTER) {
                lettersWithMedia++;
            }
        }
        long available = verified + unverified;
        double coverage = all.isEmpty() ? 0 : Math.round(available * 1000.0 / all.size()) / 10.0;
        return new VocabularyStats(all.size(), words, phrases, count(all, SignCategory.LETTER),
                count(all, SignCategory.DIGIT), words + phrases, properties.vocabulary().targetSize(),
                registered, available, all.size() - available, verified, unverified, lettersWithMedia, coverage);
    }

    public static SignCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SignCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("category must be one of WORD, PHRASE, LETTER, DIGIT");
        }
    }

    private VocabularyEntryView view(SignEntry e) {
        SignStatus status = storage.statusOf(e);
        boolean available = status.playable();
        return new VocabularyEntryView(e.id(), e.term(), e.normalizedTerm(), e.category(), e.verificationStatus(),
                status, e.hasMediaRegistered(), available, available, status == SignStatus.VERIFIED,
                e.mediaType(), available ? MediaStorage.mediaUrl(e.id()) : null,
                e.source(), e.license(), e.attribution(), e.notes());
    }

    private static long count(List<SignEntry> all, SignCategory category) {
        return all.stream().filter(e -> e.category() == category).count();
    }
}
