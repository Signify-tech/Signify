package com.signify.translation;

import com.signify.media.MediaStorage;
import com.signify.nlp.NlpPipeline;
import com.signify.nlp.NlpToken;
import com.signify.vocabulary.SignCategory;
import com.signify.vocabulary.SignEntry;
import com.signify.vocabulary.SignStatus;
import com.signify.vocabulary.TermNormalizer;
import com.signify.vocabulary.VocabularyRepository;
import com.signify.vocabulary.VocabularySeeder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * English to ISL-oriented sign sequence:
 * NLP analysis, rule-based transformation, dictionary lookup, fingerspelling fallback.
 */
@Service
public class TranslationService {

    static final String DICTIONARY = "DICTIONARY";
    static final String FINGERSPELLING = "FINGERSPELLING";

    private final NlpPipeline nlp;
    private final IslTransformer transformer;
    private final VocabularyRepository repository;
    private final MediaStorage storage;

    public TranslationService(NlpPipeline nlp, IslTransformer transformer, VocabularyRepository repository,
                              MediaStorage storage) {
        this.nlp = nlp;
        this.transformer = transformer;
        this.repository = repository;
        this.storage = storage;
    }

    public TranslationResult translate(String text) {
        long start = System.nanoTime();
        List<List<NlpToken>> sentences = nlp.analyze(text);
        // A single letter (with optional sentence punctuation) names an alphabet sign, including I.
        // In all other input, I remains a pronoun and articles keep their normal filtering.
        boolean alphabetInput = text.matches("\\s*[A-Za-z][\\s.!?]*");

        // One query for every word, lemma and phrase candidate in the input.
        Map<String, SignEntry> words = new LinkedHashMap<>();
        Set<String> phrases = new LinkedHashSet<>();
        for (SignEntry e : repository.findByNormalizedTerms(candidateKeys(sentences))) {
            if (e.category() == SignCategory.PHRASE) {
                phrases.add(e.normalizedTerm());
            }
            if (e.category() == SignCategory.WORD || e.category() == SignCategory.PHRASE) {
                words.put(e.normalizedTerm(), e);
            }
        }

        List<TokenAnalysis> tokens = new ArrayList<>();
        List<GlossUnit> units = new ArrayList<>();
        List<RuleApplication> rules = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean anyQuestion = false;
        for (int s = 0; s < sentences.size(); s++) {
            SentenceTransformation t = transformer.transform(s, sentences.get(s), phrases::contains, alphabetInput);
            tokens.addAll(t.tokens());
            units.addAll(t.units());
            rules.addAll(t.rules());
            warnings.addAll(t.notes());
            anyQuestion |= t.question();
        }

        // Lookup each unit; unknown words fall back to fingerspelling.
        List<Match> matches = new ArrayList<>();
        Set<String> letterKeys = new LinkedHashSet<>();
        for (GlossUnit unit : units) {
            Match match = alphabetInput ? new Match(null, null) : lookup(unit, words);
            matches.add(match);
            if (match.entry() == null) {
                letterKeys.addAll(letters(unit.key()));
            }
        }
        Map<String, SignEntry> letters = repository.findByNormalizedTerms(letterKeys).stream()
                .filter(e -> e.category() == SignCategory.LETTER || e.category() == SignCategory.DIGIT)
                .collect(Collectors.toMap(SignEntry::normalizedTerm, e -> e, (a, b) -> a));

        List<SignItem> signs = new ArrayList<>();
        List<SequenceUnit> sequence = new ArrayList<>();
        List<String> fingerspelled = new ArrayList<>();
        int dictionaryMatches = 0;
        for (int u = 0; u < units.size(); u++) {
            GlossUnit unit = units.get(u);
            Match match = matches.get(u);
            int first = signs.size() + 1;
            if (match.entry() != null) {
                dictionaryMatches++;
                SignStatus status = storage.statusOf(match.entry());
                signs.add(item(signs.size() + 1, unit, u, match.entry(), status, DICTIONARY, null, null, null));
                sequence.add(new SequenceUnit(u, unit.sentence(), unit.gloss(), unit.role().name(), DICTIONARY,
                        match.matchedBy(), match.entry().id(), status, first, 1));
            } else {
                List<String> chars = letters(unit.key());
                if (chars.isEmpty()) {
                    warnings.add("'" + unit.gloss() + "' has no A-Z or 0-9 characters and could not be fingerspelled.");
                } else if (!alphabetInput) {
                    fingerspelled.add(unit.gloss());
                }
                for (int i = 0; i < chars.size(); i++) {
                    SignEntry letter = letters.get(chars.get(i));
                    SignStatus status = letter == null ? SignStatus.NOT_IN_VOCABULARY : storage.statusOf(letter);
                    signs.add(item(signs.size() + 1, unit, u, letter, status, FINGERSPELLING,
                            chars.get(i), i + 1, chars.size()));
                }
                SignEntry alphabetEntry = alphabetInput && chars.size() == 1 ? letters.get(chars.get(0)) : null;
                sequence.add(new SequenceUnit(u, unit.sentence(), unit.gloss(), unit.role().name(), FINGERSPELLING,
                        null, alphabetEntry == null ? null : alphabetEntry.id(),
                        alphabetEntry == null ? SignStatus.NOT_IN_VOCABULARY : storage.statusOf(alphabetEntry),
                        first, chars.size()));
            }
        }

        int playable = (int) signs.stream().filter(SignItem::playable).count();
        int verified = (int) signs.stream().filter(SignItem::verified).count();
        if (units.isEmpty()) {
            warnings.add("No signable words were found in the input.");
        }
        if (!fingerspelled.isEmpty()) {
            warnings.add("Not in the vocabulary, fingerspelled letter by letter: " + String.join(", ", fingerspelled)
                    + ".");
        }
        if (!signs.isEmpty() && playable == 0) {
            warnings.add("No sign media has been added for this sequence yet. The player shows labelled "
                    + "placeholders, not ISL signs.");
        } else if (playable > verified) {
            warnings.add("Some media in this sequence has not been verified as authentic ISL.");
        }
        if (anyQuestion) {
            warnings.add("Question detected. ISL marks questions with facial expression and other non-manual "
                    + "features, which this sequence does not represent.");
        }

        int dropped = (int) tokens.stream().filter(t -> t.action().equals("DROPPED")).count();
        TranslationResult.Summary summary = new TranslationResult.Summary(tokens.size(), tokens.size() - dropped,
                dropped, units.size(), dictionaryMatches, fingerspelled.size(), signs.size(), playable, verified,
                signs.size() - playable);
        double ms = Math.round((System.nanoTime() - start) / 10_000.0) / 100.0;
        return new TranslationResult(text, sentences.size(), tokens,
                units.stream().map(GlossUnit::gloss).toList(), sequence, rules, signs, summary, warnings, ms);
    }

