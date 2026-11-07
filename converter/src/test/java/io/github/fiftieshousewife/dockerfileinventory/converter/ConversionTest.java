package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import org.junit.jupiter.api.Test;

class ConversionTest {

    @Test
    void rawIsEveryUntypedInstructionInEveryStageInOrder() {
        Conversion conversion = Convert.of(Parsed.of("""
                FROM ubi9 AS build
                VOLUME /data
                USER 1001
                FROM ubi9
                STOPSIGNAL SIGTERM
                """));

        assertThat(conversion.raw()).containsExactly(new Instruction.Raw("VOLUME /data", "VOLUME is not modelled"),
                new Instruction.Raw("STOPSIGNAL SIGTERM", "STOPSIGNAL is not modelled"));
    }
}
