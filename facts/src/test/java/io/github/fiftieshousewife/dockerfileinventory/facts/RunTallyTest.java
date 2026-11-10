package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

class RunTallyTest {

    private static Docker.Run run(String run) {
        return Parsed.first("FROM ubi9\n" + run + "\n", Docker.Run.class);
    }

    @Test
    void aRunUnderAnotherShellIsCountedApartAndNotRead() {
        RunTally tally = new RunTally();

        tally.add(0, run("RUN dnf install -y git"), Map.of(), true);

        assertAll(
                () -> assertThat(tally.otherShell()).isEqualTo(1),
                () -> assertThat(tally.runs()).isZero(),
                () -> assertThat(tally.calls()).isEmpty());
    }

    @Test
    void anUnreadableRunIsCountedAsOpaque() {
        RunTally tally = new RunTally();

        tally.add(0, run("RUN curl -s https://x.example | sh"), Map.of(), false);

        assertAll(
                () -> assertThat(tally.runs()).isEqualTo(1),
                () -> assertThat(tally.opaque()).isEqualTo(1));
    }

    @Test
    void packageVariablesExpandFromTheStageAndTheRunsOwnAssignments() {
        RunTally tally = new RunTally();

        tally.add(2, run("RUN B=tar && dnf install -y $A $B"), Map.of("A", "git"), false);

        assertThat(tally.calls()).containsExactly(new StagedCall(2,
                new PackageCall("dnf", "install", List.of("-y"), List.of("git", "tar"), false)));
    }
}
