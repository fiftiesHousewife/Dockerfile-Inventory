package io.github.fiftieshousewife.dockerfileinventory.report;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

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
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class RegistryImages {

    private static final ObjectMapper JSON = new ObjectMapper();

    private RegistryImages() {
    }

    static Map<String, RegistryImage> read(Path lineageDir) {
        List<JsonNode> rows = JsonLines.read(lineageDir.resolve("images.jsonl"));
        LineageFiles files = LineageFiles.read(lineageDir);
        Map<String, Set<String>> tagsOfImage = tagsOfImage(rows);
        Map<String, RegistryImage> images = new LinkedHashMap<>();
        rowsByDigest(rows).forEach((digest, tags) -> images.put(digest,
                image(digest, tags, config(lineageDir, digest), files, tagsOfImage)));
        return images;
    }

    static Map<String, List<JsonNode>> rowsByDigest(List<JsonNode> rows) {
        return rows.stream().filter(row -> row.path("configDigest").isString())
                .collect(groupingBy(row -> row.path("configDigest").asString(), LinkedHashMap::new, toList()));
    }

    // Every tag, resolved or not: a signature's tag is often a manifest lineage skips.
    static Map<String, Set<String>> tagsOfImage(List<JsonNode> rows) {
        return rows.stream().collect(groupingBy(row -> row.path("image").asString(),
                mapping(row -> row.path("tag").asString(), toSet())));
    }

    static RegistryImage image(String digest, List<JsonNode> tags, JsonNode config, LineageFiles files,
                               Map<String, Set<String>> tagsOfImage) {
        LineageFiles.Linked linked = files.linkOf(digest);
        return new RegistryImage(digest, ImageTags.names(tags), files.parentOf(digest), linked.built(),
                layers(config), config.path("created").stringValue(null), ImageTags.lastPushed(tags),
                files.newerBaseOf(digest), files.labelsOf(digest), linked.candidates(), ImageTags.size(tags),
                ImageTags.architectures(tags, config), ImageTags.provenance(tags, tagsOfImage), ImageTags.usage(tags));
    }

    static Integer layers(JsonNode config) {
        return config.path("rootfs").isObject() ? config.path("rootfs").path("diff_ids").size() : null;
    }

    static Map<String, List<Instant>> pushesByName(Path lineageDir) {
        Map<String, List<Instant>> pushes = new HashMap<>();
        JsonLines.read(lineageDir.resolve("images.jsonl")).stream().filter(row -> row.path("lastPushed").isString())
                .forEach(row -> pushes.computeIfAbsent(row.path("image").asString(), name -> new ArrayList<>())
                        .add(Instant.parse(row.path("lastPushed").asString())));
        pushes.values().forEach(list -> list.sort(null));
        return pushes;
    }

    static Map<String, List<String>> tagsByImage(Path lineageDir) {
        Map<String, List<String>> tags = new HashMap<>();
        JsonLines.read(lineageDir.resolve("images.jsonl")).stream()
                .filter(row -> !row.path("tag").asString().equals("*"))
                .forEach(row -> tags.computeIfAbsent(row.path("image").asString(), image -> new ArrayList<>())
                        .add(row.path("tag").asString()));
        return tags;
    }

    static String newestPush(List<RegistryImage> images) {
        return newest(images.stream().map(RegistryImage::lastPushed).toList());
    }

    static JsonNode config(Path lineageDir, String digest) {
        Path file = lineageDir.resolve("configs").resolve(digest.replace(':', '_') + ".json");
        try {
            return Files.exists(file) ? JSON.readTree(Files.readString(file)) : JSON.createObjectNode();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String newest(List<String> times) {
        return times.stream().filter(time -> time != null).max(Comparator.comparing(Instant::parse)).orElse(null);
    }
}
