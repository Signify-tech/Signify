package com.signify.translation;

import com.signify.nlp.NlpToken;
import com.signify.translation.GlossUnit.Role;
import com.signify.vocabulary.TermNormalizer;
import com.signify.vocabulary.VocabularySeeder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Applies the project's explicit ISL-oriented rules to one analysed sentence.
 *
 * <p>These rules encode patterns commonly described for ISL (time first, verb-final simple clauses,
 * negation and question words at the end). They are a simplification of ISL grammar, not a complete
 * model of it, and their output should be reviewed by fluent ISL signers.
 */
@Component
public class IslTransformer {

    public static final List<RuleInfo> RULES = List.of(
            new RuleInfo("R1_PHRASE", "Phrase grouping",
                    "Consecutive words that form a registered multi-word vocabulary phrase (for example "
                            + "'thank you') are kept together as one sign unit. Matching uses surface forms and "
                            + "lemmas, longest phrase first, up to " + VocabularySeeder.MAX_PHRASE_WORDS + " words."),
            new RuleInfo("R2_FILTER", "Function-word filtering",
                    "Removes articles (a, an, the), forms of the auxiliaries be, do and have, the infinitive "
                            + "marker 'to', the prepositions 'to' and 'of', the possessive 's, and punctuation. "
                            + "Content words, pronouns, modals and negation are kept."),
            new RuleInfo("R3_BASE_FORM", "Lemma and pronoun base forms",
                    "Each kept word is looked up by its lemma ('learning' becomes LEARN, 'children' becomes "
                            + "CHILD). Object pronouns use their base form: me to I, us to WE, him to HE, "
                            + "her to SHE, them to THEY."),
            new RuleInfo("R4_TIME_FRONT", "Time words first",
                    "Time words (today, tomorrow, yesterday, now, tonight, later, soon) move to the start of "
                            + "the sentence."),
            new RuleInfo("R5_NEGATION_END", "Negation after the verb",
                    "Negation (not, n't, never, and 'no' before a noun) is preserved as NOT, NEVER or NO and "
                            + "placed after the verb, at the end of the sentence."),
            new RuleInfo("R6_VERB_FINAL", "Verb after object (simple clauses only)",
                    "In a simple statement with exactly one verb group followed by other words, the verb "
                            + "group moves to the end (subject, object, verb). Questions and sentences with "
                            + "conjunctions or several verb groups keep their original order."),
            new RuleInfo("R7_WH_END", "Question word last",
                    "In questions, the question word (what, where, when, who, whom, whose, why, how, which) "
                            + "moves to the end of the sentence."));

    private static final Map<String, String> RULE_NAMES =
            RULES.stream().collect(Collectors.toMap(RuleInfo::id, RuleInfo::name));

    private static final Set<String> ARTICLES = Set.of("a", "an", "the");
    private static final Set<String> DROPPED_AUXILIARIES = Set.of("be", "do", "have");
    private static final Set<String> DROPPED_PREPOSITIONS = Set.of("to", "of");
    private static final Set<String> TIME_WORDS =
            Set.of("today", "tomorrow", "yesterday", "now", "tonight", "later", "soon");
    private static final Set<String> WH_WORDS =
            Set.of("what", "where", "when", "who", "whom", "whose", "why", "how", "which");
    private static final Map<String, String> PRONOUN_BASE =
            Map.of("me", "i", "us", "we", "him", "he", "her", "she", "them", "they");

    public SentenceTransformation transform(int sentence, List<NlpToken> tokens, Predicate<String> isPhrase) {
        return transform(sentence, tokens, isPhrase, false);
    }

