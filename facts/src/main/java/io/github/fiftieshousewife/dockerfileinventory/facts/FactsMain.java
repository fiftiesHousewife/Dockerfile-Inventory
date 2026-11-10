package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs Phase 1 from the environment: {@code DISCOVERY_DIR} (discovery's output directory) and {@code OUT_DIR}; the
 * Dockerfiles' facts, then the workloads in the YAML discovery fetched.
 */
public final class FactsMain {

    private static final Logger LOG = LoggerFactory.getLogger(FactsMain.class);

    private FactsMain() {
    }

    public static void main(String[] args) throws IOException {
        run(System.getenv());
    }

    /**
     * Reads every Dockerfile discovery stored, with the given settings, as {@link #main} does with the environment's.
     *
     * @param env the settings
     * @throws IOException when an output cannot be written
     */
    public static void run(Map<String, String> env) throws IOException {
        Path discovery = Path.of(required(env, "DISCOVERY_DIR"));
        Path out = Path.of(required(env, "OUT_DIR"));
        LOG.info("facts: {}", new FactsRun(discovery, out).run());
        LOG.info("facts: {}", new ManifestRun(discovery, out).run());
    }

    static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
