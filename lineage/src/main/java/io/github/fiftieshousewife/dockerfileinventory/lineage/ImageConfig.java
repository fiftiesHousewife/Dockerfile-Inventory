package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** What lineage reads from an image config: its layers, the user it runs as, and its labels. */
public record ImageConfig(List<String> diffIds, String user, Map<String, String> labels) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public ImageConfig {
        diffIds = List.copyOf(diffIds);
        labels = Collections.unmodifiableMap(new TreeMap<>(labels));
    }

    static Optional<ImageConfig> parse(String text) {
        JsonNode root = JSON.readTree(text);
        JsonNode config = root.path("config");
        List<String> diffIds = new ArrayList<>();
        root.path("rootfs").path("diff_ids").forEach(d -> diffIds.add(d.asString()));
        Map<String, String> labels = new TreeMap<>();
        config.path("Labels").properties().forEach(e -> labels.put(e.getKey(), e.getValue().asString()));
        return Optional.of(new ImageConfig(diffIds, config.path("User").stringValue(null), labels));
    }
}
