package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RetryTextTest {

    @Test
    void dropsWhitespaceBeforeACommaOnlyOnALineHoldingAJsonArray() {
        assertThat(RetryText.jsonCommas("CMD [\"a\" , \"b\"]")).isEqualTo("CMD [\"a\", \"b\"]");
        assertThat(RetryText.jsonCommas("RUN echo \"a\" , b")).isEqualTo("RUN echo \"a\" , b");
    }

    @Test
    void writesAFlagValuesColonDashDefaultAsADash() {
        assertThat(RetryText.flagDefaults("COPY --chown=${UID:-1001} a b")).isEqualTo("COPY --chown=${UID-1001} a b");
        assertThat(RetryText.flagDefaults("RUN echo ${A:-b}")).isEqualTo("RUN echo ${A:-b}");
    }

    @Test
    void givesEachPlainArgNameItsOwnValueAndAnEmptyDefaultQuotes() {
        assertThat(RetryText.of("ARG A B=1 C=\n")).isEqualTo("ARG A=$A B=1 C=\"\"\n");
    }

    @Test
    void aCommentHoldingAHeredocMarkerOpensNoHeredoc() {
        assertThat(RetryText.of("FROM a\n# cat <<EOF\nARG A B\n")).isEqualTo("FROM a\n\nARG A=$A B=$B\n");
    }
}
