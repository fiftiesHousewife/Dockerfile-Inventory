package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import org.junit.jupiter.api.Test;

class ConversionTest {

    @Test
    void rawIsEveryUntypedInstructionInEveryStageInOrder() {
        Conversion conversion = Convert.of(Parsed.of("""
                FROM ubi9 AS build
                RUN --device=/dev/fuse make
                USER 1001
                FROM ubi9
                COPY --exclude=x . /s/
                """));

        assertThat(conversion.raw()).containsExactly(
                new Instruction.Raw("RUN --device=/dev/fuse make", "RUN with flag --device"),
                new Instruction.Raw("COPY --exclude=x . /s/", "COPY with flag --exclude"));
    }
}
