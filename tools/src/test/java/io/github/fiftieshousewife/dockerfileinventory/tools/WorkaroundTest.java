package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;
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

    @Test
    void aCommentInAContinuationIsTakenBeforeAKeyValueComment() {
        assertThat(Workaround.multiLine(List.of("FROM ubi9", "RUN a \\", "# KEY = value"))).isEqualTo(
                "comment-in-continuation");
    }

    @Test
    void aCommentIsInAContinuationOnlyRightAfterALineEndingInABackslash() {
        assertAll(
                () -> assertThat(Workaround.commentInContinuation(List.of("RUN a \\", "# note"))).isTrue(),
                () -> assertThat(Workaround.commentInContinuation(List.of("RUN a", "# note"))).isFalse(),
                () -> assertThat(Workaround.commentInContinuation(List.of())).isFalse());
    }

    @Test
    void aKeyValueCommentCountsOnlyAfterTheFirstInstruction() {
        assertAll(
                () -> assertThat(Workaround.keyValueCommentAfterAnInstruction(List.of("# KEY = value", "FROM ubi9")))
                        .isFalse(),
                () -> assertThat(Workaround.keyValueCommentAfterAnInstruction(List.of("# syntax", "FROM ubi9",
                        "# KEY = value"))).isTrue());
    }
}
