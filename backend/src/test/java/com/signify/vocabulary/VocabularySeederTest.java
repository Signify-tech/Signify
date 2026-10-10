package com.signify.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.signify.config.SignifyProperties;
import com.signify.media.MediaStorage;
import java.util.List;

@SpringBootTest
class VocabularySeederTest {

    @Autowired
    VocabularySeeder seeder;

    @Autowired
    VocabularyRepository repository;

    @Autowired JdbcClient jdbc;

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

    @Test
    void repeatedImportsPreserveIdsTimestampsAndRowsAbsentFromTheManifest() {
        String csv = "term,category\nexpansion,WORD\nretained,WORD\n";
        seeder.importCsv(csv);
        SignEntry original = repository.findByCategoryAndTerm(SignCategory.WORD, "expansion").orElseThrow();
        var timestamps = jdbc.sql("SELECT created_at, updated_at FROM sign_entry WHERE id = :id")
                .param("id", original.id()).query().singleRow();
        seeder.importCsv("term,category\nexpansion,WORD\nnewword,WORD\n");
        assertThat(repository.findByCategoryAndTerm(SignCategory.WORD, "expansion").orElseThrow()).isEqualTo(original);
        assertThat(jdbc.sql("SELECT created_at, updated_at FROM sign_entry WHERE id = :id")
                .param("id", original.id()).query().singleRow()).isEqualTo(timestamps);
        assertThat(repository.findByCategoryAndTerm(SignCategory.WORD, "retained")).isPresent();
        assertThat(repository.findByCategoryAndTerm(SignCategory.WORD, "newword")).isPresent();
    }

    @Test
    void strictImportValidatesAllRowsBeforeWritingAndAllowsCompleteNewMedia() throws Exception {
        Path root = Files.createTempDirectory("strict-media");
        Files.createDirectory(root.resolve("words"));
        Files.copy(Path.of("../media/words/hello__INCLUDE__00566__MVI_0029.mp4"), root.resolve("words/hello.mp4"));
        var props = new SignifyProperties(List.of(), root.toString(),
                new SignifyProperties.Vocabulary("unused.csv", false, 100));
        var strict = new VocabularySeeder(props, repository, new MediaStorage(props), true);
        String header = String.join(",", VocabularySeeder.HEADER) + "\n";
        long count = repository.countAll();
        assertThatThrownBy(() -> strict.importCsv("term,category\nlicensednew,WORD\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("every manifest column");
        assertThatThrownBy(() -> strict.importCsv(header
                + "validnew,,WORD,,,,,,,\nmissingnew,,WORD,words/missing.mp4,VIDEO,,,,,\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("before database writes");
        assertThat(repository.countAll()).isEqualTo(count);
        assertThatThrownBy(() -> strict.importCsv(header
                + "verifiednew,,WORD,words/hello.mp4,VIDEO,,,,VERIFIED,\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("source, license and attribution");
        assertThatThrownBy(() -> strict.importCsv(header
                + "verifiednew,,WORD,words/hello.mp4,VIDEO,Test,unknown,Test credit,VERIFIED,\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("resolved rights");
        var report = strict.importCsv(header
                + "licensednew,,WORD,words/hello.mp4,VIDEO,Test,Test only,Test credit,UNVERIFIED,\n");
        assertThat(report.inserted()).isEqualTo(1);
        assertThat(report.rejected()).isEmpty();
        long id = repository.findByCategoryAndTerm(SignCategory.WORD, "licensednew").orElseThrow().id();
        strict.importCsv(header + "licensednew,,WORD,,,,,,,\n");
        assertThat(repository.findByCategoryAndTerm(SignCategory.WORD, "licensednew").orElseThrow().id()).isEqualTo(id);
    }
}
