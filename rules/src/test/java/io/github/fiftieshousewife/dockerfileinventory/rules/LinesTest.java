package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

class LinesTest {

    @Test
    void eachInstructionsFirstLineCountsCommentsBlankLinesAndContinuations() {
        Subject subject = Subjects.of("""
                # syntax comment
                ARG BASE=ubi9

                FROM $BASE AS build
                # a comment
                RUN make \\
                    && make install
                ENV A=1

                FROM ubi9
                USER 1001
                """);
        Lines lines = Lines.of(subject.file());
        List<Docker.Stage> stages = subject.file().getStages();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(lines.of(subject.file().getGlobalArgs().getFirst())).isEqualTo(2);
            softly.assertThat(lines.of(stages.get(0).getFrom())).isEqualTo(4);
            softly.assertThat(lines.of(stages.get(0).getInstructions().get(0))).isEqualTo(6);
            softly.assertThat(lines.of(stages.get(0).getInstructions().get(1))).isEqualTo(8);
            softly.assertThat(lines.of(stages.get(1).getFrom())).isEqualTo(10);
            softly.assertThat(lines.of(stages.get(1).getInstructions().get(0))).isEqualTo(11);
        });
    }

    @Test
    void anInstructionFromAnotherFileHasLineZero() {
        Lines lines = Lines.of(Subjects.of("FROM ubi9\n").file());

        assertThat(lines.of(Subjects.of("FROM ubi9\n").file().getStages().getFirst().getFrom())).isZero();
    }

    @Test
    void orderedIsTheGlobalArgsThenEachStagesFromAndInstructions() {
        Docker.File file = Subjects.of("ARG A\nFROM a\nUSER 1\nFROM b\n").file();

        assertThat(Lines.ordered(file)).extracting(node -> node.getClass().getSimpleName())
                .containsExactly("Arg", "From", "User", "From");
    }

    @Test
    void withoutLeadingCommentsDropsTheCommentLinesBeforeTheInstruction() {
        assertThat(Lines.withoutLeadingComments("\n# one\n  # two\nRUN make")).isEqualTo("RUN make");
        assertThat(Lines.withoutLeadingComments("# only a comment")).isEmpty();
    }

    @Test
    void startOfLineSkipsAMatchInsideACommentOrLaterInALine() {
        String text = "# RUN make\nENV X=RUN make\n  RUN make\n";

        assertThat(Lines.startOfLine(text, "RUN make", 0)).isEqualTo(text.lastIndexOf("RUN make"));
        assertThat(Lines.startOfLine(text, "USER", 0)).isEqualTo(-1);
    }

    @Test
    void newlinesCountsLineBreaks() {
        assertThat(Lines.newlines("a\nb\n\nc")).isEqualTo(3);
    }
}
