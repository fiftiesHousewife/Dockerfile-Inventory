package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class MissingOciLabelsTest {

    @Test
    void r3ReportsAFinalImageWithoutOciRevisionOrSourceCountingEarlierStagesItBuildsOn() {
        Rule r3 = new MissingOciLabels();

        assertAll(
                () -> assertThat(r3.check(Subjects.of("FROM ubi9\nLABEL org.opencontainers.image.source=https://x\n")))
                        .singleElement().extracting(Finding::message).asString()
                                .contains("org.opencontainers.image.revision").doesNotContain("image.source"),
                () -> assertThat(r3.check(Subjects.of("""
                        FROM ubi9 AS labelled
                        LABEL org.opencontainers.image.revision=$REV org.opencontainers.image.source=$SRC
                        FROM golang AS build
                        FROM labelled
                        USER 1001
                        """))).isEmpty(),
                () -> assertThat(r3.check(Subjects.of("""
                        FROM ubi9
                        # the build sets org.opencontainers.image.revision
                        LABEL org.opencontainers.image.source=$SRC
                        """))).hasSize(1),
                () -> assertThat(r3.check(Subjects.of("""
                        FROM ubi9 AS build
                        LABEL org.opencontainers.image.revision=$REV org.opencontainers.image.source=$SRC
                        FROM ubi9
                        """))).hasSize(1));
    }

    @Test
    void r3IgnoresAFileThatDidNotParse() {
        assertThat(new MissingOciLabels().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @Test
    void labelsAreTheKeysOfEveryLabelInTheFinalImageAcrossContinuations() {
        Subject subject = Subjects.of("FROM ubi9\n# a comment\nLABEL a=1 \\\n    b=2\nLABEL \"c d\"=3\n");

        assertThat(MissingOciLabels.labels(subject.file())).containsExactlyInAnyOrder("a", "b", "c d");
    }
}
