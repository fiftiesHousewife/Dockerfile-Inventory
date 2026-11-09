package io.github.fiftieshousewife.dockerfileinventory.report;

import io.github.fiftieshousewife.dockerfileinventory.secrets.Secrets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

final class LineageFiles {

    record Linked(List<String> built, List<String> candidates) {

        Linked {
            built = List.copyOf(built);
            candidates = List.copyOf(candidates);
        }
    }

    private static final Linked UNLINKED = new Linked(List.of(), List.of());

    private final Map<String, String> parents;
    private final Map<String, Linked> links;
    private final Map<String, String> newerBases;
    private final Map<String, Map<String, ImageLabel>> labels;

    private LineageFiles(Path lineageDir) {
        this.parents = parents(lineageDir.resolve("lineage.jsonl"));
        this.links = links(lineageDir.resolve("links.jsonl"));
        this.newerBases = newerBases(lineageDir.resolve("outdated-bases.jsonl"));
        this.labels = labels(lineageDir.resolve("effective.jsonl"));
    }

    static LineageFiles read(Path lineageDir) {
        return new LineageFiles(lineageDir);
    }

    String parentOf(String configDigest) {
        return parents.get(configDigest);
    }

    Linked linkOf(String configDigest) {
        return links.getOrDefault(configDigest, UNLINKED);
    }

    String newerBaseOf(String configDigest) {
        return newerBases.get(configDigest);
    }

    Map<String, ImageLabel> labelsOf(String configDigest) {
        return labels.getOrDefault(configDigest, Map.of());
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
        return row.path("credited").asBoolean(false) ? new Linked(shas, List.of()) : new Linked(List.of(), shas);
    }

    static Map<String, String> newerBases(Path outdated) {
        Map<String, String> newer = new HashMap<>();
        JsonLines.read(outdated).forEach(row -> newer.put(row.path("image").asString(), row.path("newer").asString()));
        return newer;
    }

    static Map<String, Map<String, ImageLabel>> labels(Path effective) {
        Map<String, Map<String, ImageLabel>> labels = new HashMap<>();
        JsonLines.read(effective).forEach(row -> labels.put(row.path("configDigest").asString(),
                imageLabels(row.path("labels"))));
        return labels;
    }

    static Map<String, ImageLabel> imageLabels(JsonNode labels) {
        Map<String, ImageLabel> image = new LinkedHashMap<>();
        labels.propertyNames().forEach(name -> image.put(name, label(name, labels.path(name))));
        return image;
    }

    static ImageLabel label(String name, JsonNode property) {
        JsonNode setBy = property.path("setBy");
        String kind = setBy.path("kind").asString();
        return new ImageLabel(Secrets.shown(name, property.path("value").asString()),
                "Image".equals(kind) ? setBy.path("configDigest").asString() : null,
                "ExternalBase".equals(kind) ? setBy.path("path").asString() : null);
    }
}
