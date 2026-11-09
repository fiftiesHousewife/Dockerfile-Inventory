package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

final class ImageTags {

    private ImageTags() {
    }

    static List<String> names(List<JsonNode> rows) {
        return rows.stream().map(ImageTags::name).distinct().toList();
    }

    static String name(JsonNode tag) {
        return tag.path("image").asString() + ":" + tag.path("tag").asString();
    }

    static String lastPushed(List<JsonNode> rows) {
        return RegistryImages.newest(rows.stream().map(row -> row.path("lastPushed").stringValue(null)).toList());
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
                RegistryImages.newest(rows.stream().map(row -> row.path("lastDownloaded").stringValue(null)).toList()));
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
}
