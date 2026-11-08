package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("estate")
class EstateConversionTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void everyInstructionIsTypedTheRoundTripHoldsItsCountAndEveryPassingClassRebuildsItsDockerfile(@TempDir Path out)
            throws IOException {
        Path discovery = Path.of(System.getenv().getOrDefault("ESTATE_DISCOVERY_DIR", "../out/all/discovery"));
        assumeTrue(Files.isDirectory(discovery.resolve("blobs")), "no estate at " + discovery.toAbsolutePath());

        new ConverterRun(discovery, null, null, out, true).run();

        JsonNode coverage = JSON.readTree(out.resolve("coverage.json").toFile());
        List<JsonNode> rows = Files.readAllLines(out.resolve("roundtrip.jsonl")).stream().map(JSON::readTree).toList();
        long textPassed = rows.stream().filter(row -> row.path("passed").asBoolean()).count();
        int expected = JSON.readTree(Path.of("src/test/resources/estate/expected.json").toFile())
                .path("textPassed").asInt();
        assertAll(
                () -> assertThat(coverage.path("rawReasons").propertyNames()).as("untyped instructions").isEmpty(),
                () -> assertThat(textPassed).as("text round trip").isGreaterThanOrEqualTo(expected),
                () -> assertThat(coverage.path("javaRoundTrip").path("passed").asLong()).as("Java round trip")
                        .isEqualTo(textPassed));
    }
}
