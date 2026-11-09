package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// The catalogue stage's entries as the catalogue rules read them: the file, not the report's classes.
final class CatalogueView {

    private static final ObjectMapper JSON = new ObjectMapper();

    record Pinned(String name, String tag, String digest) {
    }

    record Entry(String blobSha, List<Pinned> pinned) {

        Entry {
            pinned = List.copyOf(pinned);
        }
    }

    private CatalogueView() {
    }

    static List<Entry> read(Path catalogueDir) {
        try (var lines = Files.lines(catalogueDir.resolve("catalogue/dockerfiles.jsonl"))) {
            return lines.filter(line -> !line.isBlank()).map(JSON::readTree).map(CatalogueView::entry).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The bases the stages pin by digest; a stage built on an earlier one names none.
    static Entry entry(JsonNode row) {
        return new Entry(row.path("blobSha").asString(), row.path("build").path("stages").valueStream()
                .filter(stage -> stage.path("baseName").isString() && stage.path("baseDigest").isString())
                .map(stage -> new Pinned(stage.path("baseName").asString(), stage.path("baseTag").stringValue(null),
                        stage.path("baseDigest").asString()))
                .toList());
    }
}
