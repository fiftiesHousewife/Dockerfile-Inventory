package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class JsonLinesTest {

    @Test
    void eachNonBlankLineIsOneRowAndAMissingFileHasNone(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.jsonl"), "{\"n\":1}\n\n{\"n\":2}\n");

        assertAll(
                () -> assertThat(JsonLines.read(dir.resolve("a.jsonl"))).extracting(row -> row.path("n").asInt())
                        .containsExactly(1, 2),
                () -> assertThat(JsonLines.read(dir.resolve("none.jsonl"))).isEmpty());
    }

    @Test
    void aRowNeverPushedSortsBeforeEveryPushedOne() {
        ObjectMapper json = new ObjectMapper();
        JsonNode pushed = json.readTree("{\"lastPushed\":\"2026-01-01T00:00:00Z\"}");
        JsonNode never = json.readTree("{\"lastPushed\":null}");

        assertAll(
                () -> assertThat(JsonLines.pushed(pushed)).isEqualTo(Instant.parse("2026-01-01T00:00:00Z")),
                () -> assertThat(JsonLines.pushed(never)).isEqualTo(Instant.MIN));
    }

    @Test
    void aFileThatCannotBeReadStopsTheRun(@TempDir Path dir) {
        assertThatThrownBy(() -> JsonLines.read(dir)).isInstanceOf(UncheckedIOException.class);
    }
}
