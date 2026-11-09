package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class JsonLines {

    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonLines() {
    }

    static List<JsonNode> read(Path file) {
        try (var lines = Files.lines(file)) {
            return lines.filter(line -> !line.isBlank()).map(JSON::readTree).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
