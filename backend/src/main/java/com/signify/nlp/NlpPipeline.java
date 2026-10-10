package com.signify.nlp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagFormat;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import org.springframework.stereotype.Component;

/**
 * Sentence detection, tokenization, POS tagging and lemmatization with Apache OpenNLP.
 *
 * <p>The models are the Universal Dependencies English-EWT models published on Maven Central
 * (org.apache.opennlp:opennlp-models-*-en). They are loaded from the classpath once; the
 * {@code *ME} classes are not thread-safe, so a fresh instance is created per request.
 */
@Component
public class NlpPipeline {

    private static final String SENTENCE_MODEL = "/opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin";
    private static final String TOKEN_MODEL = "/opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin";
    private static final String POS_MODEL = "/opennlp-en-ud-ewt-pos-1.3-2.5.4.bin";
    private static final String LEMMA_MODEL = "/opennlp-en-ud-ewt-lemmas-1.3-2.5.4.bin";

    private final SentenceModel sentenceModel;
    private final TokenizerModel tokenizerModel;
    private final POSModel posModel;
    private final LemmatizerModel lemmatizerModel;

    public NlpPipeline() {
        try {
            sentenceModel = new SentenceModel(open(SENTENCE_MODEL));
            tokenizerModel = new TokenizerModel(open(TOKEN_MODEL));
            posModel = new POSModel(open(POS_MODEL));
            lemmatizerModel = new LemmatizerModel(open(LEMMA_MODEL));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load OpenNLP models", e);
        }
    }

    /** Analyses the text and returns the tokens of each sentence. */
    public List<List<NlpToken>> analyze(String text) {
        SentenceDetectorME sentenceDetector = new SentenceDetectorME(sentenceModel);
        TokenizerME tokenizer = new TokenizerME(tokenizerModel);
        POSTaggerME tagger = new POSTaggerME(posModel, POSTagFormat.UD);
        LemmatizerME lemmatizer = new LemmatizerME(lemmatizerModel);

        List<List<NlpToken>> sentences = new ArrayList<>();
        int index = 0;
        for (String sentence : sentenceDetector.sentDetect(text)) {
            String[] tokens = tokenizer.tokenize(sentence);
            if (tokens.length == 0) {
                continue;
            }
            String[] tags = tagger.tag(tokens);
            String[] lemmas = lemmatizer.lemmatize(tokens, tags);
            List<NlpToken> out = new ArrayList<>();
            for (int i = 0; i < tokens.length; i++) {
                for (String[] part : splitContraction(tokens[i], tags[i], lemmas[i])) {
                    out.add(new NlpToken(index++, sentences.size(), part[0], part[1], part[2]));
                }
            }
            sentences.add(out);
        }
        return sentences;
    }

    /**
     * The UD models tag contractions as a single token with combined tags, e.g.
     * {@code don't -> AUX+PART / do+not}. Split them so each part can be handled on its own.
     * Returns {text, lemma, pos} triples.
     */
    static List<String[]> splitContraction(String token, String tag, String lemma) {
        String safeLemma = lemma == null || lemma.isBlank() || "O".equals(lemma)
                ? token.toLowerCase(Locale.ROOT) : lemma.toLowerCase(Locale.ROOT);
        if (!tag.contains("+")) {
            return List.<String[]>of(new String[] {token, safeLemma, tag});
        }
        String[] tagParts = tag.split("\\+");
        String[] lemmaParts = safeLemma.split("\\+");
        if (tagParts.length != 2) {
            return List.<String[]>of(new String[] {token, safeLemma, tagParts[0]});
        }
        String lower = token.toLowerCase(Locale.ROOT);
        int cut;
        if (lower.endsWith("n't") || lower.endsWith("n’t")) {
            cut = token.length() - 3;
        } else {
            cut = Math.max(token.lastIndexOf('\''), token.lastIndexOf('’'));
        }
        if (cut <= 0) {
            return List.<String[]>of(new String[] {token, safeLemma, tagParts[0]});
        }
        String firstLemma = lemmaParts.length > 0 ? cleanLemma(lemmaParts[0]) : "";
        String secondLemma = lemmaParts.length > 1 ? lemmaParts[1].trim() : "";
        String first = token.substring(0, cut);
        String second = token.substring(cut);
        return List.of(
                new String[] {first, firstLemma.isEmpty() ? first.toLowerCase(Locale.ROOT) : firstLemma, tagParts[0]},
                new String[] {second, secondLemma.isEmpty() ? second.toLowerCase(Locale.ROOT) : secondLemma, tagParts[1]});
    }

    private static String cleanLemma(String lemma) {
        return lemma.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
    }

    private static InputStream open(String resource) throws IOException {
        InputStream in = NlpPipeline.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("Model not found on classpath: " + resource);
        }
        return in;
    }
}
