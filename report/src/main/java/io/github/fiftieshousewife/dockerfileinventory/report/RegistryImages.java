package io.github.fiftieshousewife.dockerfileinventory.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class RegistryImages {

    private static final ObjectMapper JSON = new ObjectMapper();

    private RegistryImages() {
    }

    static Map<String, RegistryImage> read(Path lineageDir) {
        Map<String, List<JsonNode>> tags = new LinkedHashMap<>();
        JsonLines.read(lineageDir.resolve("images.jsonl")).stream().filter(row -> row.path("configDigest").isString())
                .forEach(row -> tags.computeIfAbsent(row.path("configDigest").asString(), d -> new ArrayList<>())
                        .add(row));
        Map<String, String> parents = parents(lineageDir.resolve("lineage.jsonl"));
        Map<String, List<String>> blobShas = blobShas(lineageDir.resolve("links.jsonl"));
        Map<String, String> newer = newerBases(lineageDir.resolve("outdated-bases.jsonl"));
        Map<String, RegistryImage> images = new LinkedHashMap<>();
        tags.forEach((digest, rows) -> {
            JsonNode config = config(lineageDir, digest);
            images.put(digest, new RegistryImage(digest, rows.stream().map(RegistryImages::name).distinct().toList(),
                    parents.get(digest), blobShas.getOrDefault(digest, List.of()),
                    config.path("rootfs").path("diff_ids").size(), config.path("created").stringValue(null),
                    newest(rows.stream().map(row -> row.path("lastPushed").stringValue(null)).toList()),
                    newer.get(digest)));
        });
        return images;
    }

    static Map<String, String> newerBases(Path outdated) {
        Map<String, String> newer = new HashMap<>();
        JsonLines.read(outdated).forEach(row -> newer.put(row.path("image").asString(), row.path("newer").asString()));
        return newer;
    }

    static String newestPush(List<RegistryImage> images) {
        return newest(images.stream().map(RegistryImage::lastPushed).toList());
    }

    private static Map<String, String> parents(Path lineage) {
        Map<String, String> parents = new HashMap<>();
        JsonLines.read(lineage).stream().filter(row -> "Of".equals(row.path("parent").path("kind").asString()))
                .forEach(row -> parents.put(row.path("configDigest").asString(),
                        row.path("parent").path("configDigest").asString()));
        return parents;
    }

    private static Map<String, List<String>> blobShas(Path links) {
        Map<String, List<String>> shas = new HashMap<>();
        JsonLines.read(links).forEach(row -> shas.put(row.path("configDigest").asString(),
                row.path("blobShas").valueStream().map(JsonNode::asString).toList()));
        return shas;
    }

    private static JsonNode config(Path lineageDir, String digest) {
        Path file = lineageDir.resolve("configs").resolve(digest.replace(':', '_') + ".json");
        try {
            return Files.exists(file) ? JSON.readTree(Files.readString(file)) : JSON.createObjectNode();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String name(JsonNode tag) {
        return tag.path("image").asString() + ":" + tag.path("tag").asString();
    }

    private static String newest(List<String> times) {
        return times.stream().filter(time -> time != null).max(Comparator.comparing(Instant::parse)).orElse(null);
    }
}
