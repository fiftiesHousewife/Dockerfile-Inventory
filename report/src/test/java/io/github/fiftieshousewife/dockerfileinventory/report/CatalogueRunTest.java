package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class CatalogueRunTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures/catalogue");
    private static final String SHA = "3f6d0c1a2b4e5f60718293a4b5c6d7e8f9a0b1c2";

    @Test
    void eachDockerfileIsOneJsonLineWithItsImageBuildAndLinks(@TempDir Path out) {
        CatalogueRun.write(FIXTURES, FIXTURES, null, out);

        List<JsonNode> lines = JsonLines.read(out.resolve("catalogue/dockerfiles.jsonl"));
        assertAll(
                () -> assertThat(lines).hasSize(1),
                () -> assertThat(lines.getFirst().path("blobSha").asString()).isEqualTo(SHA),
                () -> assertThat(lines.getFirst().path("image").path("environment").path("API_TOKEN").asString())
                        .startsWith("(hidden sha256:"),
                () -> assertThat(lines.getFirst().path("links").path("depth").asInt()).isZero(),
                () -> assertThat(lines.getFirst().path("parseError").isNull()).isTrue());
    }

    @Test
    void aDockerfileThatDidNotParseIsALineWithItsLocationsAndWhy(@TempDir Path facts, @TempDir Path out)
            throws IOException {
        Files.writeString(facts.resolve("parse-failures.jsonl"),
                "{\"blobSha\":\"" + SHA + "\",\"message\":\"mismatched input\"}\n");

        CatalogueRun.write(facts, FIXTURES, null, out);

        JsonNode line = JsonLines.read(out.resolve("catalogue/dockerfiles.jsonl")).getFirst();
        assertAll(
                () -> assertThat(line.path("parseError").asString()).isEqualTo("mismatched input"),
                () -> assertThat(line.path("locations").size()).isEqualTo(2),
                () -> assertThat(line.path("image").isNull()).isTrue());
    }

    @Test
    void aSecretShapedValueThatReachedALineIsHiddenBeforeItIsWritten() {
        assertThat(CatalogueRun.guarded("{\"note\":\"AKIAABCDEFGHIJKLMNOP\"}")).doesNotContain("AKIA");
    }
}
