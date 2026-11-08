package io.github.fiftieshousewife.dockerfileinventory.report;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Secrets;
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

    record Linked(List<String> built, List<String> candidates) {

        Linked {
            built = List.copyOf(built);
            candidates = List.copyOf(candidates);
        }
    }

    private static final Linked UNLINKED = new Linked(List.of(), List.of());

    private RegistryImages() {
    }

    static Map<String, RegistryImage> read(Path lineageDir) {
        Map<String, List<JsonNode>> tags = new LinkedHashMap<>();
        JsonLines.read(lineageDir.resolve("images.jsonl")).stream().filter(row -> row.path("configDigest").isString())
                .forEach(row -> tags.computeIfAbsent(row.path("configDigest").asString(), d -> new ArrayList<>())
                        .add(row));
        Map<String, String> parents = parents(lineageDir.resolve("lineage.jsonl"));
        Map<String, Linked> links = links(lineageDir.resolve("links.jsonl"));
        Map<String, String> newer = newerBases(lineageDir.resolve("outdated-bases.jsonl"));
        Map<String, Map<String, ImageLabel>> labels = labels(lineageDir.resolve("effective.jsonl"));
        Map<String, RegistryImage> images = new LinkedHashMap<>();
        tags.forEach((digest, rows) -> {
            JsonNode config = config(lineageDir, digest);
            images.put(digest, new RegistryImage(digest, rows.stream().map(RegistryImages::name).distinct().toList(),
                    parents.get(digest), links.getOrDefault(digest, UNLINKED).built(),
                    layers(config), config.path("created").stringValue(null),
                    newest(rows.stream().map(row -> row.path("lastPushed").stringValue(null)).toList()),
                    newer.get(digest), labels.getOrDefault(digest, Map.of()),
                    links.getOrDefault(digest, UNLINKED).candidates()));
        });
        return images;
    }

    static Integer layers(JsonNode config) {
        return config.path("rootfs").isObject() ? config.path("rootfs").path("diff_ids").size() : null;
    }

    static Map<String, String> newerBases(Path outdated) {
        Map<String, String> newer = new HashMap<>();
        JsonLines.read(outdated).forEach(row -> newer.put(row.path("image").asString(), row.path("newer").asString()));
        return newer;
    }

    static Map<String, Map<String, ImageLabel>> labels(Path effective) {
        Map<String, Map<String, ImageLabel>> labels = new HashMap<>();
        JsonLines.read(effective).forEach(row -> {
            Map<String, ImageLabel> image = new LinkedHashMap<>();
            row.path("labels").propertyNames().forEach(name -> image.put(name, label(name, row.path("labels")
                    .path(name))));
            labels.put(row.path("configDigest").asString(), image);
        });
        return labels;
    }

    static ImageLabel label(String name, JsonNode property) {
        JsonNode setBy = property.path("setBy");
        String kind = setBy.path("kind").asString();
        return new ImageLabel(Secrets.shown(name, property.path("value").asString()),
                "Image".equals(kind) ? setBy.path("configDigest").asString() : null,
                "ExternalBase".equals(kind) ? setBy.path("path").asString() : null);
    }

    static String newestPush(List<RegistryImage> images) {
        return newest(images.stream().map(RegistryImage::lastPushed).toList());
    }

    static Map<String, String> parents(Path lineage) {
        Map<String, String> parents = new HashMap<>();
        JsonLines.read(lineage).stream().filter(row -> "Of".equals(row.path("parent").path("kind").asString()))
                .forEach(row -> parents.put(row.path("configDigest").asString(),
                        row.path("parent").path("configDigest").asString()));
        return parents;
    }

    static Map<String, Linked> links(Path links) {
        Map<String, Linked> linked = new HashMap<>();
        JsonLines.read(links).forEach(row -> linked.put(row.path("configDigest").asString(), linked(row)));
        return linked;
    }

    static Linked linked(JsonNode row) {
        List<String> shas = row.path("blobShas").valueStream().map(JsonNode::asString).toList();
        String tier = row.path("tier").asString();
        boolean credited = "STRONG".equals(tier) || "PROBABLE".equals(tier) && shas.size() == 1;
        return credited ? new Linked(shas, List.of()) : new Linked(List.of(), shas);
    }

    static JsonNode config(Path lineageDir, String digest) {
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

    static String newest(List<String> times) {
        return times.stream().filter(time -> time != null).max(Comparator.comparing(Instant::parse)).orElse(null);
    }
}
