package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConversionsTest {

    @Test
    void eachDockerfilesRoundTripIsReadFromTheConvertersOutput(@TempDir Path converter) throws IOException {
        Files.writeString(converter.resolve("roundtrip.jsonl"), """
                {"blobSha":"aaa","passed":true,"difference":null}
                {"blobSha":"bbb","passed":false,"difference":"line 3: RUN differs"}
                """);

        Map<String, Conversions.Conversion> conversions = Conversions.read(converter);

        assertAll(
                () -> assertThat(Conversions.of(conversions, "aaa")).isEqualTo(
                        new Conversions.Conversion("passed", null)),
                () -> assertThat(Conversions.of(conversions, "bbb")).isEqualTo(
                        new Conversions.Conversion("failed", "line 3: RUN differs")));
    }

    @Test
    void aDockerfileTheConverterDidNotTryIsNotRun(@TempDir Path converter) {
        assertThat(Conversions.of(Conversions.read(converter), "ccc")).isEqualTo(
                new Conversions.Conversion("not run", null));
    }

    @Test
    void withoutTheConvertersOutputNothingWasRun() {
        assertThat(Conversions.read(null)).isEmpty();
    }
}
