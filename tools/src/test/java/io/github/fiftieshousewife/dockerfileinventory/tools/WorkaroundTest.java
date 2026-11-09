package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorkaroundTest {

    @ParameterizedTest
    @CsvSource(delimiterString = " => ", value = {
        "CMD [\"a\" , \"b\"] => json-comma",
        "RUN --mount=type=cache,target=${CACHE:-/c} make => flag-default",
        "ARG VERSION= => arg-empty",
        "ARG ONE TWO => arg-names",
        "RUN a {bs}{nl}  {bs}{nl}  b => lone-backslash",
        "RUN a {bs}{nl}# note{nl}  b => comment-in-continuation",
        "FROM ubi9{nl}# KEY = value => key-value-comment",
        "FROM ubi9 => other"})
    void aRetriedFilesWorkaroundIsTheFirstOfItsSignsTheTextShows(String text, String kind) {
        assertThat(Workaround.of(text.replace("{bs}", "\\").replace("{nl}", "\n"))).isEqualTo(kind);
    }
}
