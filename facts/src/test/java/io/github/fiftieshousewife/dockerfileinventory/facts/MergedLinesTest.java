package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
