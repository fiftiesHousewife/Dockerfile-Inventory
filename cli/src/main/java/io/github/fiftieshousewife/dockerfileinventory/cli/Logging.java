package io.github.fiftieshousewife.dockerfileinventory.cli;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

record Logging(String level, boolean json, String config) {

    static final String LEVEL = "docker-inventory.log.level";
    static final String FORMAT = "docker-inventory.log.format";
    static final List<String> LEVELS = List.of("error", "warn", "info", "debug");
    static final List<String> FLAGS = List.of("--quiet", "--verbose");

    static Logging of(List<String> args, Map<String, String> env) {
        String level = env.getOrDefault("LOG_LEVEL", "info").toLowerCase(Locale.ROOT);
        if (!LEVELS.contains(level)) {
            throw new UsageError("LOG_LEVEL is error, warn, info or debug, not " + env.get("LOG_LEVEL"));
        }
        String format = env.getOrDefault("LOG_FORMAT", "text");
        if (!format.equals("text") && !format.equals("json")) {
            throw new UsageError("LOG_FORMAT is text or json, not " + format);
        }
        String chosen = args.contains("--quiet") ? "error" : args.contains("--verbose") ? "debug" : level;
        return new Logging(chosen, format.equals("json"), env.get("LOG4J_CONFIG"));
    }

    static List<String> withoutFlags(List<String> args) {
        return args.stream().filter(arg -> !FLAGS.contains(arg)).toList();
    }

    Map<String, String> properties() {
        Map<String, String> properties = new HashMap<>(Map.of(LEVEL, level, FORMAT, json ? "json" : "text"));
        if (config != null) {
            properties.put("log4j2.configurationFile", config);
        }
        return properties;
    }
}