    /** Explicit alphabet input is identified for the whole request, never inferred from a sentence fragment. */
    public SentenceTransformation transform(int sentence, List<NlpToken> tokens, Predicate<String> isPhrase,
                                             boolean alphabetInput) {
        List<TokenAnalysis> analysis = new ArrayList<>();
        List<GlossUnit> units = new ArrayList<>();
        List<RuleApplication> rules = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        List<String> grouped = new ArrayList<>();
        List<String> baseForms = new ArrayList<>();

        boolean question = !alphabetInput && isQuestion(tokens);

        for (int i = 0; i < tokens.size(); ) {
            NlpToken token = tokens.get(i);

            // R1: longest registered phrase starting here
            GlossUnit phrase = matchPhrase(sentence, tokens, i, isPhrase);
            if (phrase != null) {
                for (NlpToken t : phrase.tokens()) {
                    analysis.add(analysisOf(t, "GROUPED", "R1_PHRASE",
                            "Part of the phrase '" + phrase.key() + "'", phrase.gloss()));
                }
                grouped.add(phrase.gloss());
                units.add(phrase);
                i += phrase.tokens().size();
                continue;
            }
            i++;

            // R2: function-word filtering
            // OpenNLP can label an attached form such as "b?!" as a single punctuation token.
            String dropReason = alphabetInput && TermNormalizer.normalize(token.text()).matches("[a-z]")
                    ? null : dropReason(token);
            if (dropReason != null) {
                analysis.add(analysisOf(token, "DROPPED", "R2_FILTER", dropReason, null));
                if (!token.isPunctuation()) {
                    dropped.add(token.text());
                }
                continue;
            }

            // R3: lemma / pronoun base form
            String surface = TermNormalizer.normalize(token.text());
            String lemma = TermNormalizer.normalize(token.lemma());
            String key = alphabetInput || lemma.isEmpty() ? surface : lemma;
            if ("PRON".equals(token.pos()) && PRONOUN_BASE.containsKey(surface)) {
                key = PRONOUN_BASE.get(surface);
            }
            if (key.isEmpty()) {
                analysis.add(analysisOf(token, "DROPPED", "R2_FILTER", "No letters or digits to sign", null));
                continue;
            }
            Role role = alphabetInput ? Role.OTHER : roleOf(token, key, question);
            if (role == Role.NEGATION && "n't".equals(surface)) {
                key = "not";
            }
            if (!key.equals(surface)) {
                baseForms.add(token.text() + " → " + key.toUpperCase(java.util.Locale.ROOT));
            }
            GlossUnit unit = new GlossUnit(key, surface, token.pos(), role, false, sentence, List.of(token));
            analysis.add(analysisOf(token, "KEPT", key.equals(surface) ? null : "R3_BASE_FORM",
                    alphabetInput ? "Explicit alphabet letter" : key.equals(surface)
                            ? "Content word kept" : "Base form used for lookup", unit.gloss()));
            units.add(unit);
        }

        if (!grouped.isEmpty()) {
            rules.add(applied("R1_PHRASE", sentence, "Grouped: " + String.join(", ", grouped), null, null));
        }
        if (!dropped.isEmpty()) {
            rules.add(applied("R2_FILTER", sentence, "Removed: " + String.join(", ", dropped), null, null));
        }
        if (!baseForms.isEmpty()) {
            rules.add(applied("R3_BASE_FORM", sentence, String.join(", ", baseForms), null, null));
        }

        List<GlossUnit> ordered = reorder(sentence, units, question, rules, notes);
        return new SentenceTransformation(sentence, question, analysis, ordered, rules, notes);
    }

    /** R4 to R7, in that order. Each rule is recorded only when it changes the order. */
    private List<GlossUnit> reorder(int sentence, List<GlossUnit> units, boolean question,
                                    List<RuleApplication> rules, List<String> notes) {
        List<GlossUnit> time = units.stream().filter(u -> u.role() == Role.TIME).toList();
        List<GlossUnit> negation = units.stream().filter(u -> u.role() == Role.NEGATION).toList();
        List<GlossUnit> wh = units.stream().filter(u -> u.role() == Role.WH).toList();
        List<GlossUnit> middle = units.stream()
                .filter(u -> u.role() != Role.TIME && u.role() != Role.NEGATION && u.role() != Role.WH)
                .collect(Collectors.toCollection(ArrayList::new));

        List<GlossUnit> current = new ArrayList<>(units);

        // R4: time words first
        if (!time.isEmpty()) {
            List<GlossUnit> next = new ArrayList<>(time);
            current.stream().filter(u -> u.role() != Role.TIME).forEach(next::add);
            current = record(rules, "R4_TIME_FRONT", sentence, "Moved to front: " + glosses(time), current, next);
        }

        // R5: negation at the end (before any question word)
        if (!negation.isEmpty()) {
            List<GlossUnit> next = new ArrayList<>(time);
            next.addAll(middle);
            next.addAll(negation);
            current.stream().filter(u -> u.role() == Role.WH).forEach(next::add);
            current = record(rules, "R5_NEGATION_END", sentence, "Negation kept and placed last: "
                    + glosses(negation), current, next);
        }

        // R6: verb group after the object, only for simple statements
        List<GlossUnit> reorderedMiddle = verbFinal(middle, question, notes, sentence);
        if (reorderedMiddle != null) {
            List<GlossUnit> next = new ArrayList<>(time);
            next.addAll(reorderedMiddle);
            current.stream().filter(u -> u.role() == Role.NEGATION || u.role() == Role.WH).forEach(next::add);
            current = record(rules, "R6_VERB_FINAL", sentence, "Verb group moved after the object", current, next);
            middle = new ArrayList<>(reorderedMiddle);
        }

        // R7: question words last
        if (!wh.isEmpty()) {
            List<GlossUnit> next = new ArrayList<>(time);
            next.addAll(middle);
            next.addAll(negation);
            next.addAll(wh);
            current = record(rules, "R7_WH_END", sentence, "Question word moved to the end: " + glosses(wh),
                    current, next);
        }
        return current;
    }

