package com.signify.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvParserTest {

    @Test
    void parsesQuotedFieldsEscapedQuotesAndBlankLines() {
        List<List<String>> rows = CsvParser.parse("﻿a,b,c\r\n\"x, y\",\"say \"\"hi\"\"\",\n\n1,2,3");
        assertThat(rows).containsExactly(
                List.of("a", "b", "c"),
                List.of("x, y", "say \"hi\"", ""),
                List.of("1", "2", "3"));
    }

    @Test
    void normalizerLowercasesAndCollapsesWhitespace() {
        assertThat(TermNormalizer.normalize("  Thank   YOU! ")).isEqualTo("thank you");
        assertThat(TermNormalizer.normalize("Don’t")).isEqualTo("don't");
    }
}
