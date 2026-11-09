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

    static List<Step> steps(String url, boolean trial, Map<String, String> env) {
        String root = env.getOrDefault("OUT_DIR", "out/" + folder(url) + (trial ? "-try" : ""));
        boolean lineage = env.containsKey("ARTIFACTORY_URL");
        Map<String, String> dirs = new HashMap<>();
        List<Step> steps = new ArrayList<>();
        steps.add(step(env, root, dirs, "discovery", trial
                ? Map.of("GHE_API_URL", url, "TRY_REPOSITORIES", String.valueOf(TRY_REPOSITORIES))
                : Map.of("GHE_API_URL", url)));
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

    // Each stage is told every earlier stage's folder, and reads those it knows.
    private static Step step(Map<String, String> env, String root, Map<String, String> dirs, String stage,
                             Map<String, String> own) {
        Map<String, String> settings = new HashMap<>(env);
        settings.putAll(dirs);
        settings.putAll(own);
        settings.put("OUT_DIR", root + "/" + stage);
        dirs.put(stage.toUpperCase(Locale.ROOT) + "_DIR", root + "/" + stage);
        return new Step(stage, settings);
    }

    static String folder(String url) {
        URI uri = URI.create(url.strip());
        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("^/+|/+$", "");
        return path.isEmpty() ? uri.getHost() : path.split("/")[0];
    }
}
