package com.signify.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class VocabularySeederTest {

    @Autowired
    VocabularySeeder seeder;

    @Autowired
    VocabularyRepository repository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        Path media = Files.createTempDirectory("seed-media");
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:seeder;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("signify.media-dir", media::toString);
        registry.add("signify.vocabulary.seed-on-startup", () -> "false");
    }

    @Test
    void importsUpdatesAndRejectsInvalidRows() {
        String header = String.join(",", VocabularySeeder.HEADER) + "\n";
        VocabularySeeder.SeedReport first = seeder.importCsv(header
                + "Water,,word,,,,,,,\n"
                + "thank you,,PHRASE,,,,,,,\n"
                + "water,,WORD,,,,,,,duplicate\n"
                + "hello,,WORD,words/hello.mp4,,,,,,\n"
                + "hi,,WORD,,,,,,VERIFIED,\n"
                + "AB,,LETTER,,,,,,,\n"
                + "x,,NOUN,,,,,,,\n"
                + "/abs,,WORD,/etc/passwd,IMAGE,,,,,\n");
        assertThat(first.inserted()).isEqualTo(2);
        assertThat(first.rejected()).hasSize(6);
        assertThat(first.rejected()).anySatisfy(r -> assertThat(r).contains("duplicate"));
        assertThat(first.rejected()).anySatisfy(r -> assertThat(r).contains("media_path and media_type"));
        assertThat(first.rejected()).anySatisfy(r -> assertThat(r).contains("VERIFIED requires"));

        SignEntry water = repository.findByCategoryAndTerm(SignCategory.WORD, "water").orElseThrow();
        assertThat(water.term()).isEqualTo("Water");
        assertThat(water.verificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);

        VocabularySeeder.SeedReport second = seeder.importCsv(header
                + "water,,WORD,words/water.mp4,VIDEO,ISLRTC,Permission ref 1,Credit,UNVERIFIED,\n");
        assertThat(second.updated()).isEqualTo(1);
        SignEntry updated = repository.findByCategoryAndTerm(SignCategory.WORD, "water").orElseThrow();
        assertThat(updated.mediaPath()).isEqualTo("words/water.mp4");
        assertThat(updated.mediaType()).isEqualTo(MediaType.VIDEO);
    }
}
