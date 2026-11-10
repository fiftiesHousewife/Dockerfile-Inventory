package io.github.fiftieshousewife.dockerfileinventory.lineage;

import io.github.fiftieshousewife.dockerfileinventory.http.HttpSettings;
import io.github.fiftieshousewife.dockerfileinventory.http.Throttle;
import io.github.fiftieshousewife.dockerfileinventory.http.TokenSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs lineage from the environment, so the job's pod needs only its variables: {@code OUT_DIR}, {@code FACTS_DIR},
 * {@code DISCOVERY_DIR}, and for the inventory {@code ARTIFACTORY_URL}, {@code ARTIFACTORY_TOKEN},
 * {@code ARTIFACTORY_MAX_REQUESTS_PER_SECOND} and optionally {@code ARTIFACTORY_CONCURRENCY}; {@code LINK_THRESHOLD}
 * and {@code LINK_MARGIN} tune which links credit an image to a Dockerfile. Without {@code ARTIFACTORY_URL} only the
 * analysis runs, on what {@code OUT_DIR} already holds, so a change to linking can be tried without Artifactory.
 */
public final class LineageMain {

    private static final Logger LOG = LoggerFactory.getLogger(LineageMain.class);

    private static final int DEFAULT_CONCURRENCY = 16;

    private LineageMain() {
    }

    /** The inventory runs first and resumes; the analysis then rewrites every output and the rates are printed. */
    public static void main(String[] args) {
        run(System.getenv());
    }

    /**
     * Runs lineage with the given settings, as {@link #main} does with the environment's.
     *
     * @param env the settings
     */
    public static void run(Map<String, String> env) {
        Path out = Path.of(required(env, "OUT_DIR"));
        String url = env.get("ARTIFACTORY_URL");
        if (url != null && !url.isBlank()) {
            inventory(env, url, out);
        }
        new Analysis(out, Path.of(required(env, "FACTS_DIR")), Path.of(required(env, "DISCOVERY_DIR")),
                linkRule(env)).run();
        LOG.info("lineage: {}", read(out.resolve("rates.json")));
    }

    static void inventory(Map<String, String> env, String url, Path out) {
        HttpSettings settings = HttpSettings.from(env);
        TokenSource token = TokenSource.from(env, "ARTIFACTORY_TOKEN");
        LOG.info("lineage: {}, token from {}, {}", url, token, settings);
        Artifactory artifactory = new ArtifactoryHttp(URI.create(url), settings, token,
                Throttle.of(Throttle.rate(env, "ARTIFACTORY_MAX_REQUESTS_PER_SECOND")));
        int concurrency = Throttle.concurrency(env, "ARTIFACTORY_CONCURRENCY", DEFAULT_CONCURRENCY);
        new Inventory(artifactory, new LineageFiles(out), concurrency).run();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static LinkRule linkRule(Map<String, String> env) {
        return new LinkRule(share(env, "LINK_THRESHOLD", LinkRule.DEFAULT.threshold()),
                share(env, "LINK_MARGIN", LinkRule.DEFAULT.margin()));
    }

    static double share(Map<String, String> env, String name, double fallback) {
        String value = env.get(name);
        if (value == null) {
            return fallback;
        }
        if (!value.matches("0(\\.\\d+)?|1(\\.0+)?|\\.\\d+")) {
            throw new IllegalStateException(name + " must be a number from 0 to 1, not " + value);
        }
        return Double.parseDouble(value);
    }

    static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
