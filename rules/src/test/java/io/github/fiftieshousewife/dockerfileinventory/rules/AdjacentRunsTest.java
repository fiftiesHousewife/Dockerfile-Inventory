package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

class AdjacentRunsTest {

    @Test
    void h3ReportsAdjacentShellFormRunsInOneStage() {
        assertThat(new AdjacentRuns().check(Subjects.of("""
                FROM ubi9 AS build
                RUN microdnf install -y make
                RUN make
                COPY . /src
                RUN make install
                RUN --mount=type=cache,target=/root/.m2 mvn package
                RUN ["echo", "exec form"]
                FROM ubi9
                RUN true
                """))).extracting(Finding::line).containsExactly(3);
    }

    @Test
    void h3IgnoresAFileThatDidNotParse() {
        assertThat(new AdjacentRuns().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @Test
    void joinableIsEachRunThatFollowsAnotherCombinableRun() {
        List<Docker.Instruction> instructions = instructions("FROM ubi9\nRUN a\nRUN b\nRUN c\nUSER 1\nRUN d\n");

        assertThat(AdjacentRuns.joinable(instructions)).containsExactly(instructions.get(1), instructions.get(2));
    }

    @Test
    void combinableIsAShellFormRunWithoutFlags() {
        List<Docker.Instruction> instructions =
                instructions("FROM ubi9\nRUN make\nRUN --network=none make\nRUN [\"make\"]\nUSER 1001\n");

        assertThat(instructions).extracting(AdjacentRuns::combinable).containsExactly(true, false, false, false);
    }

    private static List<Docker.Instruction> instructions(String dockerfile) {
        return Subjects.of(dockerfile).file().getStages().getFirst().getInstructions();
    }
}
