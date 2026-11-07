package io.github.fiftieshousewife.dockerfileinventory.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class JsonLines {

    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonLines() {
    }

    static List<JsonNode> read(Path file) {
        try {
            return Files.exists(file)
                    ? Files.readAllLines(file).stream().filter(l -> !l.isBlank()).map(JSON::readTree).toList()
                    : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Instant pushed(JsonNode row) {
        return row.path("lastPushed").isNull() ? Instant.MIN : Instant.parse(row.path("lastPushed").asString());
    }
}
