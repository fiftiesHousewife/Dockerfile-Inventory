package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MergedLinesTest {

    @Test
    void findsTheFirstNonBlankLineAfterABareNewline() {
        assertThat(MergedLines.after("make\n\n  RUNN x\n")).contains("RUNN x");
    }

    @Test
    void newlinesAfterAContinuationOrACommentLineDoNotCount() {
        assertThat(MergedLines.after("a \\\n# note\nb")).isEmpty();
    }

    @Test
    void aBareNewlineWithNothingAfterItGivesAnEmptyLine() {
        assertThat(MergedLines.after("make\n")).contains("");
    }

    @Test
    void aPlainLineEndsBare() {
        assertThat(MergedLines.endsBare("make")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"make \\  ", "  # note"})
    void aContinuedLineOrACommentLineDoesNotEndBare(String line) {
        assertThat(MergedLines.endsBare(line)).isFalse();
    }

    @Test
    void inFindsNoMergedLineInAWellFormedFile() {
        assertThat(MergedLines.in(Parsed.file("FROM ubi9\nRUN make \\\n    install\n"))).isEmpty();
    }
}
