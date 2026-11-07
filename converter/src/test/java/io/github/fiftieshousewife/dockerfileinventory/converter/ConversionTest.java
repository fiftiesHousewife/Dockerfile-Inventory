package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import org.junit.jupiter.api.Test;

class ConversionTest {

    @Test
    void rawIsEveryUntypedInstructionInEveryStageInOrder() {
        Conversion conversion = Convert.of(Parsed.of("""
                FROM ubi9 AS build
                HEALTHCHECK NONE
                USER 1001
                FROM ubi9
                ONBUILD RUN make
                """));

        assertThat(conversion.raw()).containsExactly(
                new Instruction.Raw("HEALTHCHECK NONE", "HEALTHCHECK is not modelled"),
                new Instruction.Raw("ONBUILD RUN make", "ONBUILD is not modelled"));
    }
}
