package io.github.fiftieshousewife.dockerfileinventory.survey;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// The stages of a survey in order, each with the settings that point it at the folders the earlier ones wrote.
final class Survey {

    static final int TRY_REPOSITORIES = 10;

    record Step(String stage, Map<String, String> env) {

        Step {
            env = Map.copyOf(env);
        }
    }

    private Survey() {
    }

    static List<Step> steps(List<String> urls, boolean trial, Map<String, String> env) {
        String root = env.getOrDefault("OUT_DIR", "out/" + String.join("+", urls.stream().map(Survey::folder)
                .toList()) + (trial ? "-try" : ""));
        boolean lineage = env.containsKey("ARTIFACTORY_URL");
        Map<String, String> dirs = new HashMap<>();
        List<Step> steps = new ArrayList<>();
        if (urls.size() == 1) {
            steps.add(step(env, root, dirs, "discovery", discovery(urls.getFirst(), trial)));
        } else {
            List<String> sources = urls.stream().map(url -> root + "/discovery-of/" + folder(url)).toList();
            for (int i = 0; i < urls.size(); i++) {
                Map<String, String> own = new HashMap<>(discovery(urls.get(i), trial));
                own.put("OUT_DIR", sources.get(i));
                steps.add(new Step("discovery", merged(env, dirs, own)));
            }
            steps.add(new Step("combine", merged(env, dirs, Map.of("COMBINE_SOURCES", String.join(",", sources),
                    "OUT_DIR", root + "/discovery"))));
            dirs.put("DISCOVERY_DIR", root + "/discovery");
        }
        steps.add(step(env, root, dirs, "facts", Map.of()));
        if (lineage) {
            steps.add(step(env, root, dirs, "lineage", Map.of()));
        }
        steps.add(step(env, root, dirs, "catalogue", Map.of()));
        steps.add(step(env, root, dirs, "rules", Map.of()));
        steps.add(step(env, root, dirs, "validator", Map.of()));
        steps.add(step(env, root, dirs, "report", Map.of()));
        return steps;
    }

    private static Map<String, String> discovery(String url, boolean trial) {
        return trial ? Map.of("GHE_API_URL", url, "TRY_REPOSITORIES", String.valueOf(TRY_REPOSITORIES))
                : Map.of("GHE_API_URL", url);
    }

    // Each stage is told every earlier stage's folder, and reads those it knows.
    private static Step step(Map<String, String> env, String root, Map<String, String> dirs, String stage,
                             Map<String, String> own) {
        Map<String, String> settings = new HashMap<>(own);
        settings.put("OUT_DIR", root + "/" + stage);
        Step step = new Step(stage, merged(env, dirs, settings));
        dirs.put(stage.toUpperCase(Locale.ROOT) + "_DIR", root + "/" + stage);
        return step;
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
