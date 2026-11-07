package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Runs lineage from the environment, so the job's pod needs only its variables: {@code OUT_DIR}, {@code FACTS_DIR},
 * {@code DISCOVERY_DIR}, and for the inventory {@code ARTIFACTORY_URL}, {@code ARTIFACTORY_TOKEN} and optionally
 * {@code CONCURRENCY}. Without {@code ARTIFACTORY_URL} only the analysis runs, on what {@code OUT_DIR} already holds,
 * so a change to linking can be tried without Artifactory.
 */
public final class LineageMain {

    private static final String DEFAULT_CONCURRENCY = "16";

    private LineageMain() {
    }

    /** The inventory runs first and resumes; the analysis then rewrites every output and the rates are printed. */
    public static void main(String[] args) {
        run(System.getenv());
    }

    static void run(Map<String, String> env) {
        Path out = Path.of(required(env, "OUT_DIR"));
        String url = env.get("ARTIFACTORY_URL");
        if (url != null && !url.isBlank()) {
            Artifactory artifactory = new ArtifactoryHttp(URI.create(url), required(env, "ARTIFACTORY_TOKEN"));
            int concurrency = Integer.parseInt(env.getOrDefault("CONCURRENCY", DEFAULT_CONCURRENCY));
            new Inventory(artifactory, new LineageFiles(out), concurrency).run();
        }
        new Analysis(out, Path.of(required(env, "FACTS_DIR")), Path.of(required(env, "DISCOVERY_DIR"))).run();
        System.out.println(read(out.resolve("rates.json")));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
