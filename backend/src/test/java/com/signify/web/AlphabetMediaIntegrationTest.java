package com.signify.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.signify.media.MediaStorage;
import com.signify.vocabulary.MediaType;
import com.signify.vocabulary.SignCategory;
import com.signify.vocabulary.SignEntry;
import com.signify.vocabulary.SignStatus;
import com.signify.vocabulary.VerificationStatus;
import com.signify.vocabulary.VocabularyRepository;
import com.signify.vocabulary.VocabularySeeder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Exercises the shipped manifest and alphabet files through the existing importer and API. */
@SpringBootTest
@AutoConfigureMockMvc
class AlphabetMediaIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired VocabularyRepository repository;
    @Autowired VocabularySeeder seeder;
    @Autowired MediaStorage storage;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:alphabet;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("signify.media-dir", () -> "../media");
        registry.add("signify.vocabulary.seed-file", () -> "../data/vocabulary.csv");
        registry.add("signify.vocabulary.seed-on-startup", () -> "true");
    }

    private List<SignEntry> letters() {
        return repository.findAll().stream().filter(e -> e.category() == SignCategory.LETTER).toList();
    }

    @Test
    @Transactional
    void startupImporterUpdatesExistingLettersWithoutChangingIdsOrMetadata() throws Exception {
        List<SignEntry> original = letters();
        assertThat(original).hasSize(26);
        long count = repository.countAll();
        StringBuilder legacy = new StringBuilder(
                "term,category,verification_status\n");
        original.forEach(e -> legacy.append(e.term()).append(",LETTER,UNVERIFIED\n"));
        assertThat(seeder.importCsv(legacy.toString()).updated()).isEqualTo(26);
        assertThat(letters()).allSatisfy(e -> assertThat(e.mediaPath()).isNull());

        seeder.run(new DefaultApplicationArguments());
        assertThat(letters()).containsExactlyElementsOf(original);
        assertThat(repository.countAll()).isEqualTo(count);
        VocabularySeeder.SeedReport repeated = seeder.importCsv(Files.readString(Path.of("../data/vocabulary.csv")));
        assertThat(repeated.inserted()).isZero();
        assertThat(repeated.updated()).isEqualTo((int) count);
        assertThat(repeated.rejected()).isEmpty();
    }

    @Test
    void servesEveryRegisteredAlphabetJpegWithUnverifiedStatus() throws Exception {
        assertThat(letters()).hasSize(26);
        for (SignEntry entry : letters()) {
            assertThat(entry.mediaPath()).isEqualTo("letters/" + entry.term() + ".jpg");
            assertThat(entry.mediaType()).isEqualTo(MediaType.IMAGE);
            assertThat(entry.verificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
            assertThat(storage.statusOf(entry)).isEqualTo(SignStatus.AVAILABLE_UNVERIFIED);
            mvc.perform(get(MediaStorage.mediaUrl(entry.id())))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("image/jpeg"))
                    .andExpect(content().bytes(Files.readAllBytes(storage.resolve(entry).orElseThrow())));
        }
    }

    @Test
    void unknownWordReturnsPlayableAlphabetImagesInOrder() throws Exception {
        mvc.perform(post("/api/translate").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"ZEBRA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signs[*].gloss", contains("Z", "E", "B", "R", "A")))
                .andExpect(jsonPath("$.summary.playableItems").value(5))
                .andExpect(jsonPath("$.summary.verifiedItems").value(0))
                .andExpect(jsonPath("$.summary.placeholderItems").value(0))
                .andExpect(jsonPath("$.signs[*].mappingSource", contains(
                        "FINGERSPELLING", "FINGERSPELLING", "FINGERSPELLING", "FINGERSPELLING", "FINGERSPELLING")))
                .andExpect(jsonPath("$.signs[*].mediaType", contains("IMAGE", "IMAGE", "IMAGE", "IMAGE", "IMAGE")))
                .andExpect(jsonPath("$.signs[*].mediaUrl", contains(
                        MediaStorage.mediaUrl(repository.findByCategoryAndTerm(SignCategory.LETTER, "z").orElseThrow().id()),
                        MediaStorage.mediaUrl(repository.findByCategoryAndTerm(SignCategory.LETTER, "e").orElseThrow().id()),
                        MediaStorage.mediaUrl(repository.findByCategoryAndTerm(SignCategory.LETTER, "b").orElseThrow().id()),
                        MediaStorage.mediaUrl(repository.findByCategoryAndTerm(SignCategory.LETTER, "r").orElseThrow().id()),
                        MediaStorage.mediaUrl(repository.findByCategoryAndTerm(SignCategory.LETTER, "a").orElseThrow().id()))));
    }

    static Stream<String> alphabetInputs() {
        return java.util.stream.IntStream.rangeClosed('A', 'Z').boxed()
                .flatMap(c -> {
                    String upper = Character.toString(c);
                    String lower = upper.toLowerCase(java.util.Locale.ROOT);
                    return Stream.of(upper, lower, "  " + upper + ".  ", lower + "?!");
                });
    }

    @ParameterizedTest
    @MethodSource("alphabetInputs")
    @ValueSource(strings = {" A ", "a.", " B! ", "C?", " D... ", " E?! ", " I. "})
    void explicitAlphabetInputUsesLetterEntryIncludingI(String input) throws Exception {
        String letter = input.trim().substring(0, 1).toLowerCase(java.util.Locale.ROOT);
        SignEntry entry = repository.findByCategoryAndTerm(SignCategory.LETTER, letter).orElseThrow();
        mvc.perform(post("/api/translate").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + input + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signs.length()").value(1))
                .andExpect(jsonPath("$.signs[0].gloss").value(entry.term()))
                .andExpect(jsonPath("$.signs[0].entryId").value(entry.id()))
                .andExpect(jsonPath("$.signs[0].representation").value("LETTER"))
                .andExpect(jsonPath("$.signs[0].mediaUrl").value(MediaStorage.mediaUrl(entry.id())))
                .andExpect(jsonPath("$.signs[0].playable").value(true))
                .andExpect(jsonPath("$.signs[0].verified").value(false))
                .andExpect(jsonPath("$.units[0].status").value("AVAILABLE_UNVERIFIED"))
                .andExpect(jsonPath("$.summary.fingerspelledWords").value(0));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "A brother|BROTHER", "An apple|APPLE", "I am a student|I,STUDENT",
            "I want to learn Indian Sign Language.|I,INDIAN,SIGN LANGUAGE,WANT,LEARN",
            "What is your name?|YOUR,NAME,WHAT",
            "She went to school yesterday.|YESTERDAY,SHE,SCHOOL,GO",
            "I don't like cold tea.|I,COLD,TEA,LIKE,NOT",
            "Thank you, my friend Zebra!|THANK YOU,MY,FRIEND,ZEBRA"
    })
    void sentencesFollowTheDocumentedRules(String input, String expected) throws Exception {
        mvc.perform(post("/api/translate").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + input + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transformedSequence", contains((Object[]) expected.split(","))));
        if (input.startsWith("I ")) {
            SignEntry pronoun = repository.findByCategoryAndTerm(SignCategory.WORD, "i").orElseThrow();
            mvc.perform(post("/api/translate").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"" + input + "\"}"))
                    .andExpect(jsonPath("$.signs[0].entryId").value(pronoun.id()))
                    .andExpect(jsonPath("$.signs[0].representation").value("WORD"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"brother", "hello", "water"})
    @Transactional
    void wordLookupDistinguishesPresentAndMissingVideos(String word) throws Exception {
        SignEntry entry = repository.findByCategoryAndTerm(SignCategory.WORD, word).orElseThrow();
        mvc.perform(post("/api/translate").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + word + "\"}"))
                .andExpect(jsonPath("$.signs[0].entryId").value(entry.id()))
                .andExpect(jsonPath("$.signs[0].mediaType").value("VIDEO"))
                .andExpect(jsonPath("$.signs[0].mediaUrl").value(MediaStorage.mediaUrl(entry.id())))
                .andExpect(jsonPath("$.signs[0].status").value("AVAILABLE_UNVERIFIED"));
        seeder.importCsv("term,category,media_path,media_type\n" + word + ",WORD,words/missing.mp4,VIDEO\n");
        mvc.perform(post("/api/translate").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + word + "\"}"))
                .andExpect(jsonPath("$.signs[0].entryId").value(entry.id()))
                .andExpect(jsonPath("$.signs[0].status").value("MEDIA_MISSING"))
                .andExpect(jsonPath("$.signs[0].playable").value(false))
                .andExpect(jsonPath("$.signs[0].mediaUrl").doesNotExist());
        mvc.perform(get(MediaStorage.mediaUrl(entry.id()))).andExpect(status().isNotFound());
        seeder.importCsv("term,category\n" + word + ",WORD\n");
        mvc.perform(post("/api/translate").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + word + "\"}"))
                .andExpect(jsonPath("$.signs[0].entryId").value(entry.id()))
                .andExpect(jsonPath("$.signs[0].mappingSource").value("DICTIONARY"))
                .andExpect(jsonPath("$.signs[0].status").value("MEDIA_MISSING"))
                .andExpect(jsonPath("$.signs[0].mediaType").doesNotExist())
                .andExpect(jsonPath("$.signs[0].mediaUrl").doesNotExist());
    }

    @Test
    void servesRealWordVideosWithCorrectMimeBytesAndRanges() throws Exception {
        List<SignEntry> videos = repository.findAll().stream().filter(e -> e.mediaType() == MediaType.VIDEO).toList();
        assertThat(videos).hasSize(38);
        for (SignEntry entry : videos) {
            byte[] bytes = Files.readAllBytes(storage.resolve(entry).orElseThrow());
            mvc.perform(get(MediaStorage.mediaUrl(entry.id())))
                    .andExpect(status().isOk()).andExpect(content().contentType("video/mp4"))
                    .andExpect(content().bytes(bytes));
            mvc.perform(get(MediaStorage.mediaUrl(entry.id())).header("Range", "bytes=0-15"))
                    .andExpect(status().isPartialContent())
                    .andExpect(header().string("Content-Range", "bytes 0-15/" + bytes.length))
                    .andExpect(content().bytes(java.util.Arrays.copyOf(bytes, 16)));
        }
    }

    @Test
    void mixedSequenceIncludesRealVideoFingerspellingAndMissingWordMedia() throws Exception {
        mvc.perform(post("/api/translate").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"brother Zebra book\"}"))
                .andExpect(jsonPath("$.signs[*].gloss", contains("BROTHER", "Z", "E", "B", "R", "A", "BOOK")))
                .andExpect(jsonPath("$.signs[0].mediaType").value("VIDEO"))
                .andExpect(jsonPath("$.signs[1].mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.signs[6].status").value("MEDIA_MISSING"))
                .andExpect(jsonPath("$.summary.playableItems").value(6))
                .andExpect(jsonPath("$.summary.placeholderItems").value(1));
    }

    @Test
    @Transactional
    void missingAlphabetFileKeepsSequenceAndOtherAvailableImages() throws Exception {
        seeder.importCsv("term,category,media_path,media_type\nZ,LETTER,letters/missing.jpg,IMAGE\n");
        mvc.perform(post("/api/translate").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"ZEBRA\"}"))
                .andExpect(jsonPath("$.signs[*].gloss", contains("Z", "E", "B", "R", "A")))
                .andExpect(jsonPath("$.signs[0].status").value("MEDIA_MISSING"))
                .andExpect(jsonPath("$.signs[0].mediaUrl").doesNotExist())
                .andExpect(jsonPath("$.summary.playableItems").value(4))
                .andExpect(jsonPath("$.summary.placeholderItems").value(1));
    }
}
