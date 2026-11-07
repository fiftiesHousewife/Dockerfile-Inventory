package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.openrewrite.docker.tree.Docker;

class PrintedTest {

    private static final Docker.File FILE = Parsed.of("""
            FROM ubi9
            # first
            #   second
            USER 1001
            RUN make
            """);

    @Test
    void commentsAreTheLinesBeforeTheInstructionWithoutTheirHash() {
        Printed printed = new Printed(FILE);

        assertThat(printed.comments(instruction(0))).containsExactly("first", "second");
        assertThat(printed.comments(instruction(1))).isEmpty();
    }

    @Test
    void textIsTheInstructionWithoutTheCommentsBeforeIt() {
        Printed printed = new Printed(FILE);

        assertThat(printed.text(instruction(0))).isEqualTo("USER 1001");
        assertThat(printed.text(instruction(1))).isEqualTo("RUN make");
    }

    private static Docker.Instruction instruction(int index) {
        return FILE.getStages().getFirst().getInstructions().get(index);
    }
}
