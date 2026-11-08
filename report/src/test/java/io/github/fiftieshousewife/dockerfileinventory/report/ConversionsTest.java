package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.BooleanNode;

class ConversionsTest {

    @Test
    void eachDockerfilesRoundTripIsReadFromTheConvertersOutput(@TempDir Path converter) throws IOException {
        Files.writeString(converter.resolve("roundtrip.jsonl"), """
                {"blobSha":"aaa","passed":true,"difference":null}
                {"blobSha":"bbb","passed":false,"difference":"line 3: RUN differs"}
                {"blobSha":"ccc","passed":true,"difference":null,"javaPassed":false,"javaDifference":"step 1 differs"}
                """);

        Map<String, Conversions.Conversion> conversions = Conversions.read(converter);

        assertAll(
                () -> assertThat(Conversions.of(conversions, "aaa")).isEqualTo(
                        new Conversions.Conversion("passed", null, "not run", null)),
                () -> assertThat(Conversions.of(conversions, "bbb")).isEqualTo(
                        new Conversions.Conversion("failed", "line 3: RUN differs", "not run", null)),
                () -> assertThat(Conversions.of(conversions, "ccc")).isEqualTo(
                        new Conversions.Conversion("passed", null, "failed", "step 1 differs")));
    }

    @Test
    void aDockerfileTheConverterDidNotTryIsNotRun(@TempDir Path converter) {
        assertThat(Conversions.of(Conversions.read(converter), "ddd")).isEqualTo(
                new Conversions.Conversion("not run", null, "not run", null));
    }

    @Test
    void withoutTheConvertersOutputNothingWasRun() {
        assertThat(Conversions.read(null)).isEmpty();
    }

    @Test
    void aDifferenceThatNamesASecretIsHiddenWhole() {
        assertThat(Conversions.shown("step 3 differs: ENV DB_PASSWORD hunter2 became ENV DB_PASSWORD=hunter2"))
                .isEqualTo("(hidden: it names a secret)");
    }

    @Test
    void aDifferenceWithoutASecretIsShownAsItIs() {
        assertThat(Conversions.shown("step 2 differs: USER 1001 became USER 0")).isEqualTo(
                "step 2 differs: USER 1001 became USER 0");
    }

    @Test
    void aRoundTripIsPassedOrFailed() {
        assertAll(
                () -> assertThat(Conversions.outcome(BooleanNode.TRUE)).isEqualTo("passed"),
                () -> assertThat(Conversions.outcome(BooleanNode.FALSE)).isEqualTo("failed"));
    }
}
