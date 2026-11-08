package io.github.fiftieshousewife.dockerfileinventory.facts;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Secrets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.openrewrite.Cursor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

final class FinalConfig {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private String workdir;
    private final List<String> volumes = new ArrayList<>();
    private String healthcheck;
    private List<String> shell = List.of();
    private String stopSignal;
    private ImageConfig.Command entrypoint;
    private ImageConfig.Command cmd;
    private final Map<String, String> environment = new LinkedHashMap<>();
    private final Map<String, String> labels = new LinkedHashMap<>();
    private int layers;

    private FinalConfig() {
    }

    static ImageConfig of(Docker.File file) {
        FinalConfig config = new FinalConfig();
        Steps.finalImage(file).stream()
                .map(instruction -> Continuations.oneLine(instruction.printTrimmed(new Cursor(null, file))))
                .forEach(config::accept);
        return new ImageConfig(config.workdir, config.volumes, config.healthcheck, config.shell, config.stopSignal,
                config.entrypoint, config.cmd, config.environment, config.labels, config.layers);
    }

    void accept(String line) {
        String[] parts = line.split("\\s+", 2);
        String rest = parts.length > 1 ? parts[1].strip() : "";
        switch (parts[0].toUpperCase(Locale.ROOT)) {
            case "WORKDIR" -> workdir = workdir(workdir, KeyValuePairs.unquoted(rest));
            case "VOLUME" -> volumes.addAll(words(rest));
            case "HEALTHCHECK" -> healthcheck = rest;
            case "SHELL" -> shell = words(rest);
            case "STOPSIGNAL" -> stopSignal = rest;
            case "ENTRYPOINT" -> entrypoint = command(rest);
            case "CMD" -> cmd = command(rest);
            case "ENV" -> pairs(rest).forEach(environment::put);
            case "LABEL" -> pairs(rest).forEach(labels::put);
            case "RUN", "COPY", "ADD" -> layers++;
            default -> {
            }
        }
    }

    static String workdir(String current, String path) {
        if (path.startsWith("/") || current == null) {
            return path.startsWith("/") ? path : "/" + path;
        }
        return current.endsWith("/") ? current + path : current + "/" + path;
    }

    static ImageConfig.Command command(String rest) {
        List<String> exec = json(rest);
        return exec == null ? new ImageConfig.Command(EntrypointForm.SHELL, List.of(rest))
                : new ImageConfig.Command(EntrypointForm.EXEC, exec);
    }

    static List<String> words(String rest) {
        List<String> exec = json(rest);
        return exec != null ? exec
                : KeyValuePairs.words(rest).stream().map(KeyValuePairs::unquoted).toList();
    }

    // ENV's legacy form, "ENV NAME value with spaces", has one name and the rest of the line as its value.
    static Map<String, String> pairs(String rest) {
        Map<String, String> pairs = new LinkedHashMap<>();
        List<KeyValuePairs.Pair> written = KeyValuePairs.of(rest);
        if (!written.isEmpty() && written.getFirst().value() == null) {
            String[] legacy = rest.split("\\s+", 2);
            pairs.put(legacy[0], Secrets.shown(legacy[0], legacy.length > 1 ? legacy[1] : ""));
            return pairs;
        }
        written.forEach(pair -> pairs.put(KeyValuePairs.unquoted(pair.key()),
                Secrets.shown(pair.key(), KeyValuePairs.unquoted(pair.value()))));
        return pairs;
    }

    private static List<String> json(String rest) {
        if (!rest.startsWith("[")) {
            return null;
        }
        try {
            return JSON.readValue(rest, STRINGS);
        } catch (JacksonException notJson) {
            return null;
        }
    }
}