    /**
     * Returns the middle units with the single verb group moved to the end, or null when the rule does
     * not apply. Adds a note when the rule is skipped for a reason the user should know about.
     */
    private List<GlossUnit> verbFinal(List<GlossUnit> middle, boolean question, List<String> notes, int sentence) {
        List<int[]> groups = new ArrayList<>();
        for (int i = 0; i < middle.size(); i++) {
            if (middle.get(i).role() == Role.VERB) {
                int start = i;
                while (i + 1 < middle.size() && middle.get(i + 1).role() == Role.VERB) {
                    i++;
                }
                groups.add(new int[] {start, i});
            }
        }
        if (groups.isEmpty()) {
            return null;
        }
        if (question) {
            return null;
        }
        boolean hasConjunction = middle.stream().anyMatch(u -> u.role() == Role.CONJUNCTION);
        if (groups.size() > 1 || hasConjunction) {
            notes.add("Sentence " + (sentence + 1) + ": verb-final ordering (R6) was not applied because the "
                    + "sentence has " + (hasConjunction ? "a conjunction" : "more than one verb group")
                    + "; the English word order was kept.");
            return null;
        }
        int[] group = groups.get(0);
        if (group[1] == middle.size() - 1) {
            return null;
        }
        List<GlossUnit> out = new ArrayList<>(middle.subList(0, group[0]));
        out.addAll(middle.subList(group[1] + 1, middle.size()));
        out.addAll(middle.subList(group[0], group[1] + 1));
        return out;
    }

    private GlossUnit matchPhrase(int sentence, List<NlpToken> tokens, int start, Predicate<String> isPhrase) {
        for (int n = Math.min(VocabularySeeder.MAX_PHRASE_WORDS, tokens.size() - start); n >= 2; n--) {
            List<NlpToken> window = tokens.subList(start, start + n);
            if (window.stream().anyMatch(NlpToken::isPunctuation)) {
                continue;
            }
            String surface = TermNormalizer.normalize(window.stream().map(NlpToken::text)
                    .collect(Collectors.joining(" ")));
            String lemma = TermNormalizer.normalize(window.stream().map(NlpToken::lemma)
                    .collect(Collectors.joining(" ")));
            String key = isPhrase.test(surface) ? surface : isPhrase.test(lemma) ? lemma : null;
            if (key != null) {
                return new GlossUnit(key, surface, "PHRASE", Role.OTHER, true, sentence, List.copyOf(window));
            }
        }
        return null;
    }

    static String dropReason(NlpToken token) {
        String lower = token.lowerText();
        String lemma = token.lemma();
        if (token.isPunctuation()) {
            return "Punctuation";
        }
        if ("DET".equals(token.pos()) && ARTICLES.contains(lower)) {
            return "Article";
        }
        if ("AUX".equals(token.pos()) && DROPPED_AUXILIARIES.contains(lemma)) {
            return "Auxiliary or copula verb '" + lemma + "'";
        }
        if ("PART".equals(token.pos()) && "to".equals(lower)) {
            return "Infinitive marker 'to'";
        }
        if ("ADP".equals(token.pos()) && DROPPED_PREPOSITIONS.contains(lower)) {
            return "Preposition '" + lower + "'";
        }
        if ("PART".equals(token.pos()) && (lower.equals("'s") || lower.equals("’s") || lower.equals("'"))) {
            return "Possessive marker";
        }
        return null;
    }

    static Role roleOf(NlpToken token, String key, boolean question) {
        String lower = token.lowerText();
        String pos = token.pos();
        if (key.equals("not") || lower.equals("n't") || lower.equals("n’t") || key.equals("never")
                || (key.equals("no") && "DET".equals(pos))) {
            return Role.NEGATION;
        }
        if (question && WH_WORDS.contains(key) && Set.of("PRON", "ADV", "DET", "SCONJ").contains(pos)) {
            return Role.WH;
        }
        if (TIME_WORDS.contains(key) && Set.of("ADV", "NOUN").contains(pos)) {
            return Role.TIME;
        }
        if ("VERB".equals(pos) || "AUX".equals(pos)) {
            return Role.VERB;
        }
        if ("CCONJ".equals(pos) || "SCONJ".equals(pos)) {
            return Role.CONJUNCTION;
        }
        return Role.OTHER;
    }

    static boolean isQuestion(List<NlpToken> tokens) {
        if (tokens.stream().anyMatch(t -> t.text().equals("?"))) {
            return true;
        }
        NlpToken first = tokens.stream().filter(t -> !t.isPunctuation()).findFirst().orElse(null);
        NlpToken last = tokens.get(tokens.size() - 1);
        return first != null && WH_WORDS.contains(first.lowerText())
                && !last.text().equals(".") && !last.text().equals("!");
    }

    private static List<GlossUnit> record(List<RuleApplication> rules, String id, int sentence, String detail,
                                          List<GlossUnit> before, List<GlossUnit> after) {
        if (!glosses(before).equals(glosses(after))) {
            rules.add(applied(id, sentence, detail, glosses(before), glosses(after)));
        }
        return after;
    }

    private static RuleApplication applied(String id, int sentence, String detail, String before, String after) {
        return new RuleApplication(id, RULE_NAMES.get(id), sentence, detail, before, after);
    }

    private static String glosses(List<GlossUnit> units) {
        return units.stream().map(GlossUnit::gloss).collect(Collectors.joining(" "));
    }

    private static TokenAnalysis analysisOf(NlpToken t, String action, String rule, String reason, String gloss) {
        return new TokenAnalysis(t.index(), t.sentence(), t.text(), t.lemma(), t.pos(), action, rule, reason, gloss);
    }
}
