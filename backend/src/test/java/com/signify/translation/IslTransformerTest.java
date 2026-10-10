package com.signify.translation;

import static org.assertj.core.api.Assertions.assertThat;

import com.signify.nlp.NlpPipeline;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IslTransformerTest {

    private static NlpPipeline nlp;
    private final IslTransformer transformer = new IslTransformer();
    private static final Set<String> PHRASES = Set.of("sign language", "thank you", "how are you");

    @BeforeAll
    static void load() {
        nlp = new NlpPipeline();
    }

    private SentenceTransformation run(String text) {
        return transformer.transform(0, nlp.analyze(text).get(0), PHRASES::contains);
    }

    private static List<String> glosses(SentenceTransformation t) {
        return t.units().stream().map(GlossUnit::gloss).toList();
    }

    private static List<String> ruleIds(SentenceTransformation t) {
        return t.rules().stream().map(RuleApplication::ruleId).toList();
    }

    @Test
    void groupsPhrasesFiltersFunctionWordsAndPlacesVerbsLast() {
        SentenceTransformation t = run("I want to learn Indian Sign Language.");
        assertThat(glosses(t)).containsExactly("I", "INDIAN", "SIGN LANGUAGE", "WANT", "LEARN");
        assertThat(ruleIds(t)).contains("R1_PHRASE", "R2_FILTER", "R6_VERB_FINAL");
        assertThat(t.tokens()).filteredOn(a -> a.text().equals("to"))
                .singleElement().extracting(TokenAnalysis::action).isEqualTo("DROPPED");
    }

    @Test
    void movesTimeWordsFirstAndUsesLemmas() {
        SentenceTransformation t = run("She went to school yesterday.");
        assertThat(glosses(t)).containsExactly("YESTERDAY", "SHE", "SCHOOL", "GO");
        assertThat(ruleIds(t)).contains("R3_BASE_FORM", "R4_TIME_FRONT", "R6_VERB_FINAL");
    }

    @Test
    void preservesNegationAfterTheVerb() {
        SentenceTransformation t = run("I don't like apples.");
        assertThat(glosses(t)).containsExactly("I", "APPLE", "LIKE", "NOT");
        assertThat(ruleIds(t)).contains("R5_NEGATION_END");
    }

    @Test
    void movesQuestionWordToTheEndAndSkipsVerbFinal() {
        SentenceTransformation t = run("What is your name?");
        assertThat(t.question()).isTrue();
        assertThat(glosses(t)).containsExactly("YOUR", "NAME", "WHAT");
        assertThat(ruleIds(t)).contains("R7_WH_END").doesNotContain("R6_VERB_FINAL");
    }

    @Test
    void phraseIsNotBrokenByQuestionRule() {
        SentenceTransformation t = run("How are you?");
        assertThat(glosses(t)).containsExactly("HOW ARE YOU");
    }

    @Test
    void keepsEnglishOrderForCoordinatedClausesAndExplains() {
        SentenceTransformation t = run("I eat rice and she drinks tea.");
        assertThat(glosses(t)).containsExactly("I", "EAT", "RICE", "AND", "SHE", "DRINK", "TEA");
        assertThat(ruleIds(t)).doesNotContain("R6_VERB_FINAL");
        assertThat(t.notes()).singleElement().asString().contains("R6");
    }

    @Test
    void normalisesObjectPronouns() {
        SentenceTransformation t = run("She helps me.");
        assertThat(glosses(t)).containsExactly("SHE", "I", "HELP");
    }

    @Test
    void dropsArticlesAndCopula() {
        SentenceTransformation t = run("The book is new.");
        assertThat(glosses(t)).containsExactly("BOOK", "NEW");
    }

    @Test
    void everyRuleIsDocumented() {
        assertThat(IslTransformer.RULES).extracting(RuleInfo::id).containsExactly(
                "R1_PHRASE", "R2_FILTER", "R3_BASE_FORM", "R4_TIME_FRONT", "R5_NEGATION_END",
                "R6_VERB_FINAL", "R7_WH_END");
    }
}