    private record Match(SignEntry entry, String matchedBy) {
    }

    private static Match lookup(GlossUnit unit, Map<String, SignEntry> words) {
        if (words.containsKey(unit.key())) {
            return new Match(words.get(unit.key()), unit.phrase() ? "phrase" : "lemma");
        }
        if (!unit.phrase() && words.containsKey(unit.surface())) {
            return new Match(words.get(unit.surface()), "surface form");
        }
        if (!unit.phrase() && ("NOUN".equals(unit.pos()) || "PROPN".equals(unit.pos()))) {
            for (String singular : singularCandidates(unit.key())) {
                if (words.containsKey(singular)) {
                    return new Match(words.get(singular), "singular form");
                }
            }
        }
        return new Match(null, null);
    }

    /** Every 1 to MAX_PHRASE_WORDS word window of surface forms and lemmas, plus single-word fallbacks. */
    private static Set<String> candidateKeys(List<List<NlpToken>> sentences) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(List.of("i", "we", "he", "she", "they", "not"));
        for (List<NlpToken> sentence : sentences) {
            for (int i = 0; i < sentence.size(); i++) {
                NlpToken t = sentence.get(i);
                keys.add(TermNormalizer.normalize(t.text()));
                keys.add(TermNormalizer.normalize(t.lemma()));
                keys.addAll(singularCandidates(TermNormalizer.normalize(t.lemma())));
                StringBuilder surface = new StringBuilder(t.text());
                StringBuilder lemma = new StringBuilder(t.lemma());
                for (int n = 2; n <= VocabularySeeder.MAX_PHRASE_WORDS && i + n - 1 < sentence.size(); n++) {
                    NlpToken next = sentence.get(i + n - 1);
                    surface.append(' ').append(next.text());
                    lemma.append(' ').append(next.lemma());
                    keys.add(TermNormalizer.normalize(surface.toString()));
                    keys.add(TermNormalizer.normalize(lemma.toString()));
                }
            }
        }
        keys.remove("");
        return keys;
    }

    static List<String> singularCandidates(String word) {
        List<String> out = new ArrayList<>();
        if (word.length() > 4 && word.endsWith("ies")) {
            out.add(word.substring(0, word.length() - 3) + "y");
        }
        if (word.length() > 3 && word.endsWith("es")) {
            out.add(word.substring(0, word.length() - 2));
        }
        if (word.length() > 2 && word.endsWith("s") && !word.endsWith("ss")) {
            out.add(word.substring(0, word.length() - 1));
        }
        return out;
    }

    /** The letters and digits of a word, in order, for fingerspelling. */
    static List<String> letters(String key) {
        return key.toLowerCase(Locale.ROOT).codePoints()
                .filter(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))
                .mapToObj(Character::toString)
                .toList();
    }

    private static SignItem item(int position, GlossUnit unit, int unitIndex, SignEntry entry, SignStatus status,
                                 String source, String letter, Integer letterIndex, Integer letterCount) {
        boolean fingerspelling = FINGERSPELLING.equals(source);
        SignCategory representation = fingerspelling
                ? (Character.isDigit(letter.charAt(0)) ? SignCategory.DIGIT : SignCategory.LETTER)
                : entry.category();
        String gloss = fingerspelling ? letter.toUpperCase(Locale.ROOT) : entry.term().toUpperCase(Locale.ROOT);
        boolean playable = status.playable();
        String message = switch (status) {
            case VERIFIED -> null;
            case AVAILABLE_UNVERIFIED -> "Media available but not yet verified as authentic ISL.";
            case MEDIA_MISSING -> fingerspelling
                    ? "Fingerspelling placeholder. ISL alphabet media not added yet."
                    : "Sign media not added yet.";
            case NOT_IN_VOCABULARY -> "Fingerspelling placeholder. This character has no vocabulary entry.";
        };
        return new SignItem(position, unit.sentence(), unitIndex,
                entry == null ? null : entry.id(),
                gloss, representation, source, status,
                status == SignStatus.VERIFIED, playable, !playable,
                playable ? entry.mediaType() : null,
                playable ? MediaStorage.mediaUrl(entry.id()) : null,
                playable ? entry.source() : null,
                playable ? entry.license() : null,
                playable ? entry.attribution() : null,
                fingerspelling ? unit.gloss() : null,
                letterIndex, letterCount, message);
    }
}
