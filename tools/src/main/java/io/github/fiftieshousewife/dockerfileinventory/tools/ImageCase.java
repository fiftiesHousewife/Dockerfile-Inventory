package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

final class ImageCase {

    static final List<String> SOURCE_LABELS = List.of("io.openshift.build.source-location",
            "org.opencontainers.image.source");

    private ImageCase() {
    }

    static JsonNode labels(JsonNode config) {
        JsonNode labels = config.path("config").path("Labels");
        return labels.isObject() ? labels : Json.MAPPER.createObjectNode();
    }

    static String sourceRepository(JsonNode labels) {
        return repository(SOURCE_LABELS.stream().map(label -> labels.path(label).asString(""))
                .filter(value -> !value.isEmpty()).findFirst().orElse(""));
    }

    // https://host/owner/name/... -> owner/name.
    static String repository(String url) {
        String path = url.replaceFirst("^https?://[^/]+/", "");
        List<String> parts = List.of(path.split("/"));
        return path.isEmpty() ? "" : String.join("/", parts.subList(0, Math.min(2, parts.size())));
    }

    static ObjectNode trimmed(JsonNode config, JsonNode labels) {
        ObjectNode trimmed = Json.MAPPER.createObjectNode();
        ObjectNode inner = trimmed.putObject("config");
        inner.set("Labels", labels);
        inner.set("User", presentOrNull(config.path("config").path("User")));
        trimmed.set("rootfs", presentOrNull(config.path("rootfs")));
        trimmed.set("history", presentOrNull(config.path("history")));
        return trimmed;
    }

    static JsonNode presentOrNull(JsonNode node) {
        return node.isMissingNode() ? null : node;
    }

    static ObjectNode candidate(JsonNode facts, String repository) {
        ObjectNode candidate = Json.MAPPER.createObjectNode();
        candidate.set("blobSha", facts.path("blobSha"));
        candidate.set("steps", facts.path("steps"));
        candidate.set("fromPath", facts.path("fromPath"));
        candidate.put("repository", repository);
        return candidate;
    }

    static ObjectNode expected(String name, String repository, JsonNode link) {
        ObjectNode expected = Json.MAPPER.createObjectNode();
        expected.put("image", name);
        expected.put("sourceRepository", repository);
        if (link != null) {
            expected.set("tier", link.path("tier"));
            expected.set("blobShas", link.path("blobShas"));
            expected.put("credited", link.path("credited").asBoolean(false));
        }
        return expected;
    }
}
