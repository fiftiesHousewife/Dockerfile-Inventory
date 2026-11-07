package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void aRowJoinsFieldsWithCommasAndEndsInCrLf() {
        assertThat(Csv.row("a", "", "b")).isEqualTo("a,,b\r\n");
    }

    @Test
    void aFieldWithACommaQuoteOrLineBreakIsQuotedWithQuotesDoubled() {
        assertThat(Csv.field("plain")).isEqualTo("plain");
        assertThat(Csv.field("a,b")).isEqualTo("\"a,b\"");
        assertThat(Csv.field("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(Csv.field("two\nlines")).isEqualTo("\"two\nlines\"");
    }

    @Test
    void orEmptyWritesNullAsAnEmptyField() {
        assertThat(Csv.orEmpty(null)).isEmpty();
        assertThat(Csv.orEmpty(12)).isEqualTo("12");
    }
}
