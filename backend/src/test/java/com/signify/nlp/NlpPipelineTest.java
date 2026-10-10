package com.signify.nlp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NlpPipelineTest {

    private static NlpPipeline nlp;

    @BeforeAll
    static void load() {
        nlp = new NlpPipeline();
    }

    @Test
    void tokenizesTagsAndLemmatizes() {
        List<NlpToken> tokens = nlp.analyze("The children are eating apples.").get(0);
        assertThat(tokens).extracting(NlpToken::text)
                .containsExactly("The", "children", "are", "eating", "apples", ".");
        assertThat(tokens).extracting(NlpToken::pos)
                .containsExactly("DET", "NOUN", "AUX", "VERB", "NOUN", "PUNCT");
        assertThat(tokens).extracting(NlpToken::lemma)
                .containsExactly("the", "child", "be", "eat", "apple", ".");
    }

    @Test
    void splitsSentences() {
        List<List<NlpToken>> sentences = nlp.analyze("Hello there. How are you?");
        assertThat(sentences).hasSize(2);
        assertThat(sentences.get(1).get(0).sentence()).isEqualTo(1);
    }

    @Test
    void splitsNegatedContraction() {
        List<NlpToken> tokens = nlp.analyze("I don't know.").get(0);
        assertThat(tokens).extracting(NlpToken::text).containsExactly("I", "do", "n't", "know", ".");
        assertThat(tokens.get(2).lemma()).isEqualTo("not");
        assertThat(tokens.get(2).pos()).isEqualTo("PART");
    }

    @Test
    void splitContractionHandlesPossessive() {
        List<String[]> parts = NlpPipeline.splitContraction("Zebra's", "PROPN+PART", "zebra+'s");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).containsExactly("Zebra", "zebra", "PROPN");
        assertThat(parts.get(1)).containsExactly("'s", "'s", "PART");
    }

    @Test
    void unknownLemmaFallsBackToLowercaseToken() {
        assertThat(NlpPipeline.splitContraction("Xyzzy", "PROPN", "O").get(0)[1]).isEqualTo("xyzzy");
    }
}
