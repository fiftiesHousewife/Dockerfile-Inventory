package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.openrewrite.docker.tree.Docker;

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

        assertThat(lines.of(subject.file().getGlobalArgs().getFirst())).isEqualTo(2);
        assertThat(lines.of(stages.get(0).getFrom())).isEqualTo(4);
        assertThat(lines.of(stages.get(0).getInstructions().get(0))).isEqualTo(6);
        assertThat(lines.of(stages.get(0).getInstructions().get(1))).isEqualTo(8);
        assertThat(lines.of(stages.get(1).getFrom())).isEqualTo(10);
        assertThat(lines.of(stages.get(1).getInstructions().get(0))).isEqualTo(11);
    }
}
