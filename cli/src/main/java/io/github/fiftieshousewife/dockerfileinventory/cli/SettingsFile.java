package io.github.fiftieshousewife.dockerfileinventory.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

final class SettingsFile {

    static final Map<String, Map<String, String>> SECTIONS = Map.of(
            "github", server("GHE", "GHE_API_URL"),
            "artifactory", server("ARTIFACTORY", "ARTIFACTORY_URL"));
    static final Map<String, String> TOP = Map.of("platform", "PROFILE", "out", "OUT_DIR");
    static final Map<String, String> APP = Map.of("id", "GHE_APP_ID", "privateKeyFile", "GHE_APP_PRIVATE_KEY_FILE");

    private SettingsFile() {
    }

    static Map<String, String> server(String prefix, String url) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("url", url);
        if (prefix.equals("GHE")) {
            keys.put("urls", "SURVEY_URLS");
        }
        keys.put("tokenFile", prefix + "_TOKEN_FILE");
        keys.put("load", prefix + "_LOAD");
        keys.put("requestsPerSecond", prefix + "_MAX_REQUESTS_PER_SECOND");
        keys.put("concurrency", prefix + "_CONCURRENCY");
        if (prefix.equals("GHE")) {
            keys.put("lastChanges", "GHE_LAST_CHANGES");
            keys.put("app", null);
        }
        return keys;
    }

    static Map<String, String> read(Path file) {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8), file.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, String> parse(String yaml, String source) {
        JsonNode root;
        try {
            root = new YAMLMapper().readTree(yaml);
        } catch (JacksonException e) {
            throw new UsageError(source + " is not YAML: " + e.getOriginalMessage());
        }
        Map<String, String> env = new HashMap<>();
        root.properties().forEach(entry -> {
            if (TOP.containsKey(entry.getKey())) {
                env.put(TOP.get(entry.getKey()), entry.getValue().asString());
            } else if (SECTIONS.containsKey(entry.getKey())) {
                section(entry.getKey(), entry.getValue(), source, env);
            } else {
                throw new UsageError(source + ": " + entry.getKey() + " is not a setting (github, artifactory, "
                        + "platform, out)");
            }
        });
        return env;
    }

    static void section(String name, JsonNode values, String source, Map<String, String> env) {
        Map<String, String> keys = SECTIONS.get(name);
        values.properties().forEach(entry -> {
            String key = name + "." + entry.getKey();
            if (entry.getKey().equals("token")) {
                throw new UsageError(source + ": " + key + " would put a secret in the file: name a file holding it, "
                        + name + ".tokenFile");
            }
            if (!keys.containsKey(entry.getKey())) {
                throw new UsageError(source + ": " + key + " is not a setting (" + String.join(", ", keys.keySet()
                        .stream().map(k -> name + "." + k).toList()) + ")");
            }
            if (entry.getKey().equals("app")) {
                app(entry.getValue(), key, source, env);
            } else {
                env.put(keys.get(entry.getKey()), value(entry.getValue()));
            }
        });
    }

    static void app(JsonNode values, String key, String source, Map<String, String> env) {
        values.properties().forEach(entry -> {
            if (!APP.containsKey(entry.getKey())) {
                throw new UsageError(source + ": " + key + "." + entry.getKey() + " is not a setting (" + key
                        + ".id, " + key + ".privateKeyFile)");
            }
            env.put(APP.get(entry.getKey()), value(entry.getValue()));
        });
    }

    static String value(JsonNode node) {
        return node.isArray() ? String.join(",", node.valueStream().map(JsonNode::asString).toList())
                : node.asString();
    }

    static Map<String, String> merged(Map<String, String> file, Map<String, String> environment) {
        Map<String, String> all = new HashMap<>(file);
        all.putAll(environment);
        return all;
    }
}
