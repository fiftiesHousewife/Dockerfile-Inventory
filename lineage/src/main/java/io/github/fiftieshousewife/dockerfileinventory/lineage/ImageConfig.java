package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** What lineage reads from an image config: its layers, the user it runs as, and its labels. */
public record ImageConfig(List<String> diffIds, List<HistoryStep> history, String user, Map<String, String> labels,
                          Map<String, String> env, String workingDir, List<String> entrypoint, List<String> cmd,
                          List<String> onBuild) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public ImageConfig {
        diffIds = List.copyOf(diffIds);
        history = List.copyOf(history);
        labels = Collections.unmodifiableMap(new TreeMap<>(labels));
        env = Collections.unmodifiableMap(new LinkedHashMap<>(env));
        entrypoint = List.copyOf(entrypoint);
        cmd = List.copyOf(cmd);
        onBuild = List.copyOf(onBuild);
    }

    /** An image with layers, history, user and labels only, as the tree and links need. */
    public ImageConfig(List<String> diffIds, List<HistoryStep> history, String user, Map<String, String> labels) {
        this(diffIds, history, user, labels, Map.of(), null, List.of(), List.of(), List.of());
    }

    /** Empty for text that is not JSON or has no {@code rootfs}: one bad image must not stop the run. */
    static Optional<ImageConfig> parse(String text) {
        JsonNode root;
        try {
            root = JSON.readTree(text);
        } catch (JacksonException e) {
            return Optional.empty();
        }
        if (!root.path("rootfs").isObject()) {
            return Optional.empty();
        }
        JsonNode config = root.path("config");
        List<String> diffIds = new ArrayList<>();
        root.path("rootfs").path("diff_ids").forEach(d -> diffIds.add(d.asString()));
        Map<String, String> labels = new TreeMap<>();
        config.path("Labels").properties().forEach(e -> labels.put(e.getKey(), e.getValue().asString()));
        List<HistoryStep> history = new ArrayList<>();
        root.path("history").forEach(h -> history.add(new HistoryStep(
                h.path("created_by").stringValue(null),
                h.path("comment").stringValue(null),
                h.path("empty_layer").asBoolean(),
                h.path("created").stringValue(null))));
        Map<String, String> env = new LinkedHashMap<>();
        config.path("Env").forEach(e -> {
            String pair = e.asString();
            int equals = pair.indexOf('=');
            env.put(equals < 0 ? pair : pair.substring(0, equals), equals < 0 ? "" : pair.substring(equals + 1));
        });
        return Optional.of(new ImageConfig(diffIds, history, config.path("User").stringValue(null), labels, env,
                config.path("WorkingDir").stringValue(null), strings(config.path("Entrypoint")),
                strings(config.path("Cmd")), strings(config.path("OnBuild"))));
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asString()));
        return values;
    }

    /**
     * Whether the history steps that made layers are as many as the layers. A squashed image, or a builder that
     * writes no history, breaks this, and the history can then not be aligned layer by layer.
     */
    public boolean historyMatchesLayers() {
        return history.stream().filter(h -> !h.emptyLayer()).count() == diffIds.size();
    }
}
