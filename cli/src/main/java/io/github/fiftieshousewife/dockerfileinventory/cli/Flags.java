package io.github.fiftieshousewife.dockerfileinventory.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// The flags any command takes, wherever they stand; the rest are the command's own, in order. environment: what the
// selection, the ceiling and --yes become, as the settings the stages read.
record Flags(List<String> rest, Optional<String> settings, Optional<String> load, Map<String, String> environment) {

    static final Map<String, String> VALUES = Map.of("--settings", "a YAML file", "--load", "gentle or normal",
            "--repository", "owner/name", "--repository-name-contains", "some text", "--max-dockerfiles",
            "a positive whole number");
    static final Map<String, String> SETTINGS = Map.of("--repository", "REPOSITORIES",
            "--repository-name-contains", "REPOSITORY_NAME_CONTAINS", "--max-dockerfiles", "MAX_DOCKERFILES");

    Flags {
        rest = List.copyOf(rest);
        environment = Map.copyOf(environment);
    }

    static Flags of(List<String> args) {
        List<String> rest = new ArrayList<>();
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (!VALUES.containsKey(arg)) {
                rest.add(arg);
            } else if (i + 1 < args.size()) {
                values.computeIfAbsent(arg, flag -> new ArrayList<>()).add(args.get(++i));
            } else {
                throw new UsageError(arg + " needs a value: " + VALUES.get(arg));
            }
        }
        return new Flags(rest.stream().filter(arg -> !arg.equals("--yes")).toList(), last(values, "--settings"),
                last(values, "--load"), environment(values, rest.contains("--yes")));
    }

    static Map<String, String> environment(Map<String, List<String>> values, boolean yes) {
        Map<String, String> env = new HashMap<>();
        SETTINGS.forEach((flag, setting) -> Optional.ofNullable(values.get(flag))
                .ifPresent(given -> env.put(setting, String.join(",", given))));
        Optional.ofNullable(env.get("MAX_DOCKERFILES")).filter(max -> !max.matches("[1-9][0-9]{0,8}"))
                .ifPresent(max -> {
                    throw new UsageError("--max-dockerfiles is a positive whole number, not " + max);
                });
        if (yes) {
            env.put("ASSUME_YES", "true");
        }
        return env;
    }

    private static Optional<String> last(Map<String, List<String>> values, String flag) {
        return Optional.ofNullable(values.get(flag)).map(List::getLast);
    }
}
