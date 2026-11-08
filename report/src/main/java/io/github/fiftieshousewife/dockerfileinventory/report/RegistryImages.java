package io.github.fiftieshousewife.dockerfileinventory.report;

import io.github.fiftieshousewife.dockerfileinventory.secrets.Secrets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        Map<String, Set<String>> tagsOfImage = new HashMap<>();
        // Every tag, resolved or not: a signature's tag is often a manifest lineage skips.
        JsonLines.read(lineageDir.resolve("images.jsonl")).forEach(row -> tagsOfImage
                .computeIfAbsent(row.path("image").asString(), image -> new HashSet<>())
                .add(row.path("tag").asString()));
        tags.forEach((digest, rows) -> {
            JsonNode config = config(lineageDir, digest);
            images.put(digest, new RegistryImage(digest, rows.stream().map(RegistryImages::name).distinct().toList(),
                    parents.get(digest), links.getOrDefault(digest, UNLINKED).built(),
                    layers(config), config.path("created").stringValue(null),
                    newest(rows.stream().map(row -> row.path("lastPushed").stringValue(null)).toList()),
                    newer.get(digest), labels.getOrDefault(digest, Map.of()),
                    links.getOrDefault(digest, UNLINKED).candidates(), size(rows), architectures(rows, config),
                    provenance(rows, tagsOfImage), usage(rows)));
        });
        return images;
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

    static Provenance provenance(List<JsonNode> rows, Map<String, Set<String>> tagsOfImage) {
        List<String> manifestDigests = rows.stream().map(row -> row.path("manifestDigest")).filter(JsonNode::isString)
                .map(JsonNode::asString).distinct().toList();
        Set<String> siblings = new HashSet<>();
        rows.forEach(row -> siblings.addAll(tagsOfImage.getOrDefault(row.path("image").asString(), Set.of())));
        return Provenance.of(manifestDigests, siblings);
    }

    static RegistryImage.Usage usage(List<JsonNode> rows) {
        List<JsonNode> counted = rows.stream().filter(row -> row.path("downloads").isNumber()).toList();
        return new RegistryImage.Usage(counted.isEmpty() ? null
                : counted.stream().mapToLong(row -> row.path("downloads").asLong()).sum(),
                newest(rows.stream().map(row -> row.path("lastDownloaded").stringValue(null)).toList()));
    }

    static Long size(List<JsonNode> rows) {
        return rows.stream().map(row -> row.path("size")).filter(JsonNode::isNumber).map(JsonNode::asLong)
                .findFirst().orElse(null);
    }

    static List<String> architectures(List<JsonNode> rows, JsonNode config) {
        List<String> listed = rows.stream().flatMap(row -> row.path("architectures").valueStream())
                .map(JsonNode::asString).distinct().toList();
        return !listed.isEmpty() || !config.path("architecture").isString() ? listed
                : List.of(config.path("os").asString("linux") + "/" + config.path("architecture").asString());
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
