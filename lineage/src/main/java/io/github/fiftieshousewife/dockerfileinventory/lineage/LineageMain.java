package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs lineage from the environment: {@code ARTIFACTORY_URL} (the {@code /artifactory/} URL),
 * {@code ARTIFACTORY_TOKEN}, {@code FACTS_DIR} (the facts stage's {@code OUT_DIR}), {@code DISCOVERY_DIR},
 * {@code OUT_DIR}, and optionally
 * {@code CONCURRENCY} (default 16). The inventory runs first and resumes; the analysis then rewrites every output.
 */
public final class LineageMain {

    private LineageMain() {
    }

    public static void main(String[] args) {
        Artifactory artifactory = new ArtifactoryHttp(URI.create(required("ARTIFACTORY_URL")),
                required("ARTIFACTORY_TOKEN"));
        Path out = Path.of(required("OUT_DIR"));
        int concurrency = Integer.parseInt(System.getenv().getOrDefault("CONCURRENCY", "16"));
        new Inventory(artifactory, new LineageFiles(out), concurrency).run();
        new Analysis(out, Path.of(required("FACTS_DIR")), Path.of(required("DISCOVERY_DIR"))).run();
        System.out.println(read(out.resolve("rates.json")));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
