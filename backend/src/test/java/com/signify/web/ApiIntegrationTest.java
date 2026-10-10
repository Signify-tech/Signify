package com.signify.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    private static final Path MEDIA = createMediaDir();

    @Autowired
    MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:signify;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("signify.media-dir", MEDIA::toString);
        registry.add("signify.vocabulary.seed-file", () -> "src/test/resources/test-vocabulary.csv");
        registry.add("signify.vocabulary.seed-on-startup", () -> "true");
    }

    /** Small stand-in files; they are test fixtures only, not sign media. */
    private static Path createMediaDir() {
        try {
            Path root = Files.createTempDirectory("signify-media");
            Files.createDirectories(root.resolve("words"));
            Files.createDirectories(root.resolve("letters"));
            Files.write(root.resolve("words/hello.mp4"), new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
            Files.write(root.resolve("words/friend.png"), new byte[] {1, 2, 3});
            Files.write(root.resolve("letters/e.png"), new byte[] {1, 2, 3});
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private org.springframework.test.web.servlet.ResultActions translate(String text) throws Exception {
        return mvc.perform(post("/api/translate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\"}"));
    }

    @Test
    void translatesSentenceWithFullResponseContract() throws Exception {
        translate("I want to learn Indian Sign Language.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalText").value("I want to learn Indian Sign Language."))
                .andExpect(jsonPath("$.sentenceCount").value(1))
                .andExpect(jsonPath("$.tokens", hasSize(8)))
                .andExpect(jsonPath("$.tokens[2].action").value("DROPPED"))
                .andExpect(jsonPath("$.transformedSequence",
                        contains("I", "INDIAN", "SIGN LANGUAGE", "WANT", "LEARN")))
                .andExpect(jsonPath("$.signs", hasSize(5)))
                .andExpect(jsonPath("$.signs[2].representation").value("PHRASE"))
                .andExpect(jsonPath("$.signs[2].mappingSource").value("DICTIONARY"))
                .andExpect(jsonPath("$.signs[2].status").value("MEDIA_MISSING"))
                .andExpect(jsonPath("$.signs[2].placeholder").value(true))
                .andExpect(jsonPath("$.signs[2].mediaUrl").doesNotExist())
                .andExpect(jsonPath("$.signs[2].message").value("Sign media not added yet."))
                .andExpect(jsonPath("$.summary.dictionaryMatches").value(5))
                .andExpect(jsonPath("$.rulesApplied[*].ruleId", hasItem("R6_VERB_FINAL")))
                .andExpect(jsonPath("$.processingTimeMs").isNumber());
    }

    @Test
    void unknownWordIsFingerspelledInOrder() throws Exception {
        translate("ZEBRA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.units[0].mappingSource").value("FINGERSPELLING"))
                .andExpect(jsonPath("$.units[0].status").value("NOT_IN_VOCABULARY"))
                .andExpect(jsonPath("$.signs[*].gloss", contains("Z", "E", "B", "R", "A")))
                .andExpect(jsonPath("$.signs[0].representation").value("LETTER"))
                .andExpect(jsonPath("$.signs[0].fingerspelledWord").value("ZEBRA"))
                .andExpect(jsonPath("$.signs[0].letterIndex").value(1))
                .andExpect(jsonPath("$.signs[0].letterCount").value(5))
                .andExpect(jsonPath("$.signs[0].status").value("MEDIA_MISSING"))
                .andExpect(jsonPath("$.signs[0].message", containsString("placeholder")))
                .andExpect(jsonPath("$.signs[1].status").value("AVAILABLE_UNVERIFIED"))
                .andExpect(jsonPath("$.signs[1].mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.warnings[0]", containsString("ZEBRA")));
    }

    @Test
    void distinguishesAllFourMediaStates() throws Exception {
        // hello: verified video; friend: unverified image; book: entry without media; xylophone: not in vocabulary
        translate("hello friend book xylophone")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.units[*].status",
                        contains("VERIFIED", "AVAILABLE_UNVERIFIED", "MEDIA_MISSING", "NOT_IN_VOCABULARY")))
                .andExpect(jsonPath("$.signs[0].verified").value(true))
                .andExpect(jsonPath("$.signs[0].playable").value(true))
                .andExpect(jsonPath("$.signs[0].mediaType").value("VIDEO"))
                .andExpect(jsonPath("$.signs[0].mediaUrl", startsWith("/api/media/")))
                .andExpect(jsonPath("$.signs[0].attribution").value("Test fixture"))
                .andExpect(jsonPath("$.signs[1].verified").value(false))
                .andExpect(jsonPath("$.signs[1].playable").value(true))
                .andExpect(jsonPath("$.signs[2].playable").value(false))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("not been verified"))));
    }

    @Test
    void registeredButMissingFileIsMediaMissing() throws Exception {
        translate("school")
                .andExpect(jsonPath("$.signs[0].status").value("MEDIA_MISSING"))
                .andExpect(jsonPath("$.signs[0].mediaUrl").doesNotExist());
    }

    @Test
    void mixedSentenceWithQuestionAndPlural() throws Exception {
        translate("What is your name? Thank you for the books.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentenceCount").value(2))
                .andExpect(jsonPath("$.transformedSequence[2]").value("WHAT"))
                .andExpect(jsonPath("$.units[3].gloss").value("THANK YOU"))
                .andExpect(jsonPath("$.units[?(@.gloss == 'BOOK')].matchedBy", contains("lemma")))
                .andExpect(jsonPath("$.units[?(@.gloss == 'FOR')].mappingSource", contains("FINGERSPELLING")))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("Question detected"))));
    }

    @Test
    void rejectsBlankTooLongAndMalformedInput() throws Exception {
        translate("   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("text"));
        translate("a".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("500")));
        mvc.perform(post("/api/translate").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/translate").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/translate")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void punctuationOnlyInputProducesWarningNotError() throws Exception {
        translate("?!").andExpect(status().isOk())
                .andExpect(jsonPath("$.signs", hasSize(0)))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("No signable words"))));
    }

    @Test
    void vocabularyListsSearchesAndPaginates() throws Exception {
        mvc.perform(get("/api/vocabulary").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalItems").value(25))
                .andExpect(jsonPath("$.totalPages").value(5));
        mvc.perform(get("/api/vocabulary").param("category", "LETTER"))
                .andExpect(jsonPath("$.totalItems").value(5));
        mvc.perform(get("/api/vocabulary/search").param("q", "Hello"))
                .andExpect(jsonPath("$.items[0].term").value("hello"))
                .andExpect(jsonPath("$.items[0].status").value("VERIFIED"))
                .andExpect(jsonPath("$.items[0].mediaUrl", startsWith("/api/media/")));
        mvc.perform(get("/api/vocabulary/search").param("q", "good"))
                .andExpect(jsonPath("$.items[0].category").value("PHRASE"));
        mvc.perform(get("/api/vocabulary/search").param("q", "100%_"))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void vocabularyRejectsBadParameters() throws Exception {
        mvc.perform(get("/api/vocabulary/search")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/vocabulary/search").param("q", " ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/vocabulary").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/vocabulary").param("page", "x")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/vocabulary").param("category", "NOUN")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("WORD")));
    }

    @Test
    void statsAreComputedFromData() throws Exception {
        mvc.perform(get("/api/vocabulary/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEntries").value(25))
                .andExpect(jsonPath("$.words").value(17))
                .andExpect(jsonPath("$.phrases").value(3))
                .andExpect(jsonPath("$.letters").value(5))
                .andExpect(jsonPath("$.mediaRegistered").value(4))
                .andExpect(jsonPath("$.mediaAvailable").value(3))
                .andExpect(jsonPath("$.verified").value(1))
                .andExpect(jsonPath("$.lettersWithMedia").value(1))
                .andExpect(jsonPath("$.mediaCoveragePercent").value(12.0));
    }

    @Test
    void servesOnlyRegisteredAvailableMedia() throws Exception {
        String body = mvc.perform(get("/api/vocabulary/search").param("q", "hello"))
                .andReturn().getResponse().getContentAsString();
        String url = com.jayway.jsonpath.JsonPath.read(body, "$.items[0].mediaUrl");
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType("video/mp4"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get(url).header("Range", "bytes=0-3"))
                .andExpect(status().isPartialContent());
        mvc.perform(get("/api/media/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/media/..%2F..%2Fetc%2Fpasswd")).andExpect(status().isBadRequest());
    }

    @Test
    void healthReportsDatabaseAndMedia() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.vocabularyEntries").value(25))
                .andExpect(jsonPath("$.mediaDirectory").value("PRESENT"));
    }

    @Test
    void rulesEndpointListsImplementedRules() throws Exception {
        mvc.perform(get("/api/rules")).andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[0].description").isString());
    }

    @Test
    void corsAllowsConfiguredFrontendOrigin() throws Exception {
        mvc.perform(get("/api/health").header("Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(get("/api/health").header("Origin", "http://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownApiPathReturnsJsonError() throws Exception {
        mvc.perform(get("/api/nope")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/api/nope"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }
}
