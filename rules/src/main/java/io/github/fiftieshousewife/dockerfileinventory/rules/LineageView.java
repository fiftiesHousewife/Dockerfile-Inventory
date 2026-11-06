package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads lineage's {@code effective.jsonl} and {@code images.jsonl} into the views the effective rules check. */
final class LineageView {

    private static final ObjectMapper JSON = new ObjectMapper();

    private LineageView() {
    }

    static List<ImageView> read(Path lineageDir) {
        Map<String, List<String>> names = new HashMap<>();
        lines(lineageDir.resolve("images.jsonl")).stream()
                .filter(row -> !row.path("configDigest").isNull() && row.has("configDigest"))
                .forEach(row -> names.computeIfAbsent(row.path("configDigest").asString(), d -> new ArrayList<>())
                        .add(row.path("image").asString() + ":" + row.path("tag").asString()));
        return lines(lineageDir.resolve("effective.jsonl")).stream().map(row -> {
            List<ImageView.Group> chain = new ArrayList<>();
            row.path("chain").forEach(entry -> chain.add(group(entry.path("configDigest").asString(),
                    entry.path("link"))));
            JsonNode user = row.path("user");
            JsonNode setBy = user.path("setBy");
            ImageView.Group userSetBy = setBy.path("kind").asString().equals("Image")
                    ? group(setBy.path("configDigest").asString(), setBy.path("link"))
                    : null;
            List<String> rootNames = chain.isEmpty() ? List.of()
                    : names.getOrDefault(chain.getLast().configDigest(), List.of());
            return new ImageView(row.path("configDigest").asString(), user.path("value").stringValue(null),
                    userSetBy, chain, rootNames);
        }).toList();
    }

    private static ImageView.Group group(String configDigest, JsonNode link) {
        List<String> blobs = new ArrayList<>();
        link.path("blobShas").forEach(b -> blobs.add(b.asString()));
        return new ImageView.Group(configDigest, blobs);
    }

    private static List<JsonNode> lines(Path file) {
        try {
            return Files.exists(file)
                    ? Files.readAllLines(file).stream().filter(l -> !l.isBlank()).map(JSON::readTree).toList()
                    : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
