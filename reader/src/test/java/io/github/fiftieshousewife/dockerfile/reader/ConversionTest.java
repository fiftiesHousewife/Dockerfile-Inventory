package io.github.fiftieshousewife.dockerfile.reader;

import io.github.fiftieshousewife.dockerfile.model.Raw;
import static org.assertj.core.api.Assertions.assertThat;

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
                new Raw("RUN --device=/dev/fuse make", "RUN with flag --device"),
                new Raw("COPY --exclude=x . /s/", "COPY with flag --exclude"));
    }
}
