package io.github.fiftieshousewife.dockerfileinventory.survey;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

final class Survey {

    static final int TRY_REPOSITORIES = 10;
    static final List<String> AFTER_THE_FACTS = List.of("catalogue", "rules", "validator", "converter",
            "report");

    record Step(String stage, Map<String, String> env) {

        Step {
            env = Map.copyOf(env);
        }
    }

    private Survey() {
    }

    static List<Step> steps(List<String> urls, boolean trial, Map<String, String> env) {
        String root = root(urls, trial, env);
        Map<String, String> dirs = new HashMap<>();
        List<Step> steps = new ArrayList<>(discoveries(urls, trial, env, root, dirs));
        steps.add(step(env, root, dirs, "facts", Map.of()));
        if (env.containsKey("ARTIFACTORY_URL")) {
            steps.add(step(env, root, dirs, "lineage", Map.of()));
        }
        AFTER_THE_FACTS.forEach(stage -> steps.add(step(env, root, dirs, stage, Map.of())));
        return steps;
    }

    static String root(List<String> urls, boolean trial, Map<String, String> env) {
        return env.getOrDefault("OUT_DIR", "out/" + String.join("+", urls.stream().map(Survey::folder).toList())
                + selected(env) + (trial ? "-try" : ""));
    }

    // A selection's run is kept apart from the whole organisation's: named for the repositories and strings it keeps.
    static String selected(Map<String, String> env) {
        List<String> parts = Stream.concat(listed(env.get("REPOSITORIES")).map(name -> name.substring(
                name.indexOf('/') + 1)), listed(env.get("REPOSITORY_NAME_CONTAINS"))).toList();
        return parts.isEmpty() ? "" : "-" + String.join("+", parts);
    }

    private static Stream<String> listed(String value) {
        return value == null ? Stream.empty() : Stream.of(value.split(",")).map(String::strip)
                .filter(part -> !part.isEmpty());
    }

    static List<Step> discoveries(List<String> urls, boolean trial, Map<String, String> env, String root,
                                  Map<String, String> dirs) {
        if (urls.size() == 1) {
            return List.of(step(env, root, dirs, "discovery", discovery(urls.getFirst(), trial)));
        }
        List<Step> each = urls.stream().map(url -> new Step("discovery", merged(env, dirs,
                with(discovery(url, trial), "OUT_DIR", source(root, url))))).toList();
        Step combine = new Step("combine", merged(env, dirs, Map.of("COMBINE_SOURCES", String.join(",", urls.stream()
                .map(url -> source(root, url)).toList()), "OUT_DIR", root + "/discovery")));
        dirs.put("DISCOVERY_DIR", root + "/discovery");
        return Stream.concat(each.stream(), Stream.of(combine)).toList();
    }

    private static String source(String root, String url) {
        return root + "/discovery-of/" + folder(url);
    }

    static Map<String, String> discovery(String url, boolean trial) {
        return trial ? Map.of("GHE_API_URL", url, "TRY_REPOSITORIES", String.valueOf(TRY_REPOSITORIES))
                : Map.of("GHE_API_URL", url);
    }

    // Each stage is told every earlier stage's folder, and reads those it knows.
    static Step step(Map<String, String> env, String root, Map<String, String> dirs, String stage,
                     Map<String, String> own) {
        Step step = new Step(stage, merged(env, dirs, with(own, "OUT_DIR", root + "/" + stage)));
        dirs.put(stage.toUpperCase(Locale.ROOT) + "_DIR", root + "/" + stage);
        return step;
    }

    private static Map<String, String> with(Map<String, String> settings, String name, String value) {
        Map<String, String> all = new HashMap<>(settings);
        all.put(name, value);
        return all;
    }

    private static Map<String, String> merged(Map<String, String> env, Map<String, String> dirs,
                                              Map<String, String> own) {
        Map<String, String> settings = new HashMap<>(env);
        settings.putAll(dirs);
        settings.putAll(own);
        return settings;
    }

    static String folder(String url) {
        URI uri = URI.create(url.strip());
        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("^/+|/+$", "");
        return path.isEmpty() ? uri.getHost() : path.split("/")[0];
    }
}
