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

record ImageConfig(List<String> diffIds, List<HistoryStep> history, String user, Map<String, String> labels,
                   Map<String, String> env, String workingDir, List<String> entrypoint, List<String> cmd,
                   List<String> onBuild) {

    private static final ObjectMapper JSON = new ObjectMapper();

    ImageConfig {
        diffIds = List.copyOf(diffIds);
        history = List.copyOf(history);
        labels = Collections.unmodifiableMap(new TreeMap<>(labels));
        env = Collections.unmodifiableMap(new LinkedHashMap<>(env));
        entrypoint = List.copyOf(entrypoint);
        cmd = List.copyOf(cmd);
        onBuild = List.copyOf(onBuild);
    }

    ImageConfig(List<String> diffIds, List<HistoryStep> history, String user, Map<String, String> labels) {
        this(diffIds, history, user, labels, Map.of(), null, List.of(), List.of(), List.of());
    }

    static Optional<ImageConfig> parse(String text) {
        return json(text).filter(root -> root.path("rootfs").isObject()).map(ImageConfig::of);
    }

    static Optional<JsonNode> json(String text) {
        try {
            return Optional.of(JSON.readTree(text));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    static ImageConfig of(JsonNode root) {
        JsonNode config = root.path("config");
        Map<String, String> labels = new TreeMap<>();
        config.path("Labels").properties().forEach(e -> labels.put(e.getKey(), e.getValue().asString()));
        return new ImageConfig(strings(root.path("rootfs").path("diff_ids")), history(root.path("history")),
                config.path("User").stringValue(null), labels, env(config.path("Env")),
                config.path("WorkingDir").stringValue(null), strings(config.path("Entrypoint")),
                strings(config.path("Cmd")), strings(config.path("OnBuild")));
    }

    static List<HistoryStep> history(JsonNode array) {
        List<HistoryStep> history = new ArrayList<>();
        array.forEach(h -> history.add(new HistoryStep(
                h.path("created_by").stringValue(null),
                h.path("comment").stringValue(null),
                h.path("empty_layer").asBoolean(),
                h.path("created").stringValue(null))));
        return history;
    }

    static Map<String, String> env(JsonNode array) {
        Map<String, String> env = new LinkedHashMap<>();
        array.forEach(e -> {
            String pair = e.asString();
            int equals = pair.indexOf('=');
            env.put(equals < 0 ? pair : pair.substring(0, equals), equals < 0 ? "" : pair.substring(equals + 1));
        });
        return env;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asString()));
        return values;
    }
}
